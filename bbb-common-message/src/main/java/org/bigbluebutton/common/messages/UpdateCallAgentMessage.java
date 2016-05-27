package org.bigbluebutton.common.messages;

import java.util.HashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class UpdateCallAgentMessage implements IBigBlueButtonMessage{
    public static final String UPDATE_CALL_AGENT  = "update_call_agent_message";
    public static final String VERSION = "0.0.1";

    public static final String MEETING_ID = "meeting_id";
    public static final String USER_ID = "userid";
    public static final String LOCAL_IP_ADDRESS = "local_ip_address";
    public static final String LOCAL_VIDEO_PORT = "local_video_port";
    public static final String REMOTE_VIDEO_PORT = "remote_video_port";
    public static final String SIP_HOST = "sip_host";

    public final String meetingId;
    public final String userId;
    public final String localIpAddress;
    public final String localVideoPort;
    public final String remoteVideoPort;
    public final String sipHost;

    public UpdateCallAgentMessage(String meetingId, String userId, String localIpAddress, String localVideoPort, String remoteVideoPort, String sipHost) {
        this.meetingId = meetingId;
        this.userId = userId;
        this.localIpAddress = localIpAddress;
        this.localVideoPort = localVideoPort;
        this.remoteVideoPort = remoteVideoPort;
        this.sipHost = sipHost;
    }

    public String toJson() {
        HashMap<String, Object> payload = new HashMap<String, Object>();
        payload.put(MEETING_ID, meetingId);
        payload.put(USER_ID, userId);
        payload.put(LOCAL_IP_ADDRESS, localIpAddress);
        payload.put(LOCAL_VIDEO_PORT, localVideoPort);
        payload.put(REMOTE_VIDEO_PORT, remoteVideoPort);
        payload.put(SIP_HOST, sipHost);


        java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(UPDATE_CALL_AGENT, VERSION, null);

        return MessageBuilder.buildJson(header, payload);
    }

    public static UpdateCallAgentMessage fromJson(String message) {
        JsonParser parser = new JsonParser();
        JsonObject obj = (JsonObject) parser.parse(message);

        if (obj.has("header") && obj.has("payload")) {
            JsonObject header = (JsonObject) obj.get("header");
            JsonObject payload = (JsonObject) obj.get("payload");

            if (header.has("name")) {
                String messageName = header.get("name").getAsString();
                if (UPDATE_CALL_AGENT.equals(messageName)) {
                    if (payload.has(MEETING_ID)
                            && payload.has(USER_ID)
                            && payload.has(LOCAL_IP_ADDRESS)
                            && payload.has(LOCAL_VIDEO_PORT)
                            && payload.has(REMOTE_VIDEO_PORT)){
                        String meetingId = payload.get(MEETING_ID).getAsString();
                        String userId = payload.get(USER_ID).getAsString();
                        String localIpAddress = payload.get(LOCAL_IP_ADDRESS).getAsString();
                        String localVideoPort = payload.get(LOCAL_VIDEO_PORT).getAsString();
                        String remoteVideoPort = payload.get(REMOTE_VIDEO_PORT).getAsString();
                        String sipHost = payload.get(SIP_HOST).getAsString();
                        return new UpdateCallAgentMessage(meetingId, userId, localIpAddress, localVideoPort, remoteVideoPort, sipHost);
                    }
                }
            }
        }
        return null;
    }
}
