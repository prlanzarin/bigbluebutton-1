package org.bigbluebutton.voiceconf.sip;

public interface CallAgentObserver {
    public void handleCallAgentClosed(String clientId, String callerName, String userId, String destination, String meetingId, String serverIp);
}
