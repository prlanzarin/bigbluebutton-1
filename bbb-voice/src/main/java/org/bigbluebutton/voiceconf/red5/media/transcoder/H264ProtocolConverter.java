package org.bigbluebutton.voiceconf.red5.media.transcoder;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.red5.app.sip.codecs.Codec;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.red5.logging.Red5LoggerFactory;

import org.bigbluebutton.voiceconf.red5.media.net.RtpPacket;
import org.bigbluebutton.voiceconf.red5.media.transcoder.VideoProtocolConverter;

public class H264ProtocolConverter extends VideoProtocolConverter {

    private static final Logger log = Red5LoggerFactory.getLogger(H264ProtocolConverter.class, "sip");
        
    // rtp => rtmp
    private byte[] sps1;
    private byte[] pps;
    private boolean sentSeq;
    private long lastFIRTime;
    private long startTs;
    private long startTm;
    private long startRelativeTime;
    private List<RtpPacketWrapper> packetsQueue;
        
    // rtmp => rtp
    private int lenSize;
    private boolean spsSent = false;
    private boolean ppsSent = false;
        
    public H264ProtocolConverter() {
        resetConverter();
        startRelativeTime = System.currentTimeMillis();
    }

    public void resetConverter() {
        packetsQueue = new ArrayList<RtpPacketWrapper>();
        lastFIRTime = System.currentTimeMillis();
        sps1 = new byte[0];
        pps = new byte[0];
        sentSeq = false;
        startTs = -1;
        startTm = -1;
    } 

