package org.bigbluebutton.core.apps.voice

import org.bigbluebutton.core.api._
import org.bigbluebutton.core.MeetingActor

trait VoiceApp {
  this : MeetingActor =>
  
  val outGW: MessageOutGateway

  def handleSipVideoPaused(msg: SipVideoPaused) {
    isSipVideoPresent = false
    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, globalVideoStreamName, talkerUserId))
  }

  def handleSipVideoResumed(msg: SipVideoResumed) {
    isSipVideoPresent = true
    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, globalVideoStreamName, talkerUserId))
  }

  def handleActiveTalkerChanged(msg: ActiveTalkerChanged) {
    talkerUserId = msg.talkerUserId
    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, globalVideoStreamName, talkerUserId))
  }

  def handleGlobalVideoStreamInfo(msg: GlobalVideoStreamInfo) {
    globalVideoStreamName = msg.globalVideoStreamName
    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, globalVideoStreamName, talkerUserId))
  }
}
