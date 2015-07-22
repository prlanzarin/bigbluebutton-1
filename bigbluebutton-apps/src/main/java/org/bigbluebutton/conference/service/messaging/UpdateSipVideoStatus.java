package org.bigbluebutton.conference.service.messaging;

public class UpdateSipVideoStatus implements IMessage{

    public static final String UPDATE_SIP_VIDEO_STATUS_EVENT  = "update_sip_video_status";
    public static final String VERSION = "0.0.1";

    public final String meetingId;
    public final String width;
    public final String height;

    public UpdateSipVideoStatus(String meetingId,String width, String height) {
        this.meetingId = meetingId;
        this.width = width;
        this.height = height;
    }
}
