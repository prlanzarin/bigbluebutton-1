package org.bigbluebutton.voiceconf.sip;

public class ListenOnlyUser {

	public final String clientId;
	public final String callerIdName;
	public final String voiceConf;
	public final boolean listeningToAudio;
	
	public ListenOnlyUser(String clientId, String callerIdName, String voiceConf, boolean listeningToAudio) {
	  this.clientId = clientId;
	  this.callerIdName = callerIdName;
	  this.voiceConf = voiceConf;
	  this.listeningToAudio = listeningToAudio;
	}
}
