package org.bigbluebutton.common.messages;

import java.util.HashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class ActiveTalkerChangedInVoiceConfMessage implements IBigBlueButtonMessage {
	public static final String ACTIVE_TALKER_CHANGED_IN_VOICE_CONF  = "active_talker_changed_in_voice_conf_message";
	public static final String VERSION = "0.0.1";

	public static final String VOICE_CONF_ID = "voice_conf_id"; //may move this to constants
	public static final String VOICE_USER_ID = "voice_user_id";
	public static final String USER_ID = "user_id";

	public final String voiceConfId;
	public final String voiceUserId;
	public final String userId;

	public ActiveTalkerChangedInVoiceConfMessage(String voiceConfId, String voiceUserId, String userId) {
		this.voiceConfId = voiceConfId;
		this.voiceUserId = voiceUserId;
		this.userId = userId;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(VOICE_CONF_ID, voiceConfId);
		payload.put(VOICE_USER_ID, voiceUserId);
		payload.put(USER_ID, userId);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(ACTIVE_TALKER_CHANGED_IN_VOICE_CONF, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static ActiveTalkerChangedInVoiceConfMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (ACTIVE_TALKER_CHANGED_IN_VOICE_CONF.equals(messageName)) {
					if (payload.has(VOICE_CONF_ID)
							&& payload.has(VOICE_USER_ID)
							&& payload.has(USER_ID)) {
						String voiceConfId = payload.get(VOICE_CONF_ID).getAsString();
						String voiceUserId = payload.get(VOICE_USER_ID).getAsString();
						String userId = payload.get(USER_ID).getAsString();
						return new ActiveTalkerChangedInVoiceConfMessage(voiceConfId, voiceUserId, userId);
					}
				}
			}
		}
		return null;
	}
}
