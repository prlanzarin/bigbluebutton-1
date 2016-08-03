package org.bigbluebutton.common.messages;

import java.util.Map;
import java.util.HashMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class StopKurentoRtpRequestMessage implements IBigBlueButtonMessage {
	public static final String STOP_KURENTO_RTP_REQUEST  = "stop_kurento_rtp_request_message";
	public static final String VERSION = "0.0.1";

	public static final String MEETING_ID = "meeting_id";
	public static final String KURENTO_ENDPOINT_ID = "kurento_endpoint_id";

	public final String meetingId;
	public final String kurentoEndpointId;

	public StopKurentoRtpRequestMessage(String meetingId, String kurentoEndpointId) {
		this.meetingId = meetingId;
		this.kurentoEndpointId = kurentoEndpointId;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(MEETING_ID, meetingId);
		payload.put(KURENTO_ENDPOINT_ID, kurentoEndpointId);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(STOP_KURENTO_RTP_REQUEST, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static StopKurentoRtpRequestMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (STOP_KURENTO_RTP_REQUEST.equals(messageName)) {
					if (payload.has(MEETING_ID)
						&& payload.has(KURENTO_ENDPOINT_ID)){
						String meetingId = payload.get(MEETING_ID).getAsString();
						String kurentoEndpointId = payload.get(KURENTO_ENDPOINT_ID).getAsString();
						return new StopKurentoRtpRequestMessage(meetingId, kurentoEndpointId);
					}
				}
			}
		}
		return null;
	}
}
