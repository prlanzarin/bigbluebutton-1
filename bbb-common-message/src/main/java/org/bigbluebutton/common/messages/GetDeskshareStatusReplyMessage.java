package org.bigbluebutton.common.messages;

import java.util.Map;
import java.util.HashMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;


public class GetDeskshareStatusReplyMessage implements IBigBlueButtonMessage {
	public static final String GET_DESKSHARE_STATUS_REPLY  = "get_deskshare_status_reply_message";
	public static final String VERSION = "0.0.1";

	public static final String MEETING_ID = "meeting_id";
	public static final String DESKSHARE_PRESENT = "deskshare_present";

	public final String meetingId;
	public final Boolean desksharePresent;

	public GetDeskshareStatusReplyMessage(String meetingId, Boolean desksharePresent) {
		this.meetingId = meetingId;
		this.desksharePresent = desksharePresent;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(MEETING_ID, meetingId);
		payload.put(DESKSHARE_PRESENT, desksharePresent);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(GET_DESKSHARE_STATUS_REPLY, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static GetDeskshareStatusReplyMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (GET_DESKSHARE_STATUS_REPLY.equals(messageName)) {
					if (payload.has(MEETING_ID)
						&& payload.has(DESKSHARE_PRESENT)){
						String meetingId = payload.get(MEETING_ID).getAsString();
						Boolean desksharePresent = payload.get(DESKSHARE_PRESENT).getAsBoolean();
						return new GetDeskshareStatusReplyMessage(meetingId, desksharePresent);
					}
				}
			}
		}
		return null;
	}
}
