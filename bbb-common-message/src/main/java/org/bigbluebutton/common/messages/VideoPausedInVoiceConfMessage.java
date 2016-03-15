package org.bigbluebutton.common.messages;

import java.util.HashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class VideoPausedInVoiceConfMessage implements IBigBlueButtonMessage {
	public static final String VIDEO_PAUSED_IN_VOICE_CONF  = "video_paused_in_voice_conf_message";
	public static final String VERSION = "0.0.1";

	public static final String VOICE_CONF_ID = "voice_conf_id";

	public final String voiceConfId;

	public VideoPausedInVoiceConfMessage(String voiceConfId) {
		this.voiceConfId = voiceConfId;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(VOICE_CONF_ID, voiceConfId);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(VIDEO_PAUSED_IN_VOICE_CONF, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static VideoPausedInVoiceConfMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (VIDEO_PAUSED_IN_VOICE_CONF.equals(messageName)) {
					if (payload.has(VOICE_CONF_ID)) {
						String voiceConfId = payload.get(VOICE_CONF_ID).getAsString();
						return new VideoPausedInVoiceConfMessage(voiceConfId);
					}
				}
			}
		}
		return null;
	}
}
