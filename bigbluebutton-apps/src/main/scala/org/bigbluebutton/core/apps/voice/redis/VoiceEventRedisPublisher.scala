package org.bigbluebutton.core.apps.voice.redis

import org.bigbluebutton.conference.service.messaging.redis.MessageSender
import org.bigbluebutton.core.api._
import org.bigbluebutton.conference.service.messaging.MessagingConstants
import org.bigbluebutton.core.apps.users.redis.UsersMessageToJsonConverter
import com.google.gson.Gson

class VoiceEventRedisPublisher(service: MessageSender) extends OutMessageListener2 {

  def handleMessage(msg: IOutMessage) {
    msg match {
      case msg: SipVideoUpdated               => handleSipVideoUpdated(msg)
      case msg: UserSharedWebcam              => handleUserSharedWebcam(msg)
      case msg: UserUnsharedWebcam            => handleUserUnsharedWebcam(msg)
      case msg: SipPhoneUpdated            => handleSipPhoneUpdated(msg)
      case _ => // Do nothing
    }
  }

  private def handleSipVideoUpdated(msg: SipVideoUpdated) {
    val json = VoiceMessageToJsonConverter.sipVideoUpdatedToJson(msg)
    service.send(MessagingConstants.TO_BBB_VOICE_CHANNEL, json)
    service.send(MessagingConstants.FROM_MEETING_CHANNEL, json)
  }

    private def handleUserSharedWebcam(msg: UserSharedWebcam) {
        val json = UsersMessageToJsonConverter.userSharedWebcamToJson(msg)
        service.send(MessagingConstants.TO_BBB_VOICE_CHANNEL, json)
        service.send(MessagingConstants.FROM_MEETING_CHANNEL, json)
    }

    private def handleUserUnsharedWebcam(msg: UserUnsharedWebcam) {
        val json = UsersMessageToJsonConverter.userUnsharedWebcamToJson(msg)
        service.send(MessagingConstants.TO_BBB_VOICE_CHANNEL, json)
        service.send(MessagingConstants.FROM_MEETING_CHANNEL, json)
    }

    private def handleSipPhoneUpdated(msg: SipPhoneUpdated) {
        val json = VoiceMessageToJsonConverter.sipPhoneUpdatedToJson(msg)
        service.send(MessagingConstants.TO_BBB_VOICE_CHANNEL, json)
        service.send(MessagingConstants.FROM_MEETING_CHANNEL, json)
    }
}
