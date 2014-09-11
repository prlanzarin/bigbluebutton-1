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

public class CallManager {

	private final Map<String, CallAgent> calls = new ConcurrentHashMap<String, CallAgent>();
	private final Map<String, String> identifiers = new ConcurrentHashMap<String, String>();
	
	public CallAgent add(CallAgent ca) {
		System.out.println("[CallManager] ca.getUserId(): " + ca.getUserId());
		System.out.println("[CallManager] ca.getCallId(): " + ca.getCallId());
		
		identifiers.put(ca.getUserId(), ca.getCallId());
		return calls.put(ca.getCallId(), ca);
	}
	
	public CallAgent remove(String id) {
		CallAgent ca = calls.get(id);
		String userId = ca.getUserId();

		identifiers.remove(userId);
		return calls.remove(id);
	}

	public CallAgent removeByUserId(String userId) {
		String uid = userId;
		String id;

		if( (id = identifiers.get(uid)) == null )
			return null;
		else {
			identifiers.remove(uid);
			return calls.remove(id);
		}
	}
	
	public CallAgent get(String id) {
		return calls.get(id);
	}

	public CallAgent getByUserId(String userId) {
		String uid = userId;
		String id;

		if( (id = identifiers.get(uid)) == null )
			return null;
		else {
			return calls.get(id);
		}
	}
	
	public Collection<CallAgent> getAll() {
		return calls.values();
	}
}
