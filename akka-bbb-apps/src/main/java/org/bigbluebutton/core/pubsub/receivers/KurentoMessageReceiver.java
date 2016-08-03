
package org.bigbluebutton.core.pubsub.receivers;

import org.bigbluebutton.common.messages.MessagingConstants;
import org.bigbluebutton.common.messages.StartKurentoRtpReplyMessage;
import org.bigbluebutton.common.messages.StopKurentoRtpReplyMessage;
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
						case UpdateKurentoTokenMessage.UPDATE_KURENTO_TOKEN:
							processUpdateKurentoTokenMessage(message);
							break;
					}
				}
			}
		}
	}

	private void processStartKurentoRtpReplyMessage(String message) {
		StartKurentoRtpReplyMessage msg = StartKurentoRtpReplyMessage.fromJson(message);
		if (msg != null){
			System.out.println("KURENTO START IS NOT NULL");
			bbbInGW.startKurentoRtpReply(msg.meetingId, msg.kurentoEndpointId, msg.params);
		} else {
			System.out.println("KURENTO START IS NULL DUH");
		}
	}

	private void processStopKurentoRtpReplyMessage(String message) {
		StopKurentoRtpReplyMessage msg = StopKurentoRtpReplyMessage.fromJson(message);
		if (msg != null){
			bbbInGW.stopKurentoRtpReply(msg.meetingId, msg.kurentoEndpointId);
		}
	}

	private void processUpdateKurentoTokenMessage(String message) {
		UpdateKurentoTokenMessage msg = UpdateKurentoTokenMessage.fromJson(message);
		if (msg != null){
			bbbInGW.updateKurentoToken(msg.kurentoToken);
		}
	}
}
