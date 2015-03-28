package org.bigbluebutton.voiceconf.red5.media.transcoder;

public class RtpData {

	public final byte[] data;
	public final long timestamp;
	
	public RtpData(byte[] data, long timestamp) {
		this.data = data;
		this.timestamp = timestamp;
	}
}
