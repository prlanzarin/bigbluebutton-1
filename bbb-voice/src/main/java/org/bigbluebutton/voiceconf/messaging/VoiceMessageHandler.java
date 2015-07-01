package org.bigbluebutton.voiceconf.messaging;

import org.bigbluebutton.voiceconf.messaging.messages.IMessage;
import org.bigbluebutton.voiceconf.messaging.messages.UpdateSipPhoneStatus;
import org.bigbluebutton.voiceconf.messaging.messages.UpdateVideoStatus;
import org.bigbluebutton.voiceconf.messaging.messages.UserSharedWebcam;
import org.bigbluebutton.voiceconf.messaging.messages.UserUnsharedWebcam;
import org.bigbluebutton.voiceconf.red5.Service;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class VoiceMessageHandler implements MessageHandler {
    private static Logger log = Red5LoggerFactory.getLogger(VoiceMessageHandler.class, "sip");

    private Service service;

    public void handleMessage(String pattern, String channel, String message) {
        if (channel.equalsIgnoreCase(MessagingConstants.TO_BBB_VOICE_CHANNEL)) {
            IMessage msg = MessageFromJsonConverter.convert(message);

            if (msg != null) {
                if(msg instanceof UpdateVideoStatus) {
                    UpdateVideoStatus uvs = (UpdateVideoStatus) msg;
                    log.info("Handling Update Video Status [{}, {}, {}]", uvs.voiceBridge, uvs.floorHolder, uvs.videoPresent);
                    service.updateVideoStatus(uvs.voiceBridge, uvs.floorHolder, uvs.videoPresent);
                }
                else if(msg instanceof UserSharedWebcam) {
                    UserSharedWebcam usw = (UserSharedWebcam) msg;
                    log.info("Handling User Shared Webcam [{}, {}]",usw.userId, usw.streamName);
                    service.userSharedWebcam(usw.userId, usw.streamName);
                }
                else if(msg instanceof UserUnsharedWebcam) {
                    UserUnsharedWebcam uuw = (UserUnsharedWebcam) msg;
                    log.info("Handling User Unshared Webcam [{}]",uuw.userId);
                    service.userUnsharedWebcam(uuw.userId);
                }
                else if (msg instanceof UpdateSipPhoneStatus) {
                    UpdateSipPhoneStatus usps = (UpdateSipPhoneStatus) msg;
                    log.info("Handling Update Sip Phone Status [{}]", usps.sipPhonePresent);
                    service.updateSipPhoneStatus(usps.voiceBridge, usps.sipPhonePresent);
                }
            }
        }
    }

    public void setVoiceService(Service voiceService){
        this.service = voiceService;
    }
}
