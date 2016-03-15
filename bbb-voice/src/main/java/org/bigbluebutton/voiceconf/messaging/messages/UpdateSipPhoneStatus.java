package org.bigbluebutton.voiceconf.messaging.messages;

import org.bigbluebutton.voiceconf.messaging.Constants;

public class UpdateSipPhoneStatus implements IMessage {

    public static final String UPDATE_SIP_PHONE_STATUS_EVENT = Constants.SIP_PHONE_UPDATE;

    public final String voiceBridge;
    public final Boolean sipPhonePresent;
    public final String meetingId;

    public UpdateSipPhoneStatus(String voiceBridge, Boolean sipPhonePresent,String meetingId) {
        this.voiceBridge =  voiceBridge;
        this.sipPhonePresent = sipPhonePresent;
        this.meetingId = meetingId;
    }

}
