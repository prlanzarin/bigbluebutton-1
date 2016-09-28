
package org.bigbluebutton.core.pubsub.receivers;

import org.bigbluebutton.common.messages.MessagingConstants;
import org.bigbluebutton.common.messages.AllMediaSourcesStoppedMessage;
import org.bigbluebutton.common.messages.StartKurentoRtpReplyMessage;
import org.bigbluebutton.common.messages.StopKurentoRtpReplyMessage;
import org.bigbluebutton.common.messages.UpdateKurentoRtpMessage;
import org.bigbluebutton.common.messages.UpdateKurentoTokenMessage;
import org.bigbluebutton.core.api.IBigBlueButtonInGW;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

public class KurentoMessageReceiver implements MessageHandler{

	private IBigBlueButtonInGW bbbInGW;

	public KurentoMessageReceiver(IBigBlueButtonInGW bbbInGW) {
		this.bbbInGW = bbbInGW;
	}

	@Override
	public void handleMessage(String pattern, String channel, String message) {
		if (channel.equalsIgnoreCase(MessagingConstants.FROM_KURENTO_SYSTEM_CHAN)) {
			JsonParser parser = new JsonParser();
			JsonObject obj = (JsonObject) parser.parse(message);
			if (obj.has("header") && obj.has("payload")) {
				JsonObject header = (JsonObject) obj.get("header");
				if (header.has("name")) {
					String messageName = header.get("name").getAsString();
					switch (messageName) {
						case StartKurentoRtpReplyMessage.START_KURENTO_RTP_REPLY:
							processStartKurentoRtpReplyMessage(message);
							break;
						case StopKurentoRtpReplyMessage.STOP_KURENTO_RTP_REPLY:
							processStopKurentoRtpReplyMessage(message);
							break;
						case UpdateKurentoRtpMessage.UPDATE_KURENTO_RTP:
							processUpdateKurentoRtpMessage(message);
							break;
						case UpdateKurentoTokenMessage.UPDATE_KURENTO_TOKEN:
							processUpdateKurentoTokenMessage(message);
							break;
						case AllMediaSourcesStoppedMessage.ALL_MEDIA_SOURCES_STOPPED:
							processAllMediaSourcesStoppedMessage(message);
							break;
					}
				}
			}
		}
	}

	private void processAllMediaSourcesStoppedMessage(String message) {
		AllMediaSourcesStoppedMessage msg = AllMediaSourcesStoppedMessage.fromJson(message);
		if (msg != null) {
			System.out.println("Message AllMediaSourcesStoppedMessage is not null");
			bbbInGW.allMediaSourcesStopped(msg.meetingId);
		} else {
			System.out.println("AllMediaSourcesStoppedMessage is NULL");
		}
	}

	private void processStartKurentoRtpReplyMessage(String message) {
		StartKurentoRtpReplyMessage msg = StartKurentoRtpReplyMessage.fromJson(message);
		if (msg != null){
			bbbInGW.startKurentoRtpReply(msg.meetingId, msg.kurentoEndpointId, msg.params);
		}
	}

	private void processStopKurentoRtpReplyMessage(String message) {
		StopKurentoRtpReplyMessage msg = StopKurentoRtpReplyMessage.fromJson(message);
		if (msg != null){
			bbbInGW.stopKurentoRtpReply(msg.meetingId, msg.kurentoEndpointId);
		}
	}

	private void processUpdateKurentoRtpMessage(String message) {
		UpdateKurentoRtpMessage msg = UpdateKurentoRtpMessage.fromJson(message);
		if (msg != null){
			bbbInGW.updateKurentoRtp(msg.meetingId, msg.kurentoEndpointId, msg.params);
		}
	}

	private void processUpdateKurentoTokenMessage(String message) {
		UpdateKurentoTokenMessage msg = UpdateKurentoTokenMessage.fromJson(message);
		if (msg != null){
			bbbInGW.updateKurentoToken(msg.kurentoToken);
		}
	}
}
