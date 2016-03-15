package org.bigbluebutton.voiceconf.messaging.messages;

import org.bigbluebutton.voiceconf.messaging.Constants;

public class UpdateVideoStatus implements IMessage {

    public static final String UPDATE_VIDEO_STATUS_REQUEST_EVENT = Constants.SIP_VIDEO_UPDATE;

    public final String meetingId;
    public final String voiceBridge;
    public final String floorHolder;
    public final Boolean videoPresent;

    public UpdateVideoStatus(String meetingId,String voiceBridge, String floorHolder, Boolean videoPresent) {
        this.meetingId = meetingId;
        this.voiceBridge = voiceBridge;
        this.floorHolder = floorHolder;
        this.videoPresent = videoPresent;
    }
}
