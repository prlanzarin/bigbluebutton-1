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
package org.bigbluebutton.webconference.red5.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bigbluebutton.webconference.voice.events.ConferenceEvent;
import org.bigbluebutton.webconference.voice.events.ChannelCallStateEvent;
import org.bigbluebutton.webconference.voice.events.ChannelHangupCompleteEvent;
import org.bigbluebutton.webconference.voice.events.DialEvent;
import org.bigbluebutton.webconference.voice.events.ParticipantJoinedEvent;
import org.bigbluebutton.webconference.voice.events.ParticipantLeftEvent;
import org.bigbluebutton.webconference.voice.events.ParticipantLockedEvent;
import org.bigbluebutton.webconference.voice.events.ParticipantMutedEvent;
import org.bigbluebutton.webconference.voice.events.ParticipantTalkingEvent;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.so.ISharedObject;
import org.slf4j.Logger;

public class ClientManager implements ClientNotifier {
	private static Logger log = Red5LoggerFactory.getLogger(ClientManager.class, "bigbluebutton");

	private final ConcurrentMap<String, RoomInfo> voiceRooms;
	private final ConcurrentMap<String, RoomInfo> webRooms;
	
	private final ConcurrentMap<String, DialStates> dials;

	public ClientManager() {
		voiceRooms = new ConcurrentHashMap<String, RoomInfo>();
		webRooms = new ConcurrentHashMap<String, RoomInfo>();
		
		dials = new ConcurrentHashMap<String, DialStates>();
	}
	
	public void addSharedObject(String webRoom, String voiceRoom, ISharedObject so) {
		log.debug("Adding SO for [" + webRoom + "," + voiceRoom + "]");
		RoomInfo soi = new RoomInfo(webRoom, voiceRoom, so);
		voiceRooms.putIfAbsent(voiceRoom, soi);
		webRooms.putIfAbsent(webRoom, soi);
	}
	
	public void removeSharedObject(String webRoom) {
		RoomInfo soi = webRooms.remove(webRoom);
		if (soi != null) voiceRooms.remove(soi.getVoiceRoom());
	}
		
	private void joined(String room, Integer participant, String name, Boolean muted, Boolean talking, Boolean locked){
		log.debug("Participant " + name + "joining room " + room);
		RoomInfo soi = voiceRooms.get(room);
		if (soi != null) {
			List<Object> list = new ArrayList<Object>();
			list.add(participant);
			list.add(name);
			list.add(name);
			list.add(muted);
			list.add(talking);
			list.add(locked);
			log.debug("Sending join to client " + name);
			soi.getSharedObject().sendMessage("userJoin", list);
		}				
	}
	
	private void left(String room, Integer participant){
		log.debug("Participant [" + participant + "," + room + "] leaving");
		RoomInfo soi = voiceRooms.get(room);
		if (soi != null) {
			List<Object> list = new ArrayList<Object>();
			list.add(participant);
			soi.getSharedObject().sendMessage("userLeft", list);
		}
	}
	
	private void muted(String room, Integer participant, Boolean muted){
		log.debug("Participant " + participant + " is muted = " + muted);
		RoomInfo soi = voiceRooms.get(room);
		if (soi != null) {
			List<Object> list = new ArrayList<Object>();
			list.add(participant);
			list.add(muted);
			soi.getSharedObject().sendMessage("userMute", list);
		}		
	}
	
	private void locked(String room, Integer participant, Boolean locked){
		log.debug("Participant " + participant + " is locked = " + locked);
		RoomInfo soi = voiceRooms.get(room);
		if (soi != null) {
			List<Object> list = new ArrayList<Object>();
			list.add(participant);
			list.add(locked);
			soi.getSharedObject().sendMessage("userLockedMute", list);
		}		
	}
	
	private void talking(String room, Integer participant, Boolean talking){
		log.debug("Participant " + participant + " is talking = " + talking);
		RoomInfo soi = voiceRooms.get(room);
		if (soi != null) {
			List<Object> list = new ArrayList<Object>();
			list.add(participant);
			list.add(talking);
			soi.getSharedObject().sendMessage("userTalk", list);
		}
	}	
	
