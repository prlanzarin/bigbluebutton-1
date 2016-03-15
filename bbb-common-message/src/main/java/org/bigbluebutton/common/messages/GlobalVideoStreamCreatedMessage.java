package org.bigbluebutton.common.messages;

import java.util.HashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class GlobalVideoStreamCreatedMessage implements IBigBlueButtonMessage {
	public static final String GLOBAL_VIDEO_STREAM_CREATED  = "global_video_stream_created_message";
	public static final String VERSION = "0.0.1";

	public static final String MEETING_ID = "meeting_id";
	public static final String VIDEO_STREAM_NAME = "video_Stream_name";

	public final String meetingId;
	public final String videoStreamName;

	public GlobalVideoStreamCreatedMessage(String meetingId, String videoStreamName) {
		this.meetingId = meetingId;
		this.videoStreamName = videoStreamName;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(MEETING_ID, meetingId);
		payload.put(VIDEO_STREAM_NAME, videoStreamName);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(GLOBAL_VIDEO_STREAM_CREATED, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static GlobalVideoStreamCreatedMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (GLOBAL_VIDEO_STREAM_CREATED.equals(messageName)) {
					if (payload.has(MEETING_ID)
						&& payload.has(VIDEO_STREAM_NAME)){
						String meetingId = payload.get(MEETING_ID).getAsString();
						String videoStreamName = payload.get(VIDEO_STREAM_NAME).getAsString();
						return new GlobalVideoStreamCreatedMessage(meetingId, videoStreamName);
					}
				}
			}
		}
		return null;
	}
}
