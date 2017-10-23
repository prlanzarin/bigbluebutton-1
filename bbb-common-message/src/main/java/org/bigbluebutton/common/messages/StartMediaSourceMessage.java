package org.bigbluebutton.common.messages;

public class StartMediaSourceMessage implements IBigBlueButtonMessage {
	public static final String START_MEDIA_SOURCE_REQUEST  = "start_media_source_request";
	public static final String VERSION = "0.0.1";

	public final String meetingId;
	public final String mediaSourceId;
	public final String mediaSourceUri;

	public StartMediaSourceMessage(String meetingId, String mediaSourceId, String mediaSourceUri) {
		this.meetingId = meetingId;
		this.mediaSourceId = mediaSourceId;
		this.mediaSourceUri = mediaSourceUri;
	}
}