	public void handleConferenceEvent(ConferenceEvent event) {
		if (event instanceof ParticipantJoinedEvent) {
			ParticipantJoinedEvent pje = (ParticipantJoinedEvent) event;
			joined(pje.getRoom(), pje.getParticipantId(), pje.getCallerIdName(), pje.getMuted(), pje.getSpeaking(), pje.isLocked());
		} else if (event instanceof ParticipantLeftEvent) {		
			left(event.getRoom(), event.getParticipantId());		
		} else if (event instanceof ParticipantMutedEvent) {
			ParticipantMutedEvent pme = (ParticipantMutedEvent) event;
			muted(pme.getRoom(), pme.getParticipantId(), pme.isMuted());
		} else if (event instanceof ParticipantTalkingEvent) {
			ParticipantTalkingEvent pte = (ParticipantTalkingEvent) event;
			talking(pte.getRoom(), pte.getParticipantId(), pte.isTalking());
		} else if (event instanceof ParticipantLockedEvent) {
			ParticipantLockedEvent ple = (ParticipantLockedEvent) event;
			locked(ple.getRoom(), ple.getParticipantId(), ple.isLocked());
		}
	}
	
	private void dialing(String room, String state) {
	    RoomInfo soi = voiceRooms.get(room);
	    List<Object> list = new ArrayList<Object>();
	    list.add(state);
	    soi.getSharedObject().sendMessage("dialing", list);
	}
	
	private void hangingup(String room, String state, String hangupCause) {
	    RoomInfo soi = voiceRooms.get(room);
	    List<Object> list = new ArrayList<Object>();
	    list.add(state);
	    list.add(hangupCause);
	    soi.getSharedObject().sendMessage("hangingup", list);
	}
	
	public void handleDialEvent(DialEvent event) {	    	    
	    if(event instanceof ChannelCallStateEvent) {
	        ChannelCallStateEvent cse = (ChannelCallStateEvent) event;
	        String uniqueId = cse.getUniqueId();
	        String callState = cse.getCallState();
	        String room = cse.getRoom();  
            
            DialStates dialStates;
	        if (!dials.containsKey(uniqueId))
                dials.put(uniqueId, new DialStates(uniqueId, callState));
                
            dialStates = dials.get(uniqueId);
                
	        System.out.println("[ClientManager] Unique-ID: " + dialStates.getUniqueId());	        
	        
	        dialStates.updateState(callState);
	        System.out.println("[ClientManager] CallState: " + dialStates.getCurrentState());
	        
	        System.out.println("[ClientManager] idName: " + cse.getIdName());
	        System.out.println("[ClientManager] channelName: " + cse.getChannelName());
	        
	        dialing(room, callState);
	    }
	    else if(event instanceof ChannelHangupCompleteEvent) {
	        ChannelHangupCompleteEvent hce = (ChannelHangupCompleteEvent) event;
	        
	        String uniqueId = hce.getUniqueId();
	        String callState = hce.getCallState();
	        String room = hce.getRoom();
	        
	        DialStates dialStates;
	        if (!dials.containsKey(uniqueId))
                dials.put(uniqueId, new DialStates(uniqueId, callState));

            dialStates = dials.get(uniqueId);
	        
	        System.out.println("[ClientManager] Unique-ID: " + dialStates.getUniqueId());

	        String hangupCause = hce.getHangupCause();
	        dialStates.setHangupCause(hangupCause);
	        dialStates.updateState(callState);
	        System.out.println("[ClientManager] Hangup Cause: " + dialStates.getHangupCause());
	        
	        System.out.println("[ClientManager] idName: " + hce.getIdName());
	        System.out.println("[ClientManager] channelName: " + hce.getChannelName());
	        
	        hangingup(room, callState, hangupCause);
	    }
	    else
	        System.out.println("[ClientManager] It was not supposed to be here.");
	}
}
