package org.bigbluebutton.voiceconf.messaging.messages;

import java.util.HashMap;

import org.bigbluebutton.voiceconf.messaging.Constants;
import org.bigbluebutton.voiceconf.messaging.MessageBuilder;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class UpdateSipVideoStatus {

    private static Logger log = Red5LoggerFactory.getLogger(UpdateSipVideoStatus.class, "sip");
    public static final String SIP_VIDEO_STATUS = "update_sip_video_status_message";
    public static final String VERSION = "0.0.1";

    public final String meetingId;
    public final String width;
    public final String height;

    public UpdateSipVideoStatus(String meetingId,String width, String height) {
        this.meetingId = meetingId;
        this.width = width;
        this.height = height;
    }

    public String toJson() {
        HashMap<String, Object> payload = new HashMap<String, Object>();
        payload.put(Constants.MEETING_ID, meetingId);
        payload.put(Constants.WIDTH_RATIO, width);
        payload.put(Constants.HEIGHT_RATIO, height);

        java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(SIP_VIDEO_STATUS, VERSION, null);

        return MessageBuilder.buildJson(header, payload);
    }
}
