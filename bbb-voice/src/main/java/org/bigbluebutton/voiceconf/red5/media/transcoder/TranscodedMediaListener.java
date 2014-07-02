package org.bigbluebutton.voiceconf.red5.media.transcoder;
import org.bigbluebutton.voiceconf.red5.media.transcoder.FlashToSipTranscoder;
import org.bigbluebutton.voiceconf.red5.media.transcoder.TranscodedMediaDataListener;
import org.bigbluebutton.voiceconf.red5.media.RtpStreamSender;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class TranscodedMediaListener implements TranscodedMediaDataListener {
		private final static Logger log = Red5LoggerFactory.getLogger(TranscodedMediaListener.class, "sip");
		private RtpStreamSender rtpSender;
		private final FlashToSipTranscoder transcoder;

		public TranscodedMediaListener(RtpStreamSender sender, FlashToSipTranscoder transcoderFlashToSip ){
			this.rtpSender = sender;
			this.transcoder = transcoderFlashToSip;
		}

		@Override
		public void handleTranscodedMediaData(byte[] videoData, long timestamp) {
			if (videoData != null) {
	  		  rtpSender.sendAudio(videoData, transcoder.getCodecId(), timestamp);
	  	  } else {
	  		  log.warn("Transcodec video is null. Discarding.");
	  	  }
		}

		
	}