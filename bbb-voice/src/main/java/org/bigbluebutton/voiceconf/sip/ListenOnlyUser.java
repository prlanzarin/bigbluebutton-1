package org.bigbluebutton.voiceconf.sip;

public class ListenOnlyUser {

	public final String clientId;
	public final String callerIdName;
    public final String userId;
	public final String voiceConf;
	public final boolean listeningToAudio;
	
	public ListenOnlyUser(String clientId, String callerIdName, String userId, String voiceConf, boolean listeningToAudio) {
	  this.clientId = clientId;
	  this.callerIdName = callerIdName;
      this.userId = userId;
	  this.voiceConf = voiceConf;
	  this.listeningToAudio = listeningToAudio;
	}
}
