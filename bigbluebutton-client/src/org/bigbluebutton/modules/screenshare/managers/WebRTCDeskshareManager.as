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
	import org.bigbluebutton.main.events.MadePresenterEvent;
	import org.bigbluebutton.modules.screenshare.events.ShareStartedEvent;
	import org.bigbluebutton.modules.screenshare.events.UseJavaModeCommand;
	import org.bigbluebutton.modules.screenshare.events.WebRTCViewStreamEvent;
	import org.bigbluebutton.modules.screenshare.events.WebRTCPublishWindowChangeState;
	import org.bigbluebutton.modules.screenshare.model.ScreenshareOptions;
	import org.bigbluebutton.modules.screenshare.services.WebRTCDeskshareService;
	import org.bigbluebutton.modules.screenshare.utils.BrowserCheck;

	public class WebRTCDeskshareManager {
		private static const LOGGER:ILogger = getClassLogger(WebRTCDeskshareManager);

		private var viewWindowManager:WebRTCViewerWindowManager;
		private var module:ScreenshareModule;
		private var service:WebRTCDeskshareService;
		private var globalDispatcher:Dispatcher;
		private var sharing:Boolean = false;
		private var usingWebRTC:Boolean = false;
		private var chromeExtensionKey:String = null;
		private var _options:ScreenshareOptions = null;

		public function WebRTCDeskshareManager() {
			LOGGER.debug("WebRTCDeskshareManager::WebRTCDeskshareManager");
			service = new WebRTCDeskshareService();
			globalDispatcher = new Dispatcher();
			viewWindowManager = new WebRTCViewerWindowManager();
		}

		public function get options():ScreenshareOptions {
			if (this._options == null) {
				this._options = new ScreenshareOptions();
				this._options.parseOptions();
			}
			return this._options;
		}

		public function handleStartModuleEvent(module:ScreenshareModule):void {
			LOGGER.debug("WebRTC Screenshare Module starting");
			this.module = module;
			service.handleStartModuleEvent(module);

			if (UsersUtil.amIPresenter()) {
				initDeskshare();
			}
		}

		public function handleStopModuleEvent():void {
			LOGGER.debug("WebRTC Deskshare Module stopping");

			viewWindowManager.stopViewing();
			service.disconnect();
		}

		/*presenter stopped their program stream*/
		public function handleStreamStoppedEvent():void {
			LOGGER.debug("WebRTCDeskshareManager::handleStreamStoppedEvent Sending deskshare stopped command");
			stopWebRTCDeskshare();
		}

		/*viewer being told there is no more stream*/
		public function handleStreamStopEvent(args:Object):void {
			LOGGER.debug("WebRTCDeskshareManager::handleStreamStopEvent");
			sharing = false;
			viewWindowManager.handleViewWindowCloseEvent();
		}

		public function handleRequestStopSharingEvent():void {
			stopWebRTCDeskshare();
		}

		private function stopWebRTCDeskshare():void {
			LOGGER.debug("WebRTCDeskshareManager::stopWebRTCDeskshare");
			viewWindowManager.stopViewing();

			if (ExternalInterface.available) {
				ExternalInterface.call("kurentoExitScreenShare");
			}
		}

		private function startWebRTCDeskshare():void {
			LOGGER.debug("WebRTCDeskshareManager::startWebRTCDeskshare");

			if (ExternalInterface.available) {
				var videoTag:String = "localVertoVideo";
				var onFail:Function = function(args:Object):void {
					LOGGER.debug("WebRTCDeskshareManager::startWebRTCDeskshare - falling back to java");
					globalDispatcher.dispatchEvent(new UseJavaModeCommand())
				};
				ExternalInterface.addCallback("onFail", onFail);

				var voiceBridge:String = UserManager.getInstance().getConference().voiceBridge;
				var myName:String = UserManager.getInstance().getConference().getMyName();
				var internalMeetingID:String = UserManager.getInstance().getConference().internalMeetingID;

				ExternalInterface.call(
						'kurentoShareScreen',
						videoTag,
						voiceBridge,
						myName,
						internalMeetingID,
						"onFail",
						chromeExtensionKey
				);
			}
		}

		private function initDeskshare():void {
			LOGGER.debug("WebRTCDeskshareManager::initDeskshare");
			sharing = false;
			if (options.chromeExtensionKey) {
				chromeExtensionKey = options.chromeExtensionKey;
			}
			usingWebRTC = options.tryWebRTCFirst;
		}

		public function handleMadePresenterEvent(e:MadePresenterEvent):void {
			LOGGER.debug("Got MadePresenterEvent ");
			initDeskshare();
		}

		public function handleMadeViewerEvent(e:MadePresenterEvent):void{
			LOGGER.debug("Got MadeViewerEvent ");
			if (sharing) {
				stopWebRTCDeskshare();
			}
			sharing = false;
		}

		private function canIUseWebRTCOnThisBrowser(newOnWebRTCBrokeFailure:Function = null, newOnNoWebRTCFailure:Function = null, newOnSuccess:Function = null):void {
			LOGGER.debug("DeskshareManager::canIUseWebRTCOnThisBrowser");
			var onNoWebRTCFailure:Function, onWebRTCBrokeFailure:Function, onSuccess:Function;

			onNoWebRTCFailure = (newOnNoWebRTCFailure != null) ? newOnNoWebRTCFailure : function(message:String):void {
				usingWebRTC = false;
				// send out event to fallback to Java
				LOGGER.debug("WebRTCDeskshareManager::handleStartSharingEvent - falling back to java");
				globalDispatcher.dispatchEvent(new UseJavaModeCommand());
				return;
			};

			onWebRTCBrokeFailure = (newOnWebRTCBrokeFailure != null) ? newOnWebRTCBrokeFailure : function(message:String):void {
				globalDispatcher.dispatchEvent(new WebRTCPublishWindowChangeState(WebRTCPublishWindowChangeState.DISPLAY_INSTALL));
			};

			onSuccess = (newOnSuccess != null) ? newOnSuccess : function(message:String):void {
				LOGGER.debug("WebRTCDeskshareManager::handleStartSharingEvent onSuccess");
				usingWebRTC = true;
				startWebRTCDeskshare();
			};

			if (options.tryWebRTCFirst && BrowserCheck.isWebRTCSupported()) {
				LOGGER.debug("WebRTCDeskshareManager::handleStartSharingEvent WebRTC Supported");
				if (BrowserCheck.isFirefox()) {
					onSuccess("Firefox, lets try");
				} else {
					if (chromeExtensionKey != null) {

						LOGGER.debug("WebRTCDeskshareManager::handleStartSharingEvent chrome extension link exists - ");
						if (ExternalInterface.available) {

							var success2:Function = function(exists:Boolean):void {
								ExternalInterface.addCallback("success2", null);
								LOGGER.debug("WebRTCDeskshareManager::handleStartSharingEvent inside onSuccess2");
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

		/*handle start sharing event*/
		public function handleStartSharingEvent():void {
			LOGGER.debug("WebRTCDeskshareManager::handleStartSharingEvent");
			canIUseWebRTCOnThisBrowser();
		}

		public function handleShareWindowCloseEvent():void {
			sharing = false;
			stopWebRTCDeskshare();
		}

		public function handleViewWindowCloseEvent():void {
			LOGGER.debug("Received stop viewing command");
			sharing = false;
			viewWindowManager.handleViewWindowCloseEvent();
		}

		public function handleStreamStartEvent(e:WebRTCViewStreamEvent):void{
			if (sharing) return; //TODO must uncomment this for the non-webrtc desktop share
			LOGGER.debug("Received start viewing command");

			if (!options.tryWebRTCFirst || e == null || e.rtmp == null) {
				return;
			}

			viewWindowManager.startViewing(e.rtmp, e.videoWidth, e.videoHeight);

			 sharing = true; //TODO must uncomment this for the non-webrtc desktop share
		}

		public function handleUseJavaModeCommand():void {
			LOGGER.debug("WebRTCDeskshareManager::handleUseJavaModeCommand");
			usingWebRTC = false;
		}

		public function handleRequestStartSharingEvent():void {
			LOGGER.debug("WebRTCDeskshareManager::handleRequestStartSharingEvent");
			initDeskshare();
			handleStartSharingEvent();
		}

		public function handleScreenShareStartedEvent(event:ShareStartedEvent):void {
			var dispatcher:Dispatcher = new Dispatcher();
			dispatcher.dispatchEvent(new WebRTCViewStreamEvent(WebRTCViewStreamEvent.START));
		}
		
		public function handleIsSharingScreenEvent():void {
			var dispatcher:Dispatcher = new Dispatcher();
			dispatcher.dispatchEvent(new WebRTCViewStreamEvent(WebRTCViewStreamEvent.START));
		}

		public function handleVideoDisplayModeEvent(actualSize:Boolean):void {
			viewWindowManager.handleVideoDisplayModeEvent(actualSize);
		}
	}
}
