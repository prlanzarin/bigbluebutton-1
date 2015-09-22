/**
* BigBlueButton open source conferencing system - http://www.bigbluebutton.org/
* 
* Copyright (c) 2012 BigBlueButton Inc. and by respective authors (see below).
*
* This program is free software; you can redistribute it and/or modify it under the
* terms of the GNU Lesser General Public License as published by the Free Software
* Foundation; either version 3.0 of the License, or (at your option) any later
* version.
* 
* BigBlueButton is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
* PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License along
* with BigBlueButton; if not, see <http://www.gnu.org/licenses/>.
*
*/
package org.bigbluebutton.voiceconf.sip;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.red5.logging.Red5LoggerFactory;


public class CallManager {
	private static Logger log = Red5LoggerFactory.getLogger(CallManager.class, "sip");

	private final Map<String, CallAgent> calls = new ConcurrentHashMap<String, CallAgent>();
	private final Map<String, String> videoStreams = new ConcurrentHashMap<String, String>();
	
	public CallAgent add(CallAgent ca) {
		log.debug("Creating entry for the user with userId = "+ca.getUserId());
		return calls.put(ca.getUserId(), ca);
	}
	
	public CallAgent remove(String userId) {
		if(userId != null) {
		    log.debug("Removing callAgent entry for user: " + userId);
	        return calls.remove(userId);
		}else return null;

	}

	public String addVideoStream(String userId, String stream) {
		log.debug("Creating entry (userId, videoStream) = (" + userId + ", " + stream + ")" );
		return videoStreams.put(userId, stream);
	}

	public String removeVideoStream(String userId) {
		String uid = userId;
		log.debug("Removing videoStream entry for user: "  + userId  );
		return videoStreams.remove(uid);
	}

	public CallAgent removeByUserId(String userId) {
	    //kept for compatibility
		return calls.remove(userId);
	}
	
	public CallAgent get(String userId) {
	    CallAgent ca = calls.get(userId);
	    if(ca != null){
	        log.debug("Retrieving entry for the client with userId = " + ca.getUserId());
	        return ca;
	    }
	    else {
	        log.debug("There's no CallAgent for the user with userId = " + userId);
	        return null;
	    }
	}

	public CallAgent getByUserId(String userId) {
	    //kept for compatibility
        log.debug("*Retrieving entry for the client with userId = " + userId);
		return calls.get(userId);
	}

    /**
     * Return the global CallAgent of the given conference.
     * @param voiceconf
     * @return
     */
    public CallAgent getGlobalCallAgent(String voiceconf){
        return getByUserId(GlobalCall.LISTENONLY_USERID_PREFIX+voiceconf);
    }

	public String getVideoStream(String userId) {
		String uid = userId;
		log.debug("[Video context] stream retrieved for the userid " + uid);
			return videoStreams.get(uid);
	}

	public Collection<CallAgent> getAll() {
		return calls.values();
	}

	public Map<String, String> getAllSavedVideoStreams() {
		return videoStreams;
	}
}
