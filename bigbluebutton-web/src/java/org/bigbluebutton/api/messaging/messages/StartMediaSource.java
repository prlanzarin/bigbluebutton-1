package org.bigbluebutton.api.messaging.messages;

public class StartMediaSource implements IMessage {

	public final String meetingId;
	public final String mediaSourceId;
	public final String mediaSourceUri;

	public StartMediaSource(String meetingId, String mediaSourceId, String mediaSourceUri) {
		this.meetingId = meetingId;
		this.mediaSourceId = mediaSourceId;
		this.mediaSourceUri = mediaSourceUri;
	}
}
