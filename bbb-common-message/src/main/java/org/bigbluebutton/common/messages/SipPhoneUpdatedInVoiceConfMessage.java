package org.bigbluebutton.common.messages;

import java.util.HashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class SipPhoneUpdatedInVoiceConfMessage implements IBigBlueButtonMessage {
	public static final String SIP_PHONE_UPDATED_IN_VOICE_CONF  = "sip_phone_updated_in_voice_conf_message";
	public static final String VERSION = "0.0.1";

	public static final String MEETING_ID = "meeting_id";
	public static final String VOICE_CONF_ID = "voice_conf_id";
	public static final String IS_SIP_PHONE_PRESENT = "is_sip_phone_present";

	public final String meetingId;
	public final String voiceConfId;
	public final Boolean isSipPhonePresent;

	public SipPhoneUpdatedInVoiceConfMessage(String meetingId, String voiceConfId, Boolean isSipPhonePresent) {
		this.meetingId = meetingId;
		this.voiceConfId = voiceConfId;
		this.isSipPhonePresent = isSipPhonePresent;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(MEETING_ID, meetingId);
		payload.put(VOICE_CONF_ID, voiceConfId);
		payload.put(IS_SIP_PHONE_PRESENT, isSipPhonePresent);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(SIP_PHONE_UPDATED_IN_VOICE_CONF, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static SipPhoneUpdatedInVoiceConfMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (SIP_PHONE_UPDATED_IN_VOICE_CONF.equals(messageName)) {
					if (payload.has(MEETING_ID)
						&& payload.has(VOICE_CONF_ID)
						&& payload.has(IS_SIP_PHONE_PRESENT)){
						String meetingId = payload.get(MEETING_ID).getAsString();
						String voiceConfId = payload.get(VOICE_CONF_ID).getAsString();
						Boolean isSipPhonePresent = Boolean.valueOf(payload.get(IS_SIP_PHONE_PRESENT).getAsString());
						return new SipPhoneUpdatedInVoiceConfMessage(meetingId, voiceConfId, isSipPhonePresent);
					}
				}
			}
		}
		return null;
	}
}
