package org.bigbluebutton.modules.users.events
{
  import flash.events.Event;

  public class VideoModuleBridgeEvent extends Event
  {
    public static const VIDEO_MODULE_READY:String = "video module ready";
    public static const SET_GLOBAL_VIDEO_STREAM_NAME:String = "set global video stream name";

    public var globalVideoStreamName:String;

    public function VideoModuleBridgeEvent(type:String)
    {
      super(type, true, false);
    }
  }
}