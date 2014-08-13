    }
    
    public void receiveRtpPackets() {    
        int packetReceivedCounter = 0;
        int internalBufferLength = payloadLength + RTP_HEADER_SIZE;
        byte[] internalBuffer = new byte[internalBufferLength];
        RtpPacket rtpPacket = new RtpPacket(internalBuffer, internalBufferLength);
        
        while (receivePackets) {
        	try {       			
        		rtpSocket.receive(rtpPacket);        		
        		packetReceivedCounter++;  
        		if (shouldDropDelayedPacket(rtpPacket)) {
        			continue;
        		}
        		if (rtpPacket.isRtcpPacket()) {
        			/**
        			 * Asterisk (1.6.2.5) send RTCP packets. We just ignore them (for now).
        			 * It could be for KeepAlive (http://tools.ietf.org/html/draft-ietf-avt-app-rtp-keepalive-09)
        			 */
        			if (log.isDebugEnabled()) 
        				log.debug("RTCP packet [" + rtpPacket.getRtcpPayloadType() + ", length=" + rtpPacket.getPayloadLength() + "] seqNum[rtpSeqNum=" + rtpPacket.getSeqNum() + ",lastSeqNum=" + lastSequenceNumber 
        					+ "][rtpTS=" + rtpPacket.getTimestamp() + ",lastTS=" + lastPacketTimestamp + "][port=" + rtpSocket.getDatagramSocket().getLocalPort() + "]");          			
        		} else {
            		if (shouldHandlePacket(rtpPacket)) {

                        lastTimestampDelta = rtpPacket.getTimestamp() - lastPacketTimestamp;
                        
                        //for debugging...
                        if(packetReceivedCounter < 31)
                            log.debug("rtpPacket.getLength() = " + rtpPacket.getLength()
                                      + " | rtpPacket.getPayloadLength() = " + rtpPacket.getPayloadLength());
                                

                        lastPacketTimestamp = rtpPacket.getTimestamp();

            			lastSequenceNumber = rtpPacket.getSeqNum();

                        byte[] newBuffer = internalBuffer.clone();
            			processRtpPacket(newBuffer, RTP_HEADER_SIZE, rtpPacket.getPayloadLength());
            		
                    } else {
            			if (log.isDebugEnabled())
            				log.debug("Corrupt packet [" + rtpPacket.getRtcpPayloadType() + "," + rtpPacket.getPayloadType() + ", length=" + rtpPacket.getPayloadLength() + "] seqNum[rtpSeqNum=" + rtpPacket.getSeqNum() + ",lastSeqNum=" + lastSequenceNumber 
            					+ "][rtpTS=" + rtpPacket.getTimestamp() + ",lastTS=" + lastPacketTimestamp + "][port=" + rtpSocket.getDatagramSocket().getLocalPort() + "]");          			       			

            			if (lastPacketDropped) successivePacketDroppedCount++;
            			else lastPacketDropped = true;           			
            		}
            	}
        	} catch (IOException e) { // We get this when the socket closes when the call hangs up.        		
        		receivePackets = false;
        	}
        }
        log.debug("Rtp Receiver stopped. Packet Received = " + packetReceivedCounter + "." );
        if (listener != null) listener.onStoppedReceiving();
    }
    
    private boolean shouldDropDelayedPacket(RtpPacket rtpPacket) {
    	long now = System.currentTimeMillis();
    	if (now - lastPacketReceived > 200) {
    		if (log.isDebugEnabled())
    			log.debug("Delayed packet [" + rtpPacket.getRtcpPayloadType() + "," + rtpPacket.getPayloadType() + ", length=" + rtpPacket.getPayloadLength() + "] seqNum[rtpSeqNum=" + rtpPacket.getSeqNum() + ",lastSeqNum=" + lastSequenceNumber 
					+ "][rtpTS=" + rtpPacket.getTimestamp() + ",lastTS=" + lastPacketTimestamp + "][port=" + rtpSocket.getDatagramSocket().getLocalPort() + "]");          			       			
			lastPacketReceived = now;
    		return true;
    	}
    	lastPacketReceived = now;
    	return false;
    }
    
    private boolean isMarkerPacket(RtpPacket rtpPacket) {
    	/*
    	 * FreeSWITCH sends a marker packet at the beginning of the voice frame.
    	 * If you stop talking and then start talking, a marker packet is received on start talking. (ralam sept 20, 2010).
    	 */
		if (rtpPacket.hasMarker()) {
			if (log.isDebugEnabled())
				log.debug("Marked packet [" + rtpPacket.getPayloadType() + ", length=" + rtpPacket.getPayloadLength() + "] seqNum[rtpSeqNum=" + rtpPacket.getSeqNum() + ",lastSeqNum=" + lastSequenceNumber 
   					+ "][rtpTS=" + rtpPacket.getTimestamp() + ",lastTS=" + lastPacketTimestamp + "][port=" + rtpSocket.getDatagramSocket().getLocalPort() + "]");       				        			
   			return true;
		}    	
		
		return false;
    }
    
    private boolean shouldHandlePacket(RtpPacket rtpPacket) {
		/** Take seq number only into account and not timestamps. Seems like the timestamp sometimes change whenever the audio changes source.
		 *  For example, in FreeSWITCH, the audio prompt will have it's own "start" timestamp and then
		 *  another "start" timestamp will be generated for the voice. (ralam, sept 7, 2010).
		 *	&& packetIsNotCorrupt(rtpPacket)) {
		**/
    	 return isFirstPacket(rtpPacket) || isMarkerPacket(rtpPacket) || resetDueToSuccessiveDroppedPackets() || validSeqNum(rtpPacket) || seqNumRolledOver(rtpPacket);    			
    }
    
    private boolean resetDueToSuccessiveDroppedPackets() {
    	/*
    	 * I notice that Asterisk (1.6.2.5) sets the rtp sequence number to 12 every time it sends a marked rtp packet. This screws up our
    	 * way of determining which packet to drop. To get around this, we detect if consecutive packets have been dropped then reset
    	 * the sequence number to handle the next incoming packets (ralam sept. 20, 2010).
    	 */
    	if (lastPacketDropped && successivePacketDroppedCount > 3) {
    		if (log.isDebugEnabled())
    			log.debug("Resetting after successive dropped packets [successivePacketDroppedCount=" + successivePacketDroppedCount + 
   					"][port=" + rtpSocket.getDatagramSocket().getLocalPort() + "]");
    		lastPacketDropped = false;
    		successivePacketDroppedCount = 0;
    		return true;
    	}
    	return false;
    }
    
    private boolean isFirstPacket(RtpPacket rtpPacket) {
		if (firstPacket) {
			lastPacketReceived = System.currentTimeMillis();
			firstPacket = false;
			if (log.isDebugEnabled())
				log.debug("First packet [" + rtpPacket.getPayloadType() + ", length=" + rtpPacket.getPayloadLength() + "] seqNum[rtpSeqNum=" + rtpPacket.getSeqNum() + ",lastSeqNum=" + lastSequenceNumber 
						+ "][rtpTS=" + rtpPacket.getTimestamp() + ",lastTS=" + lastPacketTimestamp + "][port=" + rtpSocket.getDatagramSocket().getLocalPort() + "]");
			return true;
		}
		return false;
    }
    
    private boolean validSeqNum(RtpPacket rtpPacket) {
    	/*
    	 * Assume if the sequence number jumps by more that 100, that the sequence number is corrupt.
    	 */
    	return (rtpPacket.getSeqNum() > lastSequenceNumber && rtpPacket.getSeqNum() - lastSequenceNumber < 100);
    }
    
    private boolean seqNumRolledOver(RtpPacket rtpPacket) {
    	/*
    	 * Max sequence num is 65535 (16-bits). Let's use 65000 as check to take into account
    	 * delayed packets.
    	 */
    	if (lastSequenceNumber - rtpPacket.getSeqNum() > 65000) {
    		if (log.isDebugEnabled())
    			log.debug("Packet rolling over seqNum[rtpSeqNum=" + rtpPacket.getSeqNum() + ",lastSeqNum=" + lastSequenceNumber 
    					+ "][rtpTS=" + rtpPacket.getTimestamp() + ",lastTS=" + lastPacketTimestamp + "][port=" + rtpSocket.getDatagramSocket().getLocalPort() + "]");  
			return true;	
    	}
    	return false;
    }

    private void processRtpPacket(byte[] rtpMediaData, int offset, int len) {
		if (listener != null) listener.onMediaDataReceived(rtpMediaData, offset, len, lastTimestampDelta);
		else log.debug("No listener for incoming media packet");    	
    }
}
