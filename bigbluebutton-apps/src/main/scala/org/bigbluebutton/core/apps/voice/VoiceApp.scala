package org.bigbluebutton.core.apps.voice

import org.bigbluebutton.core.api._
import org.bigbluebutton.core.MeetingActor

trait VoiceApp {
  this : MeetingActor =>
  
  val outGW: MessageOutGateway

  def handleSipVideoPaused(msg: SipVideoPaused) {
    isSipVideoPresent = false
    logger.debug("Sip video PAUSED: Sending SipVideoUpdated event " +
                 "(isSipVideoPresent=" + isSipVideoPresent + ") " +
                 "(globalVideoStreamName=" + globalVideoStreamName + ")")

    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, globalVideoStreamName, talkerUserId))
  }

  def handleSipVideoResumed(msg: SipVideoResumed) {
    isSipVideoPresent = true
    logger.debug("Sip video RESUMED: Sending SipVideoUpdated event " +
                 "(isSipVideoPresent=" + isSipVideoPresent + ") " +
                 "(globalVideoStreamName=" + globalVideoStreamName + ")")

    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, globalVideoStreamName, talkerUserId))
  }

  def handleActiveTalkerChanged(msg: ActiveTalkerChanged) {
    talkerUserId = msg.talkerUserId
    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, globalVideoStreamName, talkerUserId))
  }

  def handleNewGlobalVideoStreamName(msg: NewGlobalVideoStreamName) {
    globalVideoStreamName = msg.globalVideoStreamName
    logger.debug("New video stream name is set: Sending SipVideoUpdated event " +
                 "(isSipVideoPresent=" + isSipVideoPresent + ") " +
                 "(globalVideoStreamName=" + globalVideoStreamName + ")")

    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, globalVideoStreamName, talkerUserId))
  }
}
