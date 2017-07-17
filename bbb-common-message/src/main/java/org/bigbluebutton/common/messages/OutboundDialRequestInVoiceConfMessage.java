package org.bigbluebutton.common.messages;

import java.util.Map;
import java.util.HashMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;


public class OutboundDialRequestInVoiceConfMessage implements IBigBlueButtonMessage {
	public static final String OUTBOUND_DIAL_REQUEST_IN_VOICE_CONF  = "outbound_dial_request_in_voice_conf_message";
	public static final String VERSION = "0.0.1";

	public static final String MEETING_ID = "meeting_id";
	public static final String VOICE_CONF_ID = "voice_conf_id";
	public static final String USER_ID = "user_id";
	public static final String OPTIONS = "options";
	public static final String PARAMS = "params";

	public final String meetingId;
	public final String voiceConfId;
	public final String userId;
	public final Map<String,String> options;
	public final Map<String,String> params;

	public OutboundDialRequestInVoiceConfMessage(String meetingId, String voiceConfId, String userId, Map<String,String> options, Map<String,String> params) {
		this.meetingId = meetingId;
		this.voiceConfId = voiceConfId;
		this.userId = userId;
		this.options = options;
		this.params = params;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(MEETING_ID, meetingId);
		payload.put(VOICE_CONF_ID, voiceConfId);
		payload.put(USER_ID, userId);
		payload.put(OPTIONS, options);
		payload.put(PARAMS, params);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(OUTBOUND_DIAL_REQUEST_IN_VOICE_CONF, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static OutboundDialRequestInVoiceConfMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (OUTBOUND_DIAL_REQUEST_IN_VOICE_CONF.equals(messageName)) {
					if (payload.has(MEETING_ID)
						&& payload.has(VOICE_CONF_ID)
						&& payload.has(USER_ID)
						&& payload.has(OPTIONS)
						&& payload.has(PARAMS)){
						String meetingId = payload.get(MEETING_ID).getAsString();
						String voiceConfId = payload.get(VOICE_CONF_ID).getAsString();
						String userId = payload.get(USER_ID).getAsString();
						Map<String,String> options = new Gson().fromJson(payload.get(OPTIONS).toString(), new TypeToken<Map<String, String>>() {}.getType());
						Map<String,String> params = new Gson().fromJson(payload.get(PARAMS).toString(), new TypeToken<Map<String, String>>() {}.getType());
						return new OutboundDialRequestInVoiceConfMessage(meetingId, voiceConfId, userId, options, params);
					}
				}
			}
		}
		return null;
	}
}
