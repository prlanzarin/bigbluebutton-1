package org.bigbluebutton.api.messaging.messages;

public class StopMediaSource implements IMessage {

	public final String meetingId;
	public final String mediaSourceId;

	public StopMediaSource(String meetingId, String mediaSourceId) {
		this.meetingId = meetingId;
		this.mediaSourceId = mediaSourceId;
	}
}
