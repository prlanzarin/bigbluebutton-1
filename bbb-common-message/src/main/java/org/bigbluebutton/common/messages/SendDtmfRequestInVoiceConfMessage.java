package org.bigbluebutton.common.messages;

import java.util.HashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class SendDtmfRequestInVoiceConfMessage {
	public static final String SEND_DTMF_REQUEST_IN_VOICE_CONF  = "send_dtmf_request_in_voice_conf_message";
	public static final String VERSION = "0.0.1";

	public static final String MEETING_ID = "meeting_id";
	public static final String USER_ID = "user_id";
	public static final String UNIQUE_ID = "unique_id";
	public static final String DTMF_DIGIT = "dtmf_digit";

	public final String meetingId;
	public final String userId;
	public final String uniqueId;
	public final String dtmfDigit;

	public SendDtmfRequestInVoiceConfMessage(String meetingId, String userId, String uniqueId, String dtmfDigit) {
		this.meetingId = meetingId;
		this.userId = userId;
		this.uniqueId = uniqueId;
		this.dtmfDigit = dtmfDigit;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(MEETING_ID, meetingId);
		payload.put(USER_ID, userId);
		payload.put(UNIQUE_ID, uniqueId);
		payload.put(DTMF_DIGIT, dtmfDigit);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(SEND_DTMF_REQUEST_IN_VOICE_CONF, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static SendDtmfRequestInVoiceConfMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (SEND_DTMF_REQUEST_IN_VOICE_CONF.equals(messageName)) {
					if (payload.has(MEETING_ID)
						&& payload.has(USER_ID)
						&& payload.has(UNIQUE_ID)
						&& payload.has(DTMF_DIGIT)){
						String meetingId = payload.get(MEETING_ID).getAsString();
						String userId = payload.get(USER_ID).getAsString();
						String uniqueId = payload.get(UNIQUE_ID).getAsString();
						String dtmfDigit = payload.get(DTMF_DIGIT).getAsString();
						return new SendDtmfRequestInVoiceConfMessage(meetingId, userId, uniqueId, dtmfDigit);
					}
				}
			}
		}
		return null;
	}
}
