package org.bigbluebutton.voiceconf.messaging.messages;

import java.util.HashMap;

import org.bigbluebutton.voiceconf.messaging.Constants;
import org.bigbluebutton.voiceconf.messaging.MessageBuilder;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class RequestUpdateVideoStatus {

    private static Logger log = Red5LoggerFactory.getLogger(RequestUpdateVideoStatus.class, "sip");
    public static final String REQUEST_UPDATE_VIDEO_STATUS = "request_update_video_status";
    public static final String VERSION = "0.0.1";

    public final String meetingId;
    public final String voiceConf;

    public RequestUpdateVideoStatus(String meetingId,String voiceConf) {
        this.meetingId = meetingId;
        this.voiceConf = voiceConf;
    }

    public String toJson() {
        HashMap<String, Object> payload = new HashMap<String, Object>();
        payload.put(Constants.MEETING_ID, meetingId);
        payload.put(Constants.VOICE_CONF, voiceConf);

        java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(REQUEST_UPDATE_VIDEO_STATUS, VERSION, null);

        return MessageBuilder.buildJson(header, payload);
    }
}
