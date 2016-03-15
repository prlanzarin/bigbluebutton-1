package org.bigbluebutton.core.apps

import org.bigbluebutton.core.api._
import org.bigbluebutton.core.MeetingActor
import org.bigbluebutton.core.OutMessageGateway

trait VoiceApp {
  this: MeetingActor =>

  val outGW: OutMessageGateway

  def handleNewGlobalVideoStreamName(msg: NewGlobalVideoStreamName) {
    meetingModel.setGlobalVideoStreamName(msg.globalVideoStreamName)
    log.debug("New video stream name is set: Sending SipVideoUpdated event " +
      "(isSipVideoPresent=" + meetingModel.isSipVideoPresent() + ") " +
      "(globalVideoStreamName=" + meetingModel.globalVideoStreamName() + ")")

    outGW.send(new SipVideoUpdated(mProps.meetingID, mProps.recorded, mProps.voiceBridge, meetingModel.isSipVideoPresent(), meetingModel.globalVideoStreamName(), meetingModel.talkerUserId(), meetingModel.globalVideoStreamWidth(), meetingModel.globalVideoStreamHeight()))
  }

  def handleUpdateSipVideoStatus(msg: UpdateSipVideoStatus) {
    meetingModel.setGlobalVideoStreamWidth(msg.width)
    meetingModel.setGlobalVideoStreamHeight(msg.height)
    log.debug("Global Video stream Status Updated: Sending SipVideoUpdated event " +
      "(isSipVideoPresent=" + meetingModel.isSipVideoPresent() + ") " +
      "(globalVideoStreamName=" + meetingModel.globalVideoStreamName() + ")" +
      "(width:" + meetingModel.globalVideoStreamWidth() + ")" +
      "(height:" + meetingModel.globalVideoStreamHeight() + ")")

    outGW.send(new SipVideoUpdated(mProps.meetingID, mProps.recorded, mProps.voiceBridge, meetingModel.isSipVideoPresent(), meetingModel.globalVideoStreamName(), meetingModel.talkerUserId(), meetingModel.globalVideoStreamWidth(), meetingModel.globalVideoStreamHeight()))
  }

  def handleRequestUpdateVideoStatus(msg: RequestUpdateVideoStatus) {
    log.debug("SipVideoUpdate requested. Sending it " +
      "(globalVideoStreamName=" + meetingModel.globalVideoStreamName() + ")" +
      "(currentFloorHolder=" + meetingModel.talkerUserId() + ")" +
      "(isSipVideoPresent=" + meetingModel.isSipVideoPresent() + ")")

    outGW.send(new SipVideoUpdated(mProps.meetingID, mProps.recorded, mProps.voiceBridge, meetingModel.isSipVideoPresent(), meetingModel.globalVideoStreamName(), meetingModel.talkerUserId(), meetingModel.globalVideoStreamWidth(), meetingModel.globalVideoStreamHeight()))
  }

  def handleVoiceOutboundDialRequest(msg: VoiceOutboundDialRequest) {
    log.debug("Outbound dial from  user [{}] ", msg.requesterID);
    outGW.send(new VoiceOutboundDial(msg.meetingID, mProps.recorded, mProps.voiceBridge, msg.requesterID, msg.options, msg.params));
  }

  def handleVoiceCancelDialRequest(msg: VoiceCancelDialRequest) {
    log.debug("Cancel outbound dial request from  user [{}] to uuid [{}] ", msg.requesterID, msg.uuid);
    outGW.send(new VoiceCancelDial(msg.meetingID, mProps.recorded, msg.uuid));
  }

  def handleVoiceSendDtmfRequest(msg: VoiceSendDtmfRequest) {
    log.info("sendDtmf: mid=[" + msg.meetingID + "] uuid=[" + msg.uuid + "] dtmfDigit=[" + msg.dtmfDigit + "]");
    outGW.send(new VoiceSendDtmf(msg.meetingID, mProps.recorded, msg.uuid, msg.dtmfDigit));
  }

  def handleSipVideoPaused(msg: SipVideoPaused) {
    meetingModel.setSipVideoPresent(false)
    log.debug("Sip video PAUSED: Sending SipVideoUpdated event " +
      "(isSipVideoPresent=" + meetingModel.isSipVideoPresent() + ") " +
      "(globalVideoStreamName=" + meetingModel.globalVideoStreamName() + ")")

    outGW.send(new SipVideoUpdated(mProps.meetingID, mProps.recorded, mProps.voiceBridge, meetingModel.isSipVideoPresent(), meetingModel.globalVideoStreamName(), meetingModel.talkerUserId(), meetingModel.globalVideoStreamWidth(), meetingModel.globalVideoStreamHeight()))
  }

  def handleSipVideoResumed(msg: SipVideoResumed) {
    meetingModel.setSipVideoPresent(true)
    log.debug("Sip video RESUMED: Sending SipVideoUpdated event " +
      "(isSipVideoPresent=" + meetingModel.isSipVideoPresent() + ") " +
      "(globalVideoStreamName=" + meetingModel.globalVideoStreamName() + ")")

    outGW.send(new SipVideoUpdated(mProps.meetingID, mProps.recorded, mProps.voiceBridge, meetingModel.isSipVideoPresent(), meetingModel.globalVideoStreamName(), meetingModel.talkerUserId(), meetingModel.globalVideoStreamWidth(), meetingModel.globalVideoStreamHeight()))
  }

  def handleActiveTalkerChanged(msg: ActiveTalkerChanged) {
    usersModel.getUserWithVoiceUserId(msg.voiceUserId) foreach { user =>
      meetingModel.setTalkerUserId(user.userID)
      log.debug("Active Talker Changed: talkerUserId={" + meetingModel.talkerUserId() + "}")

      outGW.send(new SipVideoUpdated(mProps.meetingID, mProps.recorded, mProps.voiceBridge, meetingModel.isSipVideoPresent(), meetingModel.globalVideoStreamName(), meetingModel.talkerUserId(), meetingModel.globalVideoStreamWidth(), meetingModel.globalVideoStreamHeight()))
    }
  }

  def handleVoiceDialing(msg: VoiceDialing) {
    outGW.send(new VoiceDialing2(mProps.meetingID, mProps.recorded, msg.requesterID, msg.uuid, msg.callState));
  }

  def handleVoiceHangingUp(msg: VoiceHangingUp) {
    outGW.send(new VoiceHangingUp2(mProps.meetingID, mProps.recorded, msg.requesterID, msg.uuid, msg.callState, msg.hangupCause));
  }

  private def globalVideoDied(): Boolean = {
    meetingModel.globalVideoStreamName().equals("")
  }
}
