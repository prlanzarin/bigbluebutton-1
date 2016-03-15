package org.bigbluebutton.voiceconf.messaging;

public interface IMessagingService {
	void userConnectedToGlobalAudio(String voiceConf, String callerIdName);
	void userDisconnectedFromGlobalAudio(String voiceConf, String callerIdName);
	void globalVideoStreamCreated(String meetingId, String streamName);
	void updateSipVideoStatus(String meetingId, String width, String height);
	void requestUpdateVideoStatus(String meetingId, String destination);
}
