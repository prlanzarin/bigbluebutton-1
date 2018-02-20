package org.bigbluebutton.modules.phone.events
{
  import flash.events.Event;
  
  public class UseFlashModeCommand extends Event
  {
    public static const USE_FLASH_MODE:String = "use flash to join voice event";
    public static const USE_FLASH_LISTEN_ONLY:String = "use flash to join listen only event";
    
    public function UseFlashModeCommand(type:String, errorCode:Number=0, cause:String=null, bubbles:Boolean=true, cancelable:Boolean=false)
    {
      super(type, bubbles, cancelable);
    }
  }
}