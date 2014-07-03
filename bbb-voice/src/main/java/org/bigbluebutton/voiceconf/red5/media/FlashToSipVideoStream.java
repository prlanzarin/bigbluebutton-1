/**
* BigBlueButton open source conferencing system - http://www.bigbluebutton.org/
* 
* Copyright (c) 2012 BigBlueButton Inc. and by respective authors (see below).
*
* This program is free software; you can redistribute it and/or modify it under the
* terms of the GNU Lesser General Public License as published by the Free Software
* Foundation; either version 3.0 of the License, or (at your option) any later
* version.
* 
* BigBlueButton is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
* PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License along
* with BigBlueButton; if not, see <http://www.gnu.org/licenses/>.
*
*/
package org.bigbluebutton.voiceconf.red5.media;

import org.bigbluebutton.voiceconf.red5.media.transcoder.FlashToSipTranscoder;
import java.net.DatagramSocket;
import org.bigbluebutton.voiceconf.sip.SipConnectInfo;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;

import org.slf4j.Logger;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.stream.IStreamListener;
import org.red5.server.api.stream.IStreamPacket;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.event.SerializeUtils;



public class FlashToSipVideoStream implements FlashToSipStream {

	//empty class for now..
	private final static Logger log = Red5LoggerFactory.getLogger(FlashToSipVideoStream.class, "sip");
	
	private final FlashToSipTranscoder transcoder;	
	private IStreamListener mInputListener;
	private final DatagramSocket srcSocket;
	private final SipConnectInfo connInfo;
	private String videoStreamName;	
	private RtpStreamSender rtpSender;
	

	public FlashToSipVideoStream(FlashToSipTranscoder transcoder, DatagramSocket srcSocket, SipConnectInfo connInfo) {

		this.transcoder = transcoder;
		this.srcSocket = srcSocket;
		this.connInfo = connInfo;		
		videoStreamName = "video_" + System.currentTimeMillis();
		rtpSender = new RtpStreamSender(srcSocket, connInfo);	    
		transcoder.setTranscodedMediaDataListener(this);	    
		
	}

	@Override
	public void start(IBroadcastStream broadcastStream, IScope scope) throws StreamException {

		if (log.isDebugEnabled())
			log.debug("startTranscodingStream({},{})", broadcastStream.getPublishedName(), scope.getName());
		mInputListener = new IStreamListener() {
			public void packetReceived(IBroadcastStream broadcastStream, IStreamPacket packet) {
		      IoBuffer buf = packet.getData();
		      if (buf != null)
		    	  buf.rewind();
		    
		      if (buf == null || buf.remaining() == 0){
		    	  log.debug("skipping empty packet with no data");
		    	  return;
		      }
		      	      
		      if (packet instanceof VideoData) {
		    	  byte[] data = SerializeUtils.ByteBufferToByteArray(buf);
		    	  // Remove the first byte as it is the codec id.
		    	  transcoder.handlePacket(data, 1, data.length-1);   
		      } 
			}
		};
				
	    broadcastStream.addStreamListener(mInputListener);    	    
		rtpSender.connect();
		transcoder.start();

	}

	@Override
	public void stop(IBroadcastStream broadcastStream, IScope scope) {
		broadcastStream.removeStreamListener(mInputListener);
		if (broadcastStream != null) {
			broadcastStream.stop();
			broadcastStream.close();
		} 
	    transcoder.stop();
	    srcSocket.close();	
	}

	@Override
	public void handleTranscodedMediaData(byte[] videoData, long timestamp) {
		if (videoData != null) {
  		  rtpSender.sendVideo(videoData, transcoder.getCodecId(), timestamp);
  	  } else {
  		  log.warn("Transcodec video is null. Discarding.");
  	  }
	}

	@Override
	public String getStreamName()
	{
		return videoStreamName;
	}

	



}