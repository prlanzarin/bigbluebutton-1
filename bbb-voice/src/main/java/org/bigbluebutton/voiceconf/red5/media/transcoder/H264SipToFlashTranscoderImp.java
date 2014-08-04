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
import org.bigbluebutton.voiceconf.red5.media.SipToFlashStream;

import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

/**
 * H264 wideband to H264 wideband Sip to Flash Transcoder.
 * This is just a passthrough transcoder.
 *
 */

public class H264SipToFlashTranscoderImp implements SipToFlashTranscoder {

	protected static Logger log = Red5LoggerFactory.getLogger(H264SipToFlashTranscoderImp.class, "sip");
	
	private static final int H264_CODEC = 35; //which is the H264 Id?
	private Codec videoCodec = null;
	private long timestamp = 0;

	//The timestamp delta between 2 video packets is variable
	private long currentTimestampIncrement = 0; 


	private TranscodedMediaDataListener transcodedMediaDataListener;


	public H264SipToFlashTranscoderImp(Codec codec) {
		this.videoCodec = codec;
        //Random rgen = new Random();
        //timestamp = rgen.nextInt(1000);
	}

	@Override
	public void transcode(byte[] videoData ) {
		transcodedMediaDataListener.handleTranscodedMediaData(videoData, timestamp += currentTimestampIncrement);
	}
	
	@Override
	public int getCodecId() {
		return H264_CODEC;
	}

	@Override
	public int getIncomingEncodedFrameSize() {
		return videoCodec.getIncomingEncodedFrameSize();
	}

	@Override
	public void handleData(byte[] videoData, int offset, int len, long timestampDelta) {
		byte[] data = new byte[len];
		System.arraycopy(videoData, offset, data, 0, len);

		currentTimestampIncrement = timestampDelta;
		transcode(data);		
	}


	@Override
	public void setTranscodedMediaDataListener(SipToFlashStream sipToFlashStream) {
		this.transcodedMediaDataListener = sipToFlashStream;
		
	}

	@Override
	public void start() {
		// do nothing
	}

	@Override
	public void stop() {
		// do nothing
	}
}