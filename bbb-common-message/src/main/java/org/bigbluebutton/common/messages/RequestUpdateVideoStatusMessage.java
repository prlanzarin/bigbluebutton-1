package org.bigbluebutton.common.messages;

import java.util.HashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class RequestUpdateVideoStatusMessage implements IBigBlueButtonMessage{
    public static final String REQUEST_UPDATE_VIDEO_STATUS  = "request_update_video_status_message";
    public static final String VERSION = "0.0.1";

    public static final String MEETING_ID = "meeting_id";

	public final String meetingId;

	public RequestUpdateVideoStatusMessage(String meetingId) {
		this.meetingId = meetingId;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(MEETING_ID, meetingId);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(REQUEST_UPDATE_VIDEO_STATUS, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static RequestUpdateVideoStatusMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (REQUEST_UPDATE_VIDEO_STATUS.equals(messageName)) {
					if (payload.has(MEETING_ID)){
						String meetingId = payload.get(MEETING_ID).getAsString();
						return new RequestUpdateVideoStatusMessage(meetingId);
					}
				}
			}
		}
		return null;
	}
}
