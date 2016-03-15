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
package org.bigbluebutton.freeswitch.voice.freeswitch;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bigbluebutton.freeswitch.voice.events.ConferenceEventListener;
import org.bigbluebutton.freeswitch.voice.freeswitch.actions.BroadcastConferenceCommand;
import org.bigbluebutton.freeswitch.voice.freeswitch.actions.CancelDialCommand;
import org.bigbluebutton.freeswitch.voice.freeswitch.actions.DialCommand;
import org.bigbluebutton.freeswitch.voice.freeswitch.actions.SendDtmfCommand;
import org.bigbluebutton.freeswitch.voice.freeswitch.actions.EjectAllUsersCommand;
import org.bigbluebutton.freeswitch.voice.freeswitch.actions.EjectUserCommand;
import org.bigbluebutton.freeswitch.voice.freeswitch.actions.MuteUserCommand;
import org.bigbluebutton.freeswitch.voice.freeswitch.actions.GetAllUsersCommand;
import org.bigbluebutton.freeswitch.voice.freeswitch.actions.RecordConferenceCommand;
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.inbound.InboundConnectionFailure;
import org.freeswitch.esl.client.manager.ManagerConnection;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.freeswitch.esl.client.transport.message.EslHeaders.Name;
import org.freeswitch.esl.client.transport.message.EslHeaders.Value;

public class ConnectionManager  {

    private static final String EVENT_NAME = "Event-Name";
    
	private static final ScheduledExecutorService connExec = Executors.newSingleThreadScheduledExecutor();
	
    private final ManagerConnection manager;
    private ScheduledFuture<ConnectThread> connectTask;
    
    private volatile boolean subscribed = false;
    
    private final ConferenceEventListener conferenceEventListener;
    private final ESLEventListener eslEventListener;
    
    public ConnectionManager(ManagerConnection connManager, ESLEventListener eventListener, ConferenceEventListener confListener) {
    	this.manager = connManager;
    	this.eslEventListener = eventListener;
    	this.conferenceEventListener = confListener;
    }
    
    private void connect() {
    	try {
    		Client c = manager.getESLClient();
    		if (! c.canSend()) {
				System.out.println("Attempting to connect to FreeSWITCH ESL");
    			subscribed = false;
    			manager.connect();
    		} else {
	    		if (!subscribed) {
	    			System.out.println("Subscribing for ESL events.");
	                c.cancelEventSubscriptions();
	                c.addEventListener(eslEventListener);
	                c.setEventSubscriptions( "plain", "all" );
	                c.addEventFilter( EVENT_NAME, "heartbeat" );
	                c.addEventFilter( EVENT_NAME, "custom" );
	                c.addEventFilter( EVENT_NAME, "background_job" );
	                c.addEventFilter( EVENT_NAME, "channel_callstate" );
	                c.addEventFilter( EVENT_NAME, "channel_hangup_complete" );
	                subscribed = true;
	    		} 
	    	}    		
		} catch (InboundConnectionFailure e) {
			System.out.println("Failed to connect to ESL");
		}
    }
    
	public void start() {
		System.out.println("Starting FreeSWITCH ESL connection manager.");
		ConnectThread connector = new ConnectThread();
		connectTask = (ScheduledFuture<ConnectThread>) connExec.scheduleAtFixedRate(connector, 5, 5, TimeUnit.SECONDS);	
	}
	
	public void stop() {
		if (connectTask != null) {
			connectTask.cancel(true);
		}		
	}
	
	private class ConnectThread implements Runnable {
		public void run() {
			connect();
		}
	}
	
	
	public void broadcast(BroadcastConferenceCommand rcc) {
		Client c = manager.getESLClient();
		if (c.canSend()) {
	    	EslMessage response = c.sendSyncApiCommand(rcc.getCommand(), rcc.getCommandArgs());
	        rcc.handleResponse(response, conferenceEventListener); 	
		}
	}
	
	public void getUsers(GetAllUsersCommand prc) {
		Client c = manager.getESLClient();
		if (c.canSend()) {
	        EslMessage response = c.sendSyncApiCommand(prc.getCommand(), prc.getCommandArgs());
	        prc.handleResponse(response, conferenceEventListener); 			
		}
	}
	
	public void mute(MuteUserCommand mpc) {
		System.out.println("Got mute request from FSApplication.");
		Client c = manager.getESLClient();
		if (c.canSend()) {
			System.out.println("Issuing command to FS ESL.");
	        c.sendAsyncApiCommand( mpc.getCommand(), mpc.getCommandArgs());			
		}
	}

    private String createUuid() {
        System.out.println("Creating UUID");
        Client c = manager.getESLClient();
        if (c.canSend()) {
            EslMessage res = c.sendSyncApiCommand("create_uuid", null);

            if (res.getHeaderValue(Name.CONTENT_TYPE).equals(Value.API_RESPONSE)
                && !res.getBodyLines().isEmpty()) {
                    return res.getBodyLines().get(0);
            }
        }
        return null;
    }
	
    public void dial(DialCommand dc) {
        String uuid = createUuid();
        if (uuid == null) {
            System.out.println("UUID is null, aborting dial");
            return;
        }

        System.out.println("Sending async dial command with uuid" + uuid);

        dc.setOriginationUuid(uuid);

        DialReferenceValuePair value = new DialReferenceValuePair(dc.getRoom(),
            dc.getParticipant());

        eslEventListener.addDialReference(uuid, value);

        Client c = manager.getESLClient();
        if (c.canSend()) {
            String job = c.sendAsyncApiCommand(dc.getCommand(), dc.getCommandArgs());
            System.out.println("DialCommand job uuid: "+ job);
        }
    }

    public void cancelDial(CancelDialCommand cdc) {
        System.out.println("Sending async cancel dial command");

        Client c = manager.getESLClient();
        if (c.canSend()) {
            c.sendAsyncApiCommand(cdc.getCommand(), cdc.getCommandArgs());
        }
    }

    public void sendDtmf(SendDtmfCommand cdc) {
        System.out.println("Sending async send dtmf command");

        Client c = manager.getESLClient();
        if (c.canSend()) {
            c.sendAsyncApiCommand(cdc.getCommand(), cdc.getCommandArgs());
        }
    }

	public void eject(EjectUserCommand mpc) {
		Client c = manager.getESLClient();
		if (c.canSend()) {
			c.sendAsyncApiCommand( mpc.getCommand(), mpc.getCommandArgs());
		}       
	}
	
	public void ejectAll(EjectAllUsersCommand mpc) {
		Client c = manager.getESLClient();
		if (c.canSend()) {
	        c.sendAsyncApiCommand( mpc.getCommand(), mpc.getCommandArgs());	
		}
	}
	
	public void record(RecordConferenceCommand rcc) {
		Client c = manager.getESLClient();
		if (c.canSend()) {
	    	EslMessage response = c.sendSyncApiCommand(rcc.getCommand(), rcc.getCommandArgs());
	        rcc.handleResponse(response, conferenceEventListener); 			
		}
	}   
}