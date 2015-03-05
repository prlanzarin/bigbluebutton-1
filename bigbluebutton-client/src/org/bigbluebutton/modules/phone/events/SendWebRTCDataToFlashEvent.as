package org.bigbluebutton.modules.phone.events
{
import flash.events.Event;

    public class SendWebRTCDataToFlashEvent extends Event
    {
        public var remoteVideoPort:String;
        public var localVideoPort:String;

        public static const ON_WEBRTC_CALL_ACCEPTED:String = "webrtc call accepted in video's context";    

        public function SendWebRTCDataToFlashEvent(type:String)
        {
            super(type, true, false);
        }
    }
}