    @Override
	public List<RTMPPacketInfo> rtpToRTMP(RtpPacket packet) {

        List<RTMPPacketInfo> result = new ArrayList<RTMPPacketInfo>();
        byte[] payload = packet.getPayload();
        int nalType = payload[0] & 0x1f;
        byte[] naldata = null;

        switch (nalType) {
        case 7: // SPS
                sps1 = payload;
                //log.debug("$$ SPS received: " + Arrays.toString(sps1));
                break;
        case 8: // PPS
                pps = payload;
                //log.debug("$$ PPS received: " + Arrays.toString(pps));
                break;
        default:
                if (payload.length > 1) {
                        if (nalType == 24) { // for cisco phones
                                payload = Arrays.copyOfRange(payload, 1, payload.length);
                                while (payload.length > 0) {
                                        int size = payload[1];
                                        payload = Arrays.copyOfRange(payload, 2, payload.length);
                                        naldata = Arrays.copyOf(payload, size);
                                        payload = Arrays.copyOfRange(payload, size, payload.length);
                                        int nt = naldata[0] & 0x1f;
                                        switch (nt) {
                                        case 7:
                                                sps1 = naldata;
                                                //log.debug("SPS received: " + Arrays.toString(sps1));
                                                break;
                                        case 8:
                                                pps = naldata;
                                                //log.debug("PPS received: " + Arrays.toString(pps));
                                                break;
                                        default:
                                                break;
                                        }
                                }
                        }
                        
                        if (nalType == 1 || nalType == 5 || nalType == 28 || nalType == 24) {
                                packetsQueue.add(new RtpPacketWrapper(packet, nalType));
                        }
                }
                break;
        }
        
        if (packetsQueue.size() > 1) {
                RtpPacket last = packetsQueue.get(packetsQueue.size() - 1).packet;
                RtpPacket preLast = packetsQueue.get(packetsQueue.size() - 2).packet;
                if (last.getTimestamp() != preLast.getTimestamp()) {
                        log.debug("$$ Clearing queue since new packet has different ts. old ts=" + preLast.getTimestamp() + 
                                        " new ts=" + last.getTimestamp());
                        packetsQueue.clear();
                }
        }
        
        // marker means the end of the frame
        if (packet.getPacket()[1] < 0 && !packetsQueue.isEmpty()) {
                int realNri = 0;
                nalType = 0;
                List<ByteArrayBuilder> payloads = new ArrayList<ByteArrayBuilder>();
                ByteArrayBuilder newdata = null;
                List<ByteArrayBuilder> pendingData = new ArrayList<ByteArrayBuilder>();
                
                for (RtpPacketWrapper q: packetsQueue) {
                        int length = 0;
                        switch (q.nalType) {
				            case 1:
				            case 5:
				                    if (newdata == null) {
				                            nalType = q.nalType;
				                            // first byte: 0x17 for intra-frame, 0x27 for non-intra frame
				                            // second byte: 0x01 for picture data
				                            newdata = new ByteArrayBuilder(new byte[]{(byte) (q.nalType == 5? 0x17: 0x27), 1, 0, 0, 0});
				                    }
				                    length = q.packet.getPayload().length;
				                    newdata.putArray((byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length);
				                    newdata.putArray(q.packet.getPayload());
				                    break;
				            case 24:
				                    payload = Arrays.copyOfRange(payload, 1, payload.length);
				                    while (payload.length > 0) {
				                            int size = payload[0];
				                            payload = Arrays.copyOfRange(payload, 2, payload.length);
				                            naldata = Arrays.copyOf(payload, size);
				                            payload = Arrays.copyOfRange(payload, size, payload.length);
				                            int nt = naldata[0] & 0x1f;
				                            if (nt == 5 || nt == 1) {
				                                    if (newdata == null) {
				                                            nalType = nt;
				                                            // first byte: 0x17 for intra-frame, 0x27 for non-intra frame
				                                            // second byte: 0x01 for picture data
				                                            newdata = new ByteArrayBuilder(new byte[]{(byte) (nt == 5? 0x17: 0x27), 1, 0, 0, 0});
				                                    }
				                                    length = naldata.length;
				                                    newdata.putArray((byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length);
				                                    newdata.putArray(naldata);
				                            }
				                    }
				                    break;

				            case 28:
				                    if (newdata == null) {
				                            nalType = q.packet.getPayload()[1] & 0x1f;
				                            realNri = q.packet.getPayload()[0] & 0x60;
				                            // first byte: 0x17 for intra-frame, 0x27 for non-intra frame
				                            // second byte: 0x01 for picture data
				                            newdata = new ByteArrayBuilder(new byte[]{(byte) (nalType == 5? 0x17: 0x27), 1, 0, 0, 0});
				                    }
				                    pendingData.add(new ByteArrayBuilder(Arrays.copyOfRange(q.packet.getPayload(), 2, q.packet.getPayload().length)));
				                    if ((q.packet.getPayload()[1] & 0x40) == 0x40) {
				                            ByteArrayBuilder remaining = new ByteArrayBuilder((byte) (nalType | realNri));
				                            for (ByteArrayBuilder pd: pendingData) {
				                                    remaining.putArray(pd.buildArray());
				                            }
				                            pendingData.clear();
				                            length = remaining.getLength();
				                            newdata.putArray((byte) (length >>> 24), (byte) (length >>> 16), (byte) (length >>> 8), (byte) length);
				                            newdata.putArray(remaining.buildArray());
				                    } 
				                    else {
				                            continue;
				                    }
				                    break;

				            default:
				                    break;
                        }
                }


                packetsQueue.clear();
                
                if (newdata != null) {
                        payloads.add(newdata);
                }
                
                if (!sentSeq && nalType != 5 && pps.length > 0 && sps1.length > 0 || sps1.length == 0 || pps.length == 0) {
                        packetsQueue.clear();
                        if (System.currentTimeMillis() - lastFIRTime > 5000) {
                                lastFIRTime = System.currentTimeMillis();
                                requestFIR();
                        }
                } 
                else {
                        if (pps.length > 0 && sps1.length > 0 && !sentSeq && nalType == 5) {
                                sentSeq = true;
                        }
                        
                        // calculate timestamp
                        if (startTs == -1) {
                                startTs = packet.getTimestamp();
                        }
                        if (startTm == -1) {
                                startTm = System.currentTimeMillis() - startRelativeTime;
                        }

                        long tm = startTm + (packet.getTimestamp() - startTs) / 90; // 90 = bitrate / 1000
                        if (nalType == 5 && payloads.size() > 0) {
                                ByteArrayBuilder data = new ByteArrayBuilder();
                                // first byte: 0x17 for intra-frame
                                // second byte: 0x00 for configuration data
                                data.putArray(new byte[]{0x17, 0, 0, 0, 0, 1});
                                data.putArray(Arrays.copyOfRange(sps1, 1, 4));
                                data.putArray((byte) 0xff, (byte) 0xe1, (byte) (sps1.length >>> 8), (byte) sps1.length);
                                data.putArray(sps1);
                                data.putArray((byte) 1, (byte) (pps.length >>> 8), (byte) pps.length);
                                data.putArray(pps);
                                payloads.add(0, data);
                        }
                        
                        for (ByteArrayBuilder bba: payloads) {
                                result.add(new RTMPPacketInfo(bba.buildArray(), tm));
                        }
                }
        }
    	return result;
	}

    protected void requestFIR() {
        log.debug("requesting FIR...");
    }

}

