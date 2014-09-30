package org.bigbluebutton.voiceconf.red5.media.transcoder;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.red5.app.sip.codecs.Codec;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.red5.logging.Red5LoggerFactory;

import org.bigbluebutton.voiceconf.red5.media.net.RtpPacket;

public class VideoProtocolConverter {

    private static final Logger log = Red5LoggerFactory.getLogger(H264ProtocolConverter.class, "sip");

	public List<RTMPPacketInfo> rtpToRTMP(RtpPacket packet) {
        return null;

	}

    public List<RtpPacket> rtmpToRTP(byte data[], long ts) {
        return null;

    }

//------------------------------------------------- INNER CLASSES --------------------------------------------
    //1.
    public static class RTMPPacketInfo {
                
        public byte[] data;
        public long ts;
                
        public RTMPPacketInfo(byte[] data, long ts) {
            super();
            this.data = data;
            this.ts = ts;
        }             
    }

    //2.
    protected static class RtpPacketWrapper {
            
        public final RtpPacket packet;
        
        public final int nalType;

        public RtpPacketWrapper(RtpPacket packet, int nalType) {
                this.packet = packet;
                this.nalType = nalType;
        }        
    }
        
 	//3.        
    protected static class ByteArrayBuilder {
                
        private final List<BuilderElement> arrays;
        private int totalLength = 0;
                
        public ByteArrayBuilder() {
            arrays = new ArrayList<BuilderElement>();
        }
                
        public ByteArrayBuilder(byte...array) {
            this();
            this.putArray(array);
        }
        
        public void putArray(byte... array) {
            if (array.length == 0) return;
            arrays.add(new BuilderElement(array));
            totalLength += array.length;
        }
        
        public int getLength() {
            return totalLength;
        }
        
        public byte[] buildArray() {
            if (totalLength == 0) return new byte[0];
            byte[] result = new byte[totalLength];
            int pos = 0;
            for (BuilderElement e: arrays) {
                    System.arraycopy(e.array, 0, result, pos, e.array.length);
                    pos += e.array.length;
            }
            
            if (arrays.size() > 1) {
                    clear();
                    putArray(result);
            }
            
            return result;
        }
                
        public void clear() {
            arrays.clear();
            totalLength = 0;
        }

        //inner class of the inner class ByteArrayBuilder...
        private static class BuilderElement {
                
            public final byte[] array;

            public BuilderElement(byte[] array) {
                    super();
                    this.array = array;
            }      
        }            
    }

}

