package org.bigbluebutton.common.messages;

import java.util.Map;
import java.util.HashMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class StartDeskshareRtpRequestMessage implements IBigBlueButtonMessage {
	public static final String START_DESKSHARE_RTP_REQUEST  = "start_deskshare_rtp_request_message";
	public static final String VERSION = "0.0.1";

	public static final String MEETING_ID = "meeting_id";

	public final String meetingId;

	public StartDeskshareRtpRequestMessage(String meetingId) {
		this.meetingId = meetingId;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(MEETING_ID, meetingId);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(START_DESKSHARE_RTP_REQUEST, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static StartDeskshareRtpRequestMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (START_DESKSHARE_RTP_REQUEST.equals(messageName)) {
					if (payload.has(MEETING_ID)){
						String meetingId = payload.get(MEETING_ID).getAsString();
						return new StartDeskshareRtpRequestMessage(meetingId);
					}
				}
			}
		}
		return null;
	}
}
