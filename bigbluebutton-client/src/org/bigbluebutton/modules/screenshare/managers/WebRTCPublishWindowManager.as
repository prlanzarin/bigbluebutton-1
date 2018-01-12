/**
* BigBlueButton open source conferencing system - http://www.bigbluebutton.org/
*
* Copyright (c) 2015 BigBlueButton Inc. and by respective authors (see below).
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

package org.bigbluebutton.modules.screenshare.managers
{
	import com.asfusion.mate.events.Dispatcher;

	import org.as3commons.logging.api.ILogger;
	import org.as3commons.logging.api.getClassLogger;

	import org.bigbluebutton.modules.screenshare.services.WebRTCDeskshareService;

	public class WebRTCPublishWindowManager {
		private static const LOGGER:ILogger = getClassLogger(PublishWindowManager);

		private var globalDispatcher:Dispatcher;
		private var service:WebRTCDeskshareService;

		public function WebRTCPublishWindowManager(service:WebRTCDeskshareService) {
			LOGGER.debug("PublishWindowManager init");
			globalDispatcher = new Dispatcher();
			this.service = service;
		}

		public function stopSharing():void {}

		private function autopublishTimerHandler(event:TimerEvent):void {}

		public function handleShareWindowCloseEvent():void {}

		public function startViewing(rtmp:String, videoWidth:Number, videoHeight:Number):void{}
	}
}
