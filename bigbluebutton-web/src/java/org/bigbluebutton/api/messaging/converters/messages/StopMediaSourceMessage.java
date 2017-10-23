package org.bigbluebutton.api.messaging.converters.messages;

public class StopMediaSourceMessage {
	public static final String STOP_MEDIA_SOURCE_REQUEST  = "stop_media_source_request";
	public static final String VERSION = "0.0.1";

	public final String meetingId;
	public final String mediaSourceId;

	public StopMediaSourceMessage(String meetingId, String mediaSourceId) {
		this.meetingId = meetingId;
		this.mediaSourceId = mediaSourceId;
	}
}
