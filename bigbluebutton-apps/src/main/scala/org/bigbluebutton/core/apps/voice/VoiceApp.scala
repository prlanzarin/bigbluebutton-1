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

    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, globalVideoStreamName, talkerUserId,globalVideoStreamWidth,globalVideoStreamHeight))
  }

  def handleSipVideoResumed(msg: SipVideoResumed) {
    isSipVideoPresent = true
    logger.debug("Sip video RESUMED: Sending SipVideoUpdated event " +
                 "(isSipVideoPresent=" + isSipVideoPresent + ") " +
                 "(globalVideoStreamName=" + globalVideoStreamName + ")")

    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, globalVideoStreamName, talkerUserId,globalVideoStreamWidth,globalVideoStreamHeight))
  }

  def handleActiveTalkerChanged(msg: ActiveTalkerChanged) {
    talkerUserId = msg.talkerUserId
    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, globalVideoStreamName, talkerUserId,globalVideoStreamWidth,globalVideoStreamHeight))
  }

  def handleNewGlobalVideoStreamName(msg: NewGlobalVideoStreamName) {
    globalVideoStreamName = msg.globalVideoStreamName
    if (globalVideoDied())
        isSipVideoPresent=false
    logger.debug("New video stream name is set: Sending SipVideoUpdated event " +
                 "(isSipVideoPresent=" + isSipVideoPresent + ") " +
                 "(globalVideoStreamName=" + globalVideoStreamName + ")")

    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, globalVideoStreamName, talkerUserId,globalVideoStreamWidth,globalVideoStreamHeight))
  }

    def handleUpdateSipVideoStatus(msg: UpdateSipVideoStatus) {
    globalVideoStreamWidth = msg.width
    globalVideoStreamHeight = msg.height
    logger.debug("Global Video stream Status Updated: Sending SipVideoUpdated event " +
                 "(isSipVideoPresent="+isSipVideoPresent+") " +
                 "(globalVideoStreamName="+globalVideoStreamName+")"+
                 "(width:"+globalVideoStreamWidth+")"+
                 "(height:"+globalVideoStreamHeight+")")

    outGW.send(new SipVideoUpdated(meetingID, recorded, voiceBridge, isSipVideoPresent, globalVideoStreamName, talkerUserId,globalVideoStreamWidth,globalVideoStreamHeight))
  }

  private def globalVideoDied():Boolean = {
    globalVideoStreamName.equals("")
  }
}
