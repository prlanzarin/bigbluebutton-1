package org.bigbluebutton.voiceconf.messaging.messages;

import org.bigbluebutton.voiceconf.messaging.Constants;

public class UserSharedWebcam implements IMessage {

    public static final String USER_SHARED_WEBCAM_EVENT = Constants.USER_SHARED_WEBCAM;

    public final String userId;
    public final String streamName;

    public UserSharedWebcam( String userId, String streamName) {
        this.userId = userId;
        this.streamName = streamName;
    }
}
