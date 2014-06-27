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
package org.bigbluebutton.voiceconf.red5.media;

import org.bigbluebutton.voiceconf.red5.media.transcoder.FlashToSipTranscoder;
import java.net.DatagramSocket;
import org.bigbluebutton.voiceconf.sip.SipConnectInfo;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;

public class FlashToSipVideoStream implements FlashToSipStream {

	//empty class for now...

	public FlashToSipVideoStream(FlashToSipTranscoder transcoder, DatagramSocket srcSocket, SipConnectInfo connInfo) {}

	@Override
	public void start(IBroadcastStream broadcastStream, IScope scope) {}

	@Override
	public void stop(IBroadcastStream broadcastStream, IScope scope) {}

	@Override
	public String getStreamName()
	{
		return null;
	}



}