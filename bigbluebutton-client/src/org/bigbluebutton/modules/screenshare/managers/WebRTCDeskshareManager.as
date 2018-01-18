/**
* BigBlueButton open source conferencing system - http://www.bigbluebutton.org/
*
* Copyright (c) 2018 BigBlueButton Inc. and by respective authors (see below).
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

	import flash.external.ExternalInterface;

	import org.as3commons.logging.api.ILogger;
	import org.as3commons.logging.api.getClassLogger;

	import org.bigbluebutton.core.UsersUtil;
	import org.bigbluebutton.core.managers.UserManager;
	import org.bigbluebutton.main.events.BBBEvent;
	import org.bigbluebutton.main.events.MadePresenterEvent;
	import org.bigbluebutton.modules.screenshare.events.ShareEvent;
	import org.bigbluebutton.modules.screenshare.events.ShareStartedEvent;
	import org.bigbluebutton.modules.screenshare.events.UseJavaModeCommand;
	import org.bigbluebutton.modules.screenshare.events.WebRTCWindowChangeState;
	import org.bigbluebutton.modules.screenshare.model.ScreenshareOptions;
	import org.bigbluebutton.modules.screenshare.model.ScreenshareModel;
	import org.bigbluebutton.modules.screenshare.utils.BrowserCheck;

	public class WebRTCDeskshareManager {
		private static const LOGGER:ILogger = getClassLogger(WebRTCDeskshareManager);

		private var globalDispatcher:Dispatcher;
		private var _options:ScreenshareOptions = null;
		private var _chromeExtensionKey:String = null;

		public function WebRTCDeskshareManager() {
			globalDispatcher = new Dispatcher();
		}

		public function get options():ScreenshareOptions {
			if (this._options == null) {
				this._options = new ScreenshareOptions();
				this._options.parseOptions();
			}
			return this._options;
		}

		public function get chromeExtensionKey():String {
			if (this._chromeExtensionKey == null) {
				this._chromeExtensionKey = options.chromeExtensionKey;
			}
			return this._chromeExtensionKey;
		}

		public function handleRequestStopSharingEvent():void {
			LOGGER.debug("handleRequestStopSharingEvent");
			stopWebRTCDeskshare();
		}

		public function handleRequestStartSharingEvent():void {
			LOGGER.debug("handleRequestStartSharingEvent");
			canIUseWebRTCOnThisBrowser();
		}

		private function stopWebRTCDeskshare():void {
			LOGGER.debug("stopWebRTCDeskshare");
			if (ScreenshareModel.getInstance().usingWebRTCDeskshare && ExternalInterface.available) {
				ExternalInterface.call("kurentoExitScreenShare");
			}
		}

		private function startWebRTCDeskshare():void {
			LOGGER.debug("startWebRTCDeskshare");
			if (ScreenshareModel.getInstance().usingWebRTCDeskshare && ExternalInterface.available) {
				var videoTag:String = "localVertoVideo";
				var onFail:Function = function(args:Object):void {
					LOGGER.debug("Falling back to Java");
					globalDispatcher.dispatchEvent(new UseJavaModeCommand())
				};
				ExternalInterface.addCallback("onFail", onFail);

				var voiceBridge:String = UserManager.getInstance().getConference().voiceBridge;
				var myName:String = UserManager.getInstance().getConference().getMyName();
				var internalMeetingID:String = UserManager.getInstance().getConference().internalMeetingID;
				var streamId:String = ScreenshareModel.getInstance().streamId;

				ExternalInterface.call(
						'kurentoShareScreen',
						videoTag,
						voiceBridge,
						myName,
						internalMeetingID,
						"onFail",
						chromeExtensionKey,
						streamId
				);
			}
		}

		public function handleMadeViewerEvent(e:MadePresenterEvent):void{
			LOGGER.debug("handleMadeViewerEvent");
			if (ScreenshareModel.getInstance().sharing) {
				stopWebRTCDeskshare();
			}
		}

		private function canIUseWebRTCOnThisBrowser(newOnWebRTCBrokeFailure:Function = null, newOnNoWebRTCFailure:Function = null, newOnSuccess:Function = null):void {
			LOGGER.debug("canIUseWebRTCOnThisBrowser");
			var onNoWebRTCFailure:Function, onWebRTCBrokeFailure:Function, onSuccess:Function;

			onNoWebRTCFailure = (newOnNoWebRTCFailure != null) ? newOnNoWebRTCFailure : function(message:String):void {
				// send out event to fallback to Java
				LOGGER.debug("Falling back to Java");
				globalDispatcher.dispatchEvent(new UseJavaModeCommand());
				return;
			};

			onWebRTCBrokeFailure = (newOnWebRTCBrokeFailure != null) ? newOnWebRTCBrokeFailure : function(message:String):void {
				globalDispatcher.dispatchEvent(new WebRTCWindowChangeState(WebRTCWindowChangeState.DISPLAY_INSTALL));
			};

			onSuccess = (newOnSuccess != null) ? newOnSuccess : function(message:String):void {
				LOGGER.debug("onSuccess");
				startWebRTCDeskshare();
			};

			if (options.tryWebRTCFirst && BrowserCheck.isWebRTCSupported()) {
				LOGGER.debug("WebRTC Supported");
				if (BrowserCheck.isFirefox()) {
					onSuccess("Firefox, lets try");
				} else {
					if (chromeExtensionKey != null) {

						LOGGER.debug("Chrome extension link exists");
						if (ExternalInterface.available) {

							var success2:Function = function(exists:Boolean):void {
								ExternalInterface.addCallback("success2", null);
								LOGGER.debug("onSuccess2");
								if (exists) {
									LOGGER.debug("Chrome Extension exists");
									onSuccess("worked");
								} else {
									onWebRTCBrokeFailure("No Chrome Extension");
									LOGGER.debug("no chrome extension");
								}
							};
							ExternalInterface.addCallback("success2", success2);
							ExternalInterface.call("checkChromeExtInstalled", "success2", chromeExtensionKey);
						}
					} else {
						onNoWebRTCFailure("No chromeExtensionKey in config.xml");
						return;
					}
				}
			} else {
				onNoWebRTCFailure("Web browser doesn't support WebRTC");
				return;
			}
		}

		public function handleUseJavaModeCommand():void {
			if (ScreenshareModel.getInstance().sharing) {
				stopWebRTCDeskshare();
			}
			ScreenshareModel.getInstance().usingWebRTCDeskshare = false;
		}

		public function handleWebRTCScreenshareStartedEvent(event:BBBEvent):void {
			ScreenshareModel.getInstance().sharing = true;
			var e:ShareEvent = new ShareEvent(ShareEvent.SCREENSHARE_STARTED_EVENT);
			e.payload.meetingId = event.payload['meetingId'];
			e.payload.streamId = event.payload['streamId'];
			e.payload.width = event.payload['width'];
			e.payload.height = event.payload['height'];
			globalDispatcher.dispatchEvent(e);
		}

		public function handleWebRTCScreenshareStoppedEvent(event:BBBEvent):void {
			var e:ShareEvent = new ShareEvent(ShareEvent.SCREENSHARE_STOPPED_EVENT);
			e.payload.meetingId = event.payload['meetingId'];
			e.payload.streamId = event.payload['streamId'];
			globalDispatcher.dispatchEvent(e);
			ScreenshareModel.getInstance().sharing = false;
		}
	}
}
