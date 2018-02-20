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

package org.bigbluebutton.modules.screenshare.managers {
    import com.asfusion.mate.events.Dispatcher;
    import org.as3commons.logging.api.ILogger;
    import org.as3commons.logging.api.getClassLogger;
    import org.bigbluebutton.core.UsersUtil;
    import org.bigbluebutton.main.events.MadePresenterEvent;
    import org.bigbluebutton.modules.screenshare.events.IsSharingScreenEvent;
    import org.bigbluebutton.modules.screenshare.events.ShareEvent;
    import org.bigbluebutton.modules.screenshare.events.ShareStartRequestResponseEvent;
    import org.bigbluebutton.modules.screenshare.events.StartShareRequestFailedEvent;
    import org.bigbluebutton.modules.screenshare.events.StartShareRequestSuccessEvent;
    import org.bigbluebutton.modules.screenshare.events.ScreenShareClientPingMessage;
    import org.bigbluebutton.modules.screenshare.events.ShareStartedEvent;
    import org.bigbluebutton.modules.screenshare.events.ViewStreamEvent;
    import org.bigbluebutton.modules.screenshare.model.ScreenshareModel;
    import org.bigbluebutton.modules.screenshare.model.ScreenshareOptions;
    import org.bigbluebutton.modules.screenshare.services.ScreenshareService;
    import org.bigbluebutton.modules.screenshare.events.UseJavaModeCommand;
    import org.bigbluebutton.modules.screenshare.utils.BrowserCheck;

    public class ScreenshareManager {
        private static const LOGGER:ILogger = getClassLogger(ScreenshareManager);

        private var publishWindowManager:PublishWindowManager;
        private var viewWindowManager:ViewerWindowManager;
        private var module:ScreenshareModule;
        private var service:ScreenshareService;
        private var globalDispatcher:Dispatcher;
        private var _option:ScreenshareOptions = null;

        public function ScreenshareManager() {
            service = new ScreenshareService();
            globalDispatcher = new Dispatcher();
            publishWindowManager = new PublishWindowManager(service);
            viewWindowManager = new ViewerWindowManager(service);
        }

        public function get option():ScreenshareOptions {
            if (this._option == null) {
                this._option = new ScreenshareOptions();
                this._option.parseOptions();
            }
            return this._option;
        }

        public function handleStartModuleEvent(module:ScreenshareModule):void {
            LOGGER.debug("Screenshare Module starting");
            this.module = module;
            service.handleStartModuleEvent(module);
            ScreenshareModel.getInstance().sharing = false;
        }

        public function handleStopModuleEvent():void {
            LOGGER.debug("Screenshare Module stopping");
            publishWindowManager.stopSharing();
            viewWindowManager.stopViewing();
            service.disconnect();
        }

        public function handleConnectionSuccessEvent():void {
            LOGGER.debug("handle Connection Success Event");
            service.checkIfPresenterIsSharingScreen();
        }

        public function handleScreenShareStartedEvent(event:ShareStartedEvent):void {
            ScreenshareModel.getInstance().streamId = event.streamId;
            ScreenshareModel.getInstance().width = event.width;
            ScreenshareModel.getInstance().height = event.height;
            ScreenshareModel.getInstance().url = event.url;

            handleStreamStartEvent(ScreenshareModel.getInstance().streamId, event.width, event.height);

            var dispatcher:Dispatcher = new Dispatcher();
            dispatcher.dispatchEvent(new ViewStreamEvent(ViewStreamEvent.START));
        }

        public function handleIsSharingScreenEvent(event:IsSharingScreenEvent):void {
            ScreenshareModel.getInstance().streamId = event.streamId;
            ScreenshareModel.getInstance().width = event.width;
            ScreenshareModel.getInstance().height = event.height;
            ScreenshareModel.getInstance().url = event.url;
            ScreenshareModel.getInstance().session = event.session

            if (!UsersUtil.amIPresenter()) {
                handleStreamStartEvent(ScreenshareModel.getInstance().streamId, event.width, event.height);
            }

            var dispatcher:Dispatcher = new Dispatcher();
            dispatcher.dispatchEvent(new ViewStreamEvent(ViewStreamEvent.START));
        }

        private function handleStreamStartEvent(streamId:String, videoWidth:Number, videoHeight:Number):void {
            LOGGER.debug("Received start vieweing command");
            viewWindowManager.startViewing(streamId, videoWidth, videoHeight);
        }

        public function handleMadeViewerEvent(e:MadePresenterEvent):void {
            LOGGER.debug("Got MadeViewerEvent ");
            if (ScreenshareModel.getInstance().sharing) {
                service.requestStopSharing(ScreenshareModel.getInstance().streamId);
            }
            publishWindowManager.stopSharing();
        }

        public function handleRequestStartSharingEvent():void {
            publishWindowManager.startSharing(module.getCaptureServerUri(), module.getRoom(), module.tunnel());
            service.requestShareToken();
        }

        public function handleShareStartEvent():void {
            service.sharingStartMessage(ScreenshareModel.getInstance().session);
        }

        public function handleScreenShareClientPingMessage(event: ScreenShareClientPingMessage):void {
            service.sendClientPongMessage(event.session, event.timestamp);
        }

        public function handleRequestPauseSharingEvent():void {
            service.requestPauseSharing(ScreenshareModel.getInstance().streamId);
        }

        public function handleRequestRestartSharingEvent():void {
            service.requestRestartSharing();
        }

        public function handleRequestStopSharingEvent():void {
            service.requestStopSharing(ScreenshareModel.getInstance().streamId);
            publishWindowManager.handleShareWindowCloseEvent();
        }

        public function handleShareStartRequestResponseEvent(event:ShareStartRequestResponseEvent):void {
            var dispatcher:Dispatcher = new Dispatcher();
            if (event.success) {
                ScreenshareModel.getInstance().authToken = event.token;
                ScreenshareModel.getInstance().jnlp = event.jnlp;
                ScreenshareModel.getInstance().streamId = event.streamId;
                ScreenshareModel.getInstance().session = event.session;

                dispatcher.dispatchEvent(new StartShareRequestSuccessEvent(ScreenshareModel.getInstance().authToken));
            } else {
                dispatcher.dispatchEvent(new StartShareRequestFailedEvent());
            }
        }

        public function handleStartSharingEvent():void {
            publishWindowManager.startSharing(module.getCaptureServerUri(), module.getRoom(), module.tunnel());
        }

        public function handleShareScreenEvent(fullScreen:Boolean):void {
            publishWindowManager.handleShareScreenEvent(fullScreen);
        }

        public function handleStopSharingEvent():void {
            publishWindowManager.stopSharing();
        }

        public function handleRefreshScreenshareTab():void {
            handleStopSharingEvent();
        }

        public function handleShareWindowCloseEvent():void {
            publishWindowManager.handleShareWindowCloseEvent();
        }

        public function handleViewWindowCloseEvent():void {
            viewWindowManager.handleViewWindowCloseEvent();
        }

        public function handleStopViewStreamEvent():void {
            viewWindowManager.stopViewing();
            if (UsersUtil.amIPresenter()) {
                publishWindowManager.stopSharing();
            }
        }

        public function handleVideoDisplayModeEvent(actualSize:Boolean):void {
            viewWindowManager.handleVideoDisplayModeEvent(actualSize);
        }

        public function handleScreenshareStartedEvent(event:ShareEvent):void {
            service.sharingStarted(
                    event.payload['meetingId'],
                    event.payload['streamId'],
                    event.payload['width'],
                    event.payload['height']
            );
        }

        public function handleScreenshareStoppedEvent(event:ShareEvent):void {
            service.sharingStopped(event.payload['meetingId'], event.payload['streamId']);
        }
    }
}
