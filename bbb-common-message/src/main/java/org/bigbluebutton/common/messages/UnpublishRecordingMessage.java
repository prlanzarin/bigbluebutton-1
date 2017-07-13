package org.bigbluebutton.common.messages;

import java.util.HashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class UnpublishRecordingMessage implements IBigBlueButtonMessage {
	public static final String UNPUBLISH_RECORDING = "unpublished";
	public static final String VERSION = "0.0.1";

	public final String recordId;
	public final String meetingId;
	public final String externalMeetingId;
	public final String format;

	public UnpublishRecordingMessage(String recordId, String meetingId, String externalMeetingId, String format) {
		this.recordId = recordId;
		this.meetingId = meetingId;
		this.externalMeetingId = externalMeetingId;
		this.format = format;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(Constants.RECORD_ID, recordId);
		payload.put(Constants.MEETING_ID, meetingId);
		payload.put(Constants.EXTERNAL_MEETING_ID, externalMeetingId);
		payload.put(Constants.FORMAT, format);

		HashMap<String, Object> header = MessageBuilder.buildHeader(UNPUBLISH_RECORDING, VERSION, null);
		return MessageBuilder.buildJson(header, payload);
	}

	public static UnpublishRecordingMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);
		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (UNPUBLISH_RECORDING.equals(messageName)) {
					if (payload.has(Constants.RECORD_ID)
							&& payload.has(Constants.MEETING_ID)
							&& payload.has(Constants.EXTERNAL_MEETING_ID)
							&& payload.has(Constants.FORMAT)) {
						String recordId = payload.get(Constants.RECORD_ID).getAsString();
						String meetingId = payload.get(Constants.MEETING_ID).getAsString();
						String externalMeetingId = payload.get(Constants.EXTERNAL_MEETING_ID).getAsString();
						String format = payload.get(Constants.FORMAT).getAsString();

						return new UnpublishRecordingMessage(recordId, meetingId, externalMeetingId, format);
					}
				}
			}
		}
		return null;
	}
}