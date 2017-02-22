package org.bigbluebutton.modules.deskshare.services

{
  import com.asfusion.mate.events.Dispatcher;

  import org.as3commons.logging.api.ILogger;
  import org.as3commons.logging.api.getClassLogger;
  import org.bigbluebutton.core.BBB;
  import org.bigbluebutton.main.model.users.IMessageListener;
  import org.bigbluebutton.modules.deskshare.events.ViewStreamEvent;

  public class MessageReceiver implements IMessageListener {

    private static const LOGGER:ILogger = getClassLogger(MessageReceiver);

    private var dispatcher:Dispatcher;

    public function MessageReceiver() {
      this.dispatcher = new Dispatcher();
      BBB.initConnectionManager().addMessageListener(this);
    }

    public function onMessage(messageName:String, message:Object):void {
      switch (messageName) {
        case "startDeskshareViewing":
          handleStartDeskshareViewing(message);
          break;
        default:
      }
    }

    private function handleStartDeskshareViewing(message:Object):void {
      LOGGER.debug("Handling start deskshare viewing message [{0}]", [message.msg]);

      var event:ViewStreamEvent = new ViewStreamEvent(ViewStreamEvent.START);
      event.videoWidth = message.videoWidth;
      event.videoHeight = message.videoHeight;
      dispatcher.dispatchEvent(event);
    }
  }
}
