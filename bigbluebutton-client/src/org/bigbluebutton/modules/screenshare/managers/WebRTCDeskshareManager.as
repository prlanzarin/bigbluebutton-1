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

	import org.bigbluebutton.core.managers.UserManager;
	import org.bigbluebutton.main.events.BBBEvent;
	import org.bigbluebutton.main.events.MadePresenterEvent;
	import org.bigbluebutton.modules.screenshare.events.ShareEvent;
	import org.bigbluebutton.modules.screenshare.events.UseJavaModeCommand;
	import org.bigbluebutton.modules.screenshare.events.WebRTCWindowChangeState;
	import org.bigbluebutton.modules.screenshare.model.ScreenshareOptions;
	import org.bigbluebutton.modules.screenshare.model.ScreenshareModel;
	import org.bigbluebutton.modules.screenshare.utils.BrowserCheck;

	public class WebRTCDeskshareManager {
		private static const LOGGER:ILogger = getClassLogger(WebRTCDeskshareManager);

		private static const BROWSER_ERROR:String = "BROWSER_ERROR";
		private static const WEBRTC_ERROR:String = "WEBRTC_ERROR";
		private static const CONNECTION_ERROR:String = "CONNECTION_ERROR";
		private static const SERVER_ERROR:String = "SERVER_ERROR";
		private static const PEER_ERROR:String = "PEER_ERROR";
		private static const SDP_ERROR:String = "SDP_ERROR";
		private static const EXTENSION_ERROR:String = "EXTENSION_ERROR";
		private static const MEDIA_ERROR:String = "MEDIA_ERROR";

		private var globalDispatcher:Dispatcher;
		private var _options:ScreenshareOptions = null;
		private var _chromeExtensionKey:String = null;

		public function WebRTCDeskshareManager() {
			globalDispatcher = new Dispatcher();
			ExternalInterface.addCallback("onWebRTCScreenshareFail", onWebRTCScreenshareFail);
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

		private function onWebRTCScreenshareFail(error:String):void {
			LOGGER.debug(error);
			switch (error) {
				case EXTENSION_ERROR:
				case BROWSER_ERROR:
				case WEBRTC_ERROR:
				case SERVER_ERROR:
					globalDispatcher.dispatchEvent(new UseJavaModeCommand());
					break;
				case PEER_ERROR:
				case CONNECTION_ERROR:
				case SDP_ERROR:
				case MEDIA_ERROR:
					globalDispatcher.dispatchEvent(new WebRTCWindowChangeState(WebRTCWindowChangeState.DISPLAY_ERROR));
					break;
				default:
					globalDispatcher.dispatchEvent(new UseJavaModeCommand());
			}
		}

		private function onWebRTCScreenshareSuccess(message:String):void {
			LOGGER.debug(message);
			startWebRTCDeskshare();
		}

		private function onWebRTCScreenshareChromeExtensionMissing():void {
			globalDispatcher.dispatchEvent(new WebRTCWindowChangeState(WebRTCWindowChangeState.DISPLAY_INSTALL));
		}

		private function startWebRTCDeskshare():void {
			LOGGER.debug("startWebRTCDeskshare");
			if (ScreenshareModel.getInstance().usingWebRTCDeskshare && ExternalInterface.available) {
				var videoTag:String = "localVertoVideo";

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
						"onWebRTCScreenshareFail",
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

		private function firefoxWebRTCScreenshare():void {
			onWebRTCScreenshareSuccess("firefoxWebRTCScreenshare");
		}

		private function chromeWebRTCScreenshare():void {
			if (chromeExtensionKey != null) {
				LOGGER.debug("Chrome extension link exists");
				if (ExternalInterface.available) {
					var isChromeWebRTCScreenshareExtensionInstalled:Function = function(installed:Boolean):void {
						LOGGER.debug("isChromeWebRTCScreenshareExtensionInstalled");
						if (installed) {
							onWebRTCScreenshareSuccess("chromeWebRTCScreenshare");
						} else {
							onWebRTCScreenshareChromeExtensionMissing();
						}
					};
					ExternalInterface.addCallback(
							"isChromeWebRTCScreenshareExtensionInstalled",
							isChromeWebRTCScreenshareExtensionInstalled
					);
					ExternalInterface.call(
							"checkChromeExtInstalled",
							"isChromeWebRTCScreenshareExtensionInstalled",
							chromeExtensionKey
					);
				}
			} else {
				onWebRTCScreenshareFail(EXTENSION_ERROR);
			}
		}

		private function canIUseWebRTCOnThisBrowser():void {
			LOGGER.debug("canIUseWebRTCOnThisBrowser");
			if (!options.tryWebRTCFirst) {
				LOGGER.debug("If this is being printed something is wrong. Review this!");
				return;
			}

			if (BrowserCheck.isWebRTCSupported()) {
				LOGGER.debug("WebRTC supported");
				globalDispatcher.dispatchEvent(new WebRTCWindowChangeState(WebRTCWindowChangeState.DISPLAY_CONNECTING));
				if (BrowserCheck.isFirefox()) {
					firefoxWebRTCScreenshare();
				} else if (BrowserCheck.isChrome()) {
					chromeWebRTCScreenshare();
				} else {
					onWebRTCScreenshareFail(BROWSER_ERROR);
				}
			} else {
				onWebRTCScreenshareFail(WEBRTC_ERROR);
			}
		}

		public function handleUseJavaModeCommand():void {
			if (ScreenshareModel.getInstance().sharing) {
				stopWebRTCDeskshare();
			}
			if (options.enableJava) {
				ScreenshareModel.getInstance().usingWebRTCDeskshare = false;
			}
		}

		public function handleWebRTCScreenshareStartedEvent(event:BBBEvent):void {
			ScreenshareModel.getInstance().sharing = true;
			globalDispatcher.dispatchEvent(new WebRTCWindowChangeState(WebRTCWindowChangeState.DISPLAY_WAITING_MEDIA));
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
