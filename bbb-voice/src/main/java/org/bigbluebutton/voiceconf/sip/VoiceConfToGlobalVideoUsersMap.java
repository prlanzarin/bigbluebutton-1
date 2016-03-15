package org.bigbluebutton.voiceconf.sip;

import java.util.ArrayList;
import java.util.List;

public class VoiceConfToGlobalVideoUsersMap {
    private List<String> GlobalVideoUsers = new ArrayList<String>();

    public final String voiceConf;

    public VoiceConfToGlobalVideoUsersMap(String voiceConf) {
      this.voiceConf = voiceConf;
    }

    public void addUser(String clientId) {
        GlobalVideoUsers.add(clientId);
    }

    public void removeUser(String clientId) {
        if (GlobalVideoUsers.contains(clientId))
            GlobalVideoUsers.remove(GlobalVideoUsers.indexOf(clientId));
    }

    public int numUsers() {
        return GlobalVideoUsers.size();
    }
}
