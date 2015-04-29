package org.bigbluebutton.conference.service.messaging;

public class GlobalVideoStreamCreated implements IMessage {
	public static final String GLOBAL_VIDEO_STREAM_CREATED_EVENT  = "global_video_stream_created";
	public static final String VERSION = "0.0.1";

	public final String voiceConf;
	public final String videoStreamName;
	
	public GlobalVideoStreamCreated(String voiceConf, String videoStreamName) {
		this.voiceConf = voiceConf;
		this.videoStreamName = videoStreamName;
	}
}
