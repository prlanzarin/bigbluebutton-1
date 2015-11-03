package org.bigbluebutton.conference.service.messaging;

public class RequestUpdateVideoStatus implements IMessage{

    public static final String REQUEST_UPDATE_VIDEO_STATUS_EVENT  = "request_update_video_status";
    public static final String VERSION = "0.0.1";

    public final String meetingId;
    public final String voiceConf;

    public RequestUpdateVideoStatus(String meetingId, String voiceConf) {
        this.meetingId = meetingId;
        this.voiceConf = voiceConf;
    }
}
