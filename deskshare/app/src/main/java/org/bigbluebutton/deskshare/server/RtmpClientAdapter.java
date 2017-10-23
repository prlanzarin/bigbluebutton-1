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
package org.bigbluebutton.deskshare.server;

import java.util.HashMap;
import java.awt.Point;
import java.util.ArrayList;

import org.bigbluebutton.deskshare.server.recorder.RecordStatusListener;
import org.bigbluebutton.deskshare.server.recorder.event.AbstractDeskshareRecordEvent;
import org.bigbluebutton.deskshare.server.recorder.event.RecordErrorEvent;
import org.bigbluebutton.deskshare.server.recorder.event.RecordEvent;
import org.bigbluebutton.deskshare.server.recorder.event.RecordStartedEvent;
import org.bigbluebutton.deskshare.server.recorder.event.RecordStoppedEvent;
import org.bigbluebutton.deskshare.server.recorder.event.RecordUpdateEvent;
import org.red5.server.api.so.ISharedObject;
import org.bigbluebutton.common.messages.Constants;
import org.bigbluebutton.common.messages.MessagingConstants;
import org.bigbluebutton.common.messages.StartKurentoSendRtpRequestMessage;
import org.bigbluebutton.common.messages.StopKurentoSendRtpRequestMessage;
import org.bigbluebutton.common.messages.StopTranscoderRequestMessage;
import org.bigbluebutton.common.messages.SetMeetingDesksharePresentMessage;
import redis.clients.jedis.Jedis;

public class RtmpClientAdapter implements DeskshareClient, RecordStatusListener {

	private final ISharedObject so;
	private long lastUpdate = 0;
	private String redisHost;
	private int redisPort;
	private Jedis jedis;
	
	public RtmpClientAdapter(ISharedObject so) {
		this.so = so;
		redisHost = "127.0.0.1";
		redisPort = 6379;
		jedis = new Jedis(redisHost, redisPort);
	}
	
	public void sendDeskshareStreamStopped(String room) {
		ArrayList<Object> msg = new ArrayList<Object>();
		so.sendMessage("deskshareStreamStopped" , msg);

		HashMap<String,String> params = new HashMap<String,String>();
		params.put(Constants.INPUT, Constants.DESKSHARE);
		params.put(Constants.STREAM_TYPE, Constants.STREAM_TYPE_DESKSHARE);
		jedis.publish(MessagingConstants.TO_KURENTO_SYSTEM_CHAN, new StopKurentoSendRtpRequestMessage(room, params).toJson());

		jedis.publish(MessagingConstants.TO_BBB_TRANSCODE_SYSTEM_CHAN, new StopTranscoderRequestMessage(room, Constants.DESKSHARE).toJson());
		jedis.publish(MessagingConstants.TO_MEETING_CHANNEL, new SetMeetingDesksharePresentMessage(room, false).toJson());
	}
	
	public void sendDeskshareStreamStarted(String room, int width, int height) {
		ArrayList<Object> msg = new ArrayList<Object>();
		msg.add(new Integer(width));
		msg.add(new Integer(height));
		so.sendMessage("appletStarted" , msg);

		HashMap<String,String> params = new HashMap<String,String>();
		params.put(Constants.INPUT, Constants.DESKSHARE);
		params.put(Constants.STREAM_TYPE, Constants.STREAM_TYPE_DESKSHARE);

		jedis.publish(MessagingConstants.TO_KURENTO_SYSTEM_CHAN, new StartKurentoSendRtpRequestMessage(room, params).toJson());
		jedis.publish(MessagingConstants.TO_MEETING_CHANNEL, new SetMeetingDesksharePresentMessage(room, true).toJson());
	}
	
	public void sendMouseLocation(Point mouseLoc) {
		ArrayList<Object> msg = new ArrayList<Object>();
		msg.add(new Integer(mouseLoc.x));
		msg.add(new Integer(mouseLoc.y));
		so.sendMessage("mouseLocationCallback", msg);
	}

	@Override
	public void notify(RecordEvent event) {
		// TODO Auto-generated method stub
//		System.out.println("RtmpClientAdapter: TODO Notify client of recording status");
		ArrayList<Object> msg = new ArrayList<Object>();
		if (event instanceof RecordStoppedEvent) {
			msg.add(new String("DESKSHARE_RECORD_STOPPED_EVENT"));
		} else if (event instanceof RecordStartedEvent) {
			msg.add(new String("DESKSHARE_RECORD_STARTED_EVENT"));
		} else if (event instanceof RecordUpdateEvent) {
			long now = System.currentTimeMillis();
			// We send an update every 30sec that the screen is being recorded.
			if ((now - lastUpdate) > 30000) {
				msg.add(new String("DESKSHARE_RECORD_UPDATED_EVENT"));
				lastUpdate = now;
			}
		} else if (event instanceof RecordErrorEvent) {
			msg.add(new String("DESKSHARE_RECORD_ERROR_EVENT"));
		}
		
//		so.sendMessage("recordingStatusCallback", msg);	
	}	
	
}
