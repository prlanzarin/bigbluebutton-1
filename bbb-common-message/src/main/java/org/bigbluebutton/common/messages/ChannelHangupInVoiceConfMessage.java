package org.bigbluebutton.common.messages;

import java.util.HashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class ChannelHangupInVoiceConfMessage implements IBigBlueButtonMessage {
	public static final String CHANNEL_HANGUP_IN_VOICE_CONF  = "channel_hangup_in_voice_conf_message";
	public static final String VERSION = "0.0.1";

	public static final String MEETING_ID = "meeting_id";
	public static final String VOICE_CONF_ID = "voice_conf_id";
	public static final String UNIQUE_ID = "unique_id";
	public static final String CALL_STATE = "call_state";
	public static final String HANGUP_CAUSE = "hangup_cause";
	public static final String USER_ID = "user_id";

	public final String meetingId;
	public final String voiceConfId;
	public final String uniqueId;
	public final String callState;
	public final String hangupCause;
	public final String userId;

	public ChannelHangupInVoiceConfMessage(String meetingId, String voiceConfId, String uniqueId, String callState, String hangupCause, String userId) {
		this.meetingId = meetingId;
		this.voiceConfId = voiceConfId;
		this.uniqueId = uniqueId;
		this.callState = callState;
		this.hangupCause = hangupCause;
		this.userId = userId;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(MEETING_ID, meetingId);
		payload.put(VOICE_CONF_ID, voiceConfId);
		payload.put(UNIQUE_ID, uniqueId);
		payload.put(CALL_STATE, callState);
		payload.put(HANGUP_CAUSE, hangupCause);
		payload.put(USER_ID, userId);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(CHANNEL_HANGUP_IN_VOICE_CONF, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static ChannelHangupInVoiceConfMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (CHANNEL_HANGUP_IN_VOICE_CONF.equals(messageName)) {
					if (payload.has(MEETING_ID)
						&& payload.has(VOICE_CONF_ID)
						&& payload.has(UNIQUE_ID)
						&& payload.has(CALL_STATE)
						&& payload.has(HANGUP_CAUSE)
						&& payload.has(USER_ID)){
						String meetingId = payload.get(MEETING_ID).getAsString();
						String voiceConfId = payload.get(VOICE_CONF_ID).getAsString();
						String uniqueId = payload.get(UNIQUE_ID).getAsString();
						String callState = payload.get(CALL_STATE).getAsString();
						String hangupCause = payload.get(HANGUP_CAUSE).getAsString();
						String userId = payload.get(USER_ID).getAsString();
						return new ChannelHangupInVoiceConfMessage(meetingId, voiceConfId, uniqueId, callState, hangupCause, userId);
					}
				}
			}
		}
		return null;
	}
}
