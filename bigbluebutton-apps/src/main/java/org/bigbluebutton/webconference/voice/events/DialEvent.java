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
package org.bigbluebutton.webconference.voice.events;

public class DialEvent {

	private final String uniqueId;
	private final String idName;
	private final String channelName;
	private final String room;
	private final Integer participant;
	
	public DialEvent(String uniqueId, String idName, String channelName, String room, Integer participant) {
		this.uniqueId = uniqueId;
		this.idName = idName;
		this.channelName = channelName;
		this.room = room;
		this.participant = participant;
	}
	
	public String getUniqueId() {
		return this.uniqueId;
	}
	
	public String getIdName() {
	    return this.idName;
	}
	
	public String getChannelName() {
	    return this.channelName;
	}
	
	public String getRoom() {
	    return this.room;
	}

	public Integer getParticipant() {
		return this.participant;
	}
}
