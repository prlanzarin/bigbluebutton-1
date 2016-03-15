package org.bigbluebutton.common.messages;

import java.util.HashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class UpdateSipVideoStatusMessage implements IBigBlueButtonMessage{
    public static final String UPDATE_SIP_VIDEO_STATUS  = "update_sip_video_status_message";
    public static final String VERSION = "0.0.1";

    public static final String MEETING_ID = "meeting_id";
    public static final String WIDTH = "width";
    public static final String HEIGHT = "height";

    public final String meetingId;
    public final String width;
    public final String height;

    public UpdateSipVideoStatusMessage(String meetingId, String width, String height) {
        this.meetingId = meetingId;
        this.width = width;
        this.height = height;
    }

    public String toJson() {
        HashMap<String, Object> payload = new HashMap<String, Object>();
        payload.put(MEETING_ID, meetingId);
        payload.put(WIDTH, width);
        payload.put(HEIGHT, height);

        java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(UPDATE_SIP_VIDEO_STATUS, VERSION, null);

        return MessageBuilder.buildJson(header, payload);
    }

    public static UpdateSipVideoStatusMessage fromJson(String message) {
        JsonParser parser = new JsonParser();
        JsonObject obj = (JsonObject) parser.parse(message);

        if (obj.has("header") && obj.has("payload")) {
            JsonObject header = (JsonObject) obj.get("header");
            JsonObject payload = (JsonObject) obj.get("payload");

            if (header.has("name")) {
                String messageName = header.get("name").getAsString();
                if (UPDATE_SIP_VIDEO_STATUS.equals(messageName)) {
                    if (payload.has(MEETING_ID)
                            && payload.has(WIDTH)
                            && payload.has(HEIGHT)){
                        String meetingId = payload.get(MEETING_ID).getAsString();
                        String width = payload.get(WIDTH).getAsString();
                        String height = payload.get(HEIGHT).getAsString();
                        return new UpdateSipVideoStatusMessage(meetingId, width, height);
                    }
                }
            }
        }
        return null;
    }
}
