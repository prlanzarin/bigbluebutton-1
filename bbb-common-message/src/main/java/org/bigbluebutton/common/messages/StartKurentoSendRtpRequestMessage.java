package org.bigbluebutton.common.messages;

import java.util.Map;
import java.util.HashMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class StartKurentoSendRtpRequestMessage implements IBigBlueButtonMessage {
	public static final String START_KURENTO_SEND_RTP_REQUEST = "start_kurento_send_rtp_request_message";
	public static final String VERSION = "0.0.1";

	public static final String MEETING_ID = "meeting_id";
	public static final String PARAMS = "params";

	public final String meetingId;
	public final Map<String,String> params;

	public StartKurentoSendRtpRequestMessage(String meetingId, Map<String,String>  params) {
		this.meetingId = meetingId;
		this.params = params;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(MEETING_ID, meetingId);
		payload.put(PARAMS, params);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(START_KURENTO_SEND_RTP_REQUEST, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static StartKurentoSendRtpRequestMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (START_KURENTO_SEND_RTP_REQUEST.equals(messageName)) {
					if (payload.has(MEETING_ID)
						&& payload.has(PARAMS)){
						String meetingId = payload.get(MEETING_ID).getAsString();
						Map<String,String> params = new Gson().fromJson(payload.get(PARAMS).toString(), new TypeToken<Map<String, String>>() {}.getType());
						return new StartKurentoSendRtpRequestMessage(meetingId, params);
					}
				}
			}
		}
		return null;
	}
}
