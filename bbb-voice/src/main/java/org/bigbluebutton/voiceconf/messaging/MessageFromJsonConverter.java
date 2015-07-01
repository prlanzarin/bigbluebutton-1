package org.bigbluebutton.voiceconf.messaging;

import org.bigbluebutton.voiceconf.messaging.messages.IMessage;
import org.bigbluebutton.voiceconf.messaging.messages.UpdateSipPhoneStatus;
import org.bigbluebutton.voiceconf.messaging.messages.UpdateVideoStatus;
import org.bigbluebutton.voiceconf.messaging.messages.UserSharedWebcam;
import org.bigbluebutton.voiceconf.messaging.messages.UserUnsharedWebcam;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MessageFromJsonConverter {

	public static IMessage convert(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				switch (messageName) {
				  case UpdateVideoStatus.UPDATE_VIDEO_STATUS_REQUEST_EVENT:
					  return processUpdateVideoStatus(payload);
                  case UserSharedWebcam.USER_SHARED_WEBCAM_EVENT:
                      return processUserSharedWebcam(payload);
                  case UserUnsharedWebcam.USER_UNSHARED_WEBCAM_EVENT:
                      return processUserUnsharedWebcam(payload);
                  case UpdateSipPhoneStatus.UPDATE_SIP_PHONE_STATUS_EVENT:
                      return processUpdateSipPhoneStatus(payload);
				}
			}
		}
		return null;
	}

	private static IMessage processUpdateVideoStatus(JsonObject payload) {
		String voiceBridge = payload.get(Constants.VOICE_CONF).getAsString();
		String floorHolder = payload.get(Constants.TALKER_USER_ID).getAsString();
		Boolean videoPresent = payload.get(Constants.IS_SIP_VIDEO_PRESENT).getAsBoolean();
		return new UpdateVideoStatus(voiceBridge, floorHolder, videoPresent);
	}

    private static IMessage processUserSharedWebcam(JsonObject payload) {
        String userId = payload.get(Constants.USER_ID).getAsString();
        String streamName = payload.get(Constants.STREAM).getAsString();
        return new UserSharedWebcam(userId,streamName);
    }

    private static IMessage processUserUnsharedWebcam(JsonObject payload) {
        String userId = payload.get(Constants.USER_ID).getAsString();
        return new UserUnsharedWebcam(userId);
    }

    private static IMessage processUpdateSipPhoneStatus(JsonObject payload) {
        String voiceBridge = payload.get(Constants.VOICE_CONF).getAsString();
        Boolean sipPhonePresent = payload.get(Constants.IS_SIP_PHONE_PRESENT).getAsBoolean();
        return new UpdateSipPhoneStatus(voiceBridge, sipPhonePresent);
    }
}
