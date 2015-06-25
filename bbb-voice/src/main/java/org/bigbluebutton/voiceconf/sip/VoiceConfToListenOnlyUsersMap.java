package org.bigbluebutton.voiceconf.sip;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceConfToListenOnlyUsersMap {
	private Map<String, ListenOnlyUser> listenOnlyUsers = new ConcurrentHashMap<String, ListenOnlyUser>();
	
	public final String voiceConf;
	
	public VoiceConfToListenOnlyUsersMap(String voiceConf) {
	  this.voiceConf = voiceConf;	
	}
	
	public void addUser(String clientId, String callerIdName, String userId, boolean listeningToAudio) {
		listenOnlyUsers.put(clientId, new ListenOnlyUser(clientId, callerIdName, userId ,voiceConf,listeningToAudio));
	}
	
	public ListenOnlyUser removeUser(String clientId) {
		return listenOnlyUsers.remove(clientId);
	}
	
	public void setUserListeningStatus(String clientId, boolean newStatus) {
		listenOnlyUsers.get(clientId).listeningToAudio = newStatus;
	}

	public int numUsers() {
		return listenOnlyUsers.size();
	}

	public ListenOnlyUser getListenOnlyUser(String clientId) {
		return listenOnlyUsers.get(clientId);
	}

    public List<ListenOnlyUser> getListenOnlyUsers(){
        return new ArrayList<ListenOnlyUser>(listenOnlyUsers.values());
    }
}
