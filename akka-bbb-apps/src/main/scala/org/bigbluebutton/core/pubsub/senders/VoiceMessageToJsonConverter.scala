package org.bigbluebutton.core.apps.voice.redis

import org.bigbluebutton.core.api._
import org.bigbluebutton.common.messages.MessagingConstants
import org.bigbluebutton.core.messaging.Util
import com.google.gson.Gson
import org.bigbluebutton.core.api.UserVO
import collection.JavaConverters._
import scala.collection.JavaConversions._

object VoiceMessageToJsonConverter {
  def sipVideoUpdatedToJson(msg: SipVideoUpdated): String = {
    val payload = new java.util.HashMap[String, Any]()
    payload.put(Constants.MEETING_ID, msg.meetingID)
    payload.put(Constants.VOICE_CONF, msg.voiceBridge)
    payload.put(Constants.IS_SIP_VIDEO_PRESENT, msg.isSipVideoPresent)
    payload.put(Constants.TALKER_USER_ID, msg.talkerUserId)

    val header = Util.buildHeader(MessageNames.SIP_VIDEO_UPDATE, None)
    Util.buildJson(header, payload)
  }

  def sipPhoneUpdatedToJson(msg: SipPhoneUpdated): String = {
    val payload = new java.util.HashMap[String, Any]()
    payload.put(Constants.VOICE_CONF, msg.voiceBridge)
    payload.put(Constants.IS_SIP_PHONE_PRESENT, msg.isSipPhonePresent)
    payload.put(Constants.MEETING_ID, msg.meetingID)
    val header = Util.buildHeader(MessageNames.SIP_PHONE_UPDATE, None)
    Util.buildJson(header, payload)
  }
}
