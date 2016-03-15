package org.bigbluebutton.voiceconf.messaging.messages;

import java.util.HashMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bigbluebutton.voiceconf.messaging.Constants;
import org.bigbluebutton.voiceconf.messaging.MessageBuilder;
import org.bigbluebutton.voiceconf.messaging.RedisMessagingService;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class GlobalVideoStreamCreated {

	private static Logger log = Red5LoggerFactory.getLogger(GlobalVideoStreamCreated.class, "sip");
	public static final String GLOBAL_VIDEO_STREAM_CREATED = "global_video_stream_created_message";
	public static final String VERSION = "0.0.1";

	public final String meetingId;
	public final String videoStreamName;

	public GlobalVideoStreamCreated(String meetingId,String videoStreamName) {
		this.meetingId = meetingId;
		this.videoStreamName = videoStreamName;

	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(Constants.MEETING_ID, meetingId);
		payload.put(Constants.VIDEO_STREAM_NAME, videoStreamName);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(GLOBAL_VIDEO_STREAM_CREATED, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

}
