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
import org.apache.mina.core.buffer.IoBuffer;

public class SipToFlashStream {	
	
	public final String listenStreamName;
	public StreamObserver observer;
	public IoBuffer mBuffer;

	public SipToFlashStream() {		
		listenStreamName = "speaker_" + System.currentTimeMillis();	
		mBuffer = IoBuffer.allocate(1024);
		mBuffer = mBuffer.setAutoExpand(true);
	}		
	
	public String getStreamName() {
		return listenStreamName;
	}

	public void addListenStreamObserver(StreamObserver o) {
		observer = o;
	}


	public void stop() {}	

	public void start() {}
}
