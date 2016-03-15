package org.bigbluebutton.freeswitch.voice;

public interface IVoiceConferenceService {
	void voiceConfRecordingStarted(String voiceConfId, String recordStream, Boolean recording, String timestamp);	
	void userJoinedVoiceConf(String voiceConfId, String voiceUserId, String userId, String callerIdName, 
			String callerIdNum, Boolean muted, Boolean speaking, Boolean hasVideo, Boolean hasFloor);
	void userLeftVoiceConf(String voiceConfId, String voiceUserId);
	void userLockedInVoiceConf(String voiceConfId, String voiceUserId, Boolean locked);
	void userMutedInVoiceConf(String voiceConfId, String voiceUserId, Boolean muted);
	void userTalkingInVoiceConf(String voiceConfId, String voiceUserId, Boolean talking);
    void videoPausedInVoiceConf(String meetingId);
    void videoResumedInVoiceConf(String meetingId);
    void activeTalkerChangedInVoiceConf(String meetingId, String floorHolderVoiceUserId);
    void channelCallStateInVoiceConf(String conference, String uniqueId, String callState,
        String userId);
    void channelHangupInVoiceConf(String conference, String uniqueId, String callState,
        String hangupCause, String userId);
}
