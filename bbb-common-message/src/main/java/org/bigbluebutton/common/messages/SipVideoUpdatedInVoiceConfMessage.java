package org.bigbluebutton.common.messages;

import java.util.HashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class SipVideoUpdatedInVoiceConfMessage implements IBigBlueButtonMessage {
	public static final String SIP_VIDEO_UPDATED_IN_VOICE_CONF  = "sip_video_updated_in_voice_conf_message";
	public static final String VERSION = "0.0.1";

	public static final String MEETING_ID = "meeting_id";
	public static final String VOICE_CONF_ID = "voice_conf_id";
	public static final String IS_SIP_VIDEO_PRESENT = "is_sip_video_present";
	public static final String SIP_VIDEO_STREAM_NAME = "sip_video_stream_name";
	public static final String TALKER_USER_ID = "talker_user_id";
	public static final String WIDTH = "width";
	public static final String HEIGHT = "height";


	public final String meetingId;
	public final String voiceConfId;
	public final Boolean isSipVideoPresent;
	public final String sipVideoStreamName;
	public final String talkerUserId;
	public final String width;
	public final String height;

	public SipVideoUpdatedInVoiceConfMessage(String meetingId, String voiceConfId, Boolean isSipVideoPresent, String sipVideoStreamName, String talkerUserId, String width, String height) {
		this.meetingId = meetingId;
		this.voiceConfId = voiceConfId;
		this.isSipVideoPresent = isSipVideoPresent;
		this.sipVideoStreamName = sipVideoStreamName;
		this.talkerUserId = talkerUserId;
		this.width = width;
		this.height = height;

	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(MEETING_ID, meetingId);
		payload.put(VOICE_CONF_ID, voiceConfId);
		payload.put(IS_SIP_VIDEO_PRESENT, isSipVideoPresent);
		payload.put(SIP_VIDEO_STREAM_NAME, sipVideoStreamName);
		payload.put(TALKER_USER_ID, talkerUserId);
		payload.put(WIDTH, width);
		payload.put(HEIGHT, height);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(SIP_VIDEO_UPDATED_IN_VOICE_CONF, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static SipVideoUpdatedInVoiceConfMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (SIP_VIDEO_UPDATED_IN_VOICE_CONF.equals(messageName)) {
					if (payload.has(MEETING_ID)
						&& payload.has(VOICE_CONF_ID)
						&& payload.has(IS_SIP_VIDEO_PRESENT)
						&& payload.has(SIP_VIDEO_STREAM_NAME)
						&& payload.has(TALKER_USER_ID)
						&& payload.has(WIDTH)
						&& payload.has(HEIGHT)){
						String meetingId = payload.get(MEETING_ID).getAsString();
						String voiceConfId = payload.get(VOICE_CONF_ID).getAsString();
						Boolean isSipVideoPresent = Boolean.valueOf(payload.get(IS_SIP_VIDEO_PRESENT).getAsString());
						String sipVideoStreamName = payload.get(SIP_VIDEO_STREAM_NAME).getAsString();
						String talkerUserId = payload.get(TALKER_USER_ID).getAsString();
						String width = payload.get(WIDTH).getAsString();
						String height = payload.get(HEIGHT).getAsString();
						return new SipVideoUpdatedInVoiceConfMessage(meetingId, voiceConfId, isSipVideoPresent, sipVideoStreamName, talkerUserId, width, height);
					}
				}
			}
		}
		return null;
	}
}
