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
package org.bigbluebutton.voiceconf.red5.media.transcoder;

import java.util.Random;

import org.red5.app.sip.codecs.Codec;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;
import org.bigbluebutton.voiceconf.red5.media.FlashToSipStream;

/**
 * H264 wideband to H264 wideband Flash to SIP transcoder.
 * This class is just a passthrough transcoder.
 *
 */
public class H264FlashToSipTranscoderImp implements FlashToSipTranscoder {
	protected static Logger log = Red5LoggerFactory.getLogger(H264FlashToSipTranscoderImp.class, "sip");
	
	private Codec videoCodec;
	private long timestamp = 0;
	private final static int TS_INCREMENT = 320; // Determined from PCAP traces. //qual o TS_INCREMENT do H264?
	private TranscodedMediaDataListener transcodedMediaDataListener;
	
	public H264FlashToSipTranscoderImp(Codec videoCodec) {
		this.videoCodec = videoCodec;
        Random rgen = new Random();
        timestamp = rgen.nextInt(1000);
	}
	
	public void transcode(byte[] videoData, int startOffset, int length) {
		byte[] transcodedVideo = new byte[length];
		// Just copy the video data removing the codec id which is the first-byte
		// represented by the startOffset var.
		System.arraycopy(videoData, startOffset, transcodedVideo, 0, length);
		transcodedMediaDataListener.handleTranscodedMediaData(transcodedVideo, timestamp += TS_INCREMENT);
	}
	
	public int getCodecId() {
		return videoCodec.getCodecId();
	}

	public int getOutgoingEncodedFrameSize() {
		return videoCodec.getOutgoingEncodedFrameSize();
	}

	public int getOutgoingPacketization() {
		return videoCodec.getOutgoingPacketization();
	}

	@Override
	public void handlePacket(byte[] data, int begin, int end) {
		transcode(data, begin, end);		
	}

	@Override
	public void setTranscodedMediaDataListener(FlashToSipStream flashToSipStream) {
		this.transcodedMediaDataListener = flashToSipStream;		
	}

	@Override
	public void start() {
		// do nothing. just implement the interface.
	}
	
	@Override
	public void stop() {
		// do nothing. just implement the interface.
	}
}
