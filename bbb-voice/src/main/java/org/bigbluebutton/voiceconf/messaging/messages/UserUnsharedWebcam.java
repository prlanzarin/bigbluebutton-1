package org.bigbluebutton.voiceconf.messaging.messages;

import org.bigbluebutton.voiceconf.messaging.Constants;

public class UserUnsharedWebcam implements IMessage {

    public static final String USER_UNSHARED_WEBCAM_EVENT = Constants.USER_UNSHARED_WEBCAM;

    public final String userId;
    public final String streamName;

    public UserUnsharedWebcam(String userId,String streamName) {
        this.userId = userId;
        this.streamName = streamName;
    }
}
