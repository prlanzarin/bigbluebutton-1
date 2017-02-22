package org.bigbluebutton.common.messages;

import java.util.Map;
import java.util.HashMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class StartDeskshareViewingMessage implements IBigBlueButtonMessage {
	public static final String START_DESKSHARE_VIEWING = "start_deskshare_viewing_message";
	public static final String VERSION = "0.0.1";

	public static final String MEETING_ID = "meeting_id";
	public static final String WIDTH = "width";
	public static final String HEIGHT = "height";

	public final String meetingId;
	public final int videoWidth;
	public final int videoHeight;

	public StartDeskshareViewingMessage(String meetingId, int videoWidth, int videoHeight) {
		this.meetingId = meetingId;
		this.videoWidth = videoWidth;
		this.videoHeight = videoHeight;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(MEETING_ID, meetingId);
		payload.put(WIDTH, videoWidth);
		payload.put(HEIGHT, videoHeight);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(START_DESKSHARE_VIEWING, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static StartDeskshareViewingMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (START_DESKSHARE_VIEWING.equals(messageName)) {
					if (payload.has(MEETING_ID)
						&& payload.has(WIDTH)
						&& payload.has(HEIGHT)){
						String meetingId = payload.get(MEETING_ID).getAsString();
						int videoWidth = payload.get(WIDTH).getAsInt();
						int videoHeight = payload.get(HEIGHT).getAsInt();
						return new StartDeskshareViewingMessage(meetingId, videoWidth, videoHeight);
					}
				}
			}
		}
		return null;
	}
}
