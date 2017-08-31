package org.bigbluebutton.core.apps

import org.bigbluebutton.core.api._
import org.bigbluebutton.core.MeetingActor
import org.bigbluebutton.core.OutMessageGateway
import org.bigbluebutton.common.messages.{ Constants => MessagesConstants }

trait VoiceApp {
  this: MeetingActor =>

  val outGW: OutMessageGateway

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

  def handleActiveTalkerChanged(msg: ActiveTalkerChanged) {
    usersModel.getUserWithVoiceUserId(msg.voiceUserId) foreach { user =>
      val oldTalkerUserId = meetingModel.talkerUserId
      meetingModel.setTalkerUserId(user.userID)
      handleActiveTalkerChangedInWebconference(oldTalkerUserId, meetingModel.talkerUserId())
      log.debug("Active Talker Changed: talkerUserId={" + meetingModel.talkerUserId() + "}")
    }
  }

  def handleVoiceDialing(msg: VoiceDialing) {
    outGW.send(new VoiceDialing2(mProps.meetingID, mProps.recorded, msg.requesterID, msg.uuid, msg.callState));
  }

  def handleVoiceHangingUp(msg: VoiceHangingUp) {
    outGW.send(new VoiceHangingUp2(mProps.meetingID, mProps.recorded, msg.requesterID, msg.uuid, msg.callState, msg.hangupCause));
  }
}
