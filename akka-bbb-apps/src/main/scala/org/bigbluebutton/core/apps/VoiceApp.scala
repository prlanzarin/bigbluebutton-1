package org.bigbluebutton.core.apps

import org.bigbluebutton.core.api._
import org.bigbluebutton.core.MeetingActor
import org.bigbluebutton.core.OutMessageGateway
import org.bigbluebutton.common.messages.{ Constants => MessagesConstants }

trait VoiceApp {
  this: MeetingActor =>

  val outGW: OutMessageGateway

  def handleUpdateCallAgent(msg: UpdateCallAgent) {
    if (usersModel.isGlobalCallAgent(msg.userId)) {
      meetingModel.setGlobalCallCallername(msg.userId)
      meetingModel.setGlobalCallLocalIpAddress(msg.localIpAddress)
      meetingModel.setSipHost(msg.sipHost)
      meetingModel.setGlobalCallLocalVideoPort(msg.localVideoPort)
      meetingModel.setGlobalCallRemoteVideoPort(msg.remoteVideoPort)
      log.debug("GlobalCall update: " +
        " localIpAddress = " + meetingModel.globalCallLocalIpAddress() +
        " localVideoPort = " + meetingModel.globalCallLocalVideoPort() +
        " remoteVideoPort = " + meetingModel.globalCallRemoteVideoPort() +
        " sipHost = " + meetingModel.sipHost())
      if (meetingModel.isSipPhonePresent()) {
        val params = new scala.collection.mutable.HashMap[String, String]
        params += MessagesConstants.LOCAL_IP_ADDRESS -> meetingModel.globalCallLocalIpAddress()
        params += MessagesConstants.LOCAL_VIDEO_PORT -> meetingModel.globalCallLocalVideoPort()
        params += MessagesConstants.REMOTE_VIDEO_PORT -> meetingModel.globalCallRemoteVideoPort()
        params += MessagesConstants.DESTINATION_IP_ADDRESS -> meetingModel.sipHost()
        log.debug("Global CallAgent [{}] restarted. Updating transcoder (Sending UpdateTranscoderRequest message)", mProps.voiceBridge)
        outGW.send(new UpdateTranscoderRequest(mProps.meetingID, meetingModel.globalCallCallername, params))
      }
    } else {
      getUser(msg.userId) match {
        case Some(u) => {
          val si = new SipInfo(msg.localIpAddress, msg.localVideoPort, msg.remoteVideoPort, msg.sipHost)
          val nu = u.copy(sipInfo = si)
          usersModel.addUser(nu)
          log.debug("User's CallAgent update: " +
            " localIpAddress = " + nu.sipInfo.localIpAddress +
            " localVideoPort = " + nu.sipInfo.localVideoPort +
            " remoteVideoPort = " + nu.sipInfo.remoteVideoPort +
            " sipHost = " + nu.sipInfo.sipHost)
          if (meetingModel.isSipPhonePresent() && meetingModel.isTalker(msg.userId)) {
            val params = new scala.collection.mutable.HashMap[String, String]
            params += MessagesConstants.LOCAL_IP_ADDRESS -> nu.sipInfo.localIpAddress
            params += MessagesConstants.LOCAL_VIDEO_PORT -> nu.sipInfo.localVideoPort
            params += MessagesConstants.REMOTE_VIDEO_PORT -> nu.sipInfo.remoteVideoPort
            params += MessagesConstants.DESTINATION_IP_ADDRESS -> nu.sipInfo.sipHost
            log.debug("User [{}] restarted call agent. Updating transcoder (Sending UpdateTranscoderRequest message)", msg.userId)
            outGW.send(new UpdateTranscoderRequest(mProps.meetingID, u.userID, params))
          }
        }
        case None => log.debug("User " + msg.userId + " not found. Can't update SIP info")
      }
    }

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

    handleTranscoding()
    outGW.send(new SipVideoUpdated(mProps.meetingID, mProps.recorded, mProps.voiceBridge, meetingModel.isSipVideoPresent(), meetingModel.globalVideoStreamName(), meetingModel.talkerUserId(), meetingModel.globalVideoStreamWidth(), meetingModel.globalVideoStreamHeight()))
  }

  def handleSipVideoResumed(msg: SipVideoResumed) {
    meetingModel.setSipVideoPresent(true)
    log.debug("Sip video RESUMED: Sending SipVideoUpdated event " +
      "(isSipVideoPresent=" + meetingModel.isSipVideoPresent() + ") " +
      "(globalVideoStreamName=" + meetingModel.globalVideoStreamName() + ")")

    handleTranscoding()
    outGW.send(new SipVideoUpdated(mProps.meetingID, mProps.recorded, mProps.voiceBridge, meetingModel.isSipVideoPresent(), meetingModel.globalVideoStreamName(), meetingModel.talkerUserId(), meetingModel.globalVideoStreamWidth(), meetingModel.globalVideoStreamHeight()))
  }

  def handleActiveTalkerChanged(msg: ActiveTalkerChanged) {
    usersModel.getUserWithVoiceUserId(msg.voiceUserId) foreach { user =>
      val oldTalkerUserId = meetingModel.talkerUserId
      meetingModel.setTalkerUserId(user.userID)
      handleActiveTalkerChangedInWebconference(oldTalkerUserId, meetingModel.talkerUserId())
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

  def handleTranscoding() {
    if (meetingModel.isSipPhonePresent() && meetingModel.isSipVideoEnabled()) {
      startTranscoder(meetingModel.talkerUserId())
    } else {
      stopAllTranscoders()
    }
  }

  private def startTranscoder(userId: String) {
    usersModel.getUser(userId) foreach { user =>
      if (!user.phoneUser) {

        //User's RTP transcoder
        val params = new scala.collection.mutable.HashMap[String, String]

        if (user.hasStream) {
          params += MessagesConstants.TRANSCODER_TYPE -> MessagesConstants.TRANSCODE_RTMP_TO_RTP
          params += MessagesConstants.INPUT -> usersModel.getUserMainWebcamStream(user.userID)
        } else {
          //if user has no video , send videoconf logo to FS
          params += MessagesConstants.TRANSCODER_TYPE -> MessagesConstants.TRANSCODE_FILE_TO_RTP
        }

        params += MessagesConstants.LOCAL_IP_ADDRESS -> user.sipInfo.localIpAddress
        params += MessagesConstants.LOCAL_VIDEO_PORT -> user.sipInfo.localVideoPort
        params += MessagesConstants.REMOTE_VIDEO_PORT -> user.sipInfo.remoteVideoPort
        params += MessagesConstants.DESTINATION_IP_ADDRESS -> user.sipInfo.sipHost

        params += MessagesConstants.MEETING_ID -> mProps.meetingID
        params += MessagesConstants.VOICE_CONF -> mProps.voiceBridge
        params += MessagesConstants.CALLERNAME -> user.name
        outGW.send(new StartTranscoderRequest(mProps.meetingID, user.userID, params))

        //videoconf logo transcoder (shown in webconference)
        val params_logo = params.clone
        params_logo += MessagesConstants.TRANSCODER_TYPE -> MessagesConstants.TRANSCODE_FILE_TO_RTMP
        params_logo -= MessagesConstants.INPUT
        outGW.send(new StartTranscoderRequest(mProps.meetingID, meetingModel.VIDEOCONFERENCE_LOGO_PREFIX + mProps.voiceBridge, params_logo))

      } else {
        //start global transcoder
        val params = new scala.collection.mutable.HashMap[String, String]
        params += MessagesConstants.TRANSCODER_TYPE -> MessagesConstants.TRANSCODE_RTP_TO_RTMP
        params += MessagesConstants.LOCAL_IP_ADDRESS -> meetingModel.globalCallLocalIpAddress()
        params += MessagesConstants.LOCAL_VIDEO_PORT -> meetingModel.globalCallLocalVideoPort()
        params += MessagesConstants.REMOTE_VIDEO_PORT -> meetingModel.globalCallRemoteVideoPort()
        params += MessagesConstants.DESTINATION_IP_ADDRESS -> meetingModel.sipHost()
        params += MessagesConstants.MEETING_ID -> mProps.meetingID
        params += MessagesConstants.VOICE_CONF -> mProps.voiceBridge
        params += MessagesConstants.CALLERNAME -> meetingModel.globalCallCallername
        outGW.send(new StartTranscoderRequest(mProps.meetingID, meetingModel.globalCallCallername, params))
      }
    }
  }

  private def stopTranscoder(userId: String) {
    getUser(userId) match {
      case Some(user) => {
        if (!user.phoneUser) {
          //also stops videoconf logo
          outGW.send(new StopTranscoderRequest(mProps.meetingID, userId))
          outGW.send(new StopTranscoderRequest(mProps.meetingID, meetingModel.VIDEOCONFERENCE_LOGO_PREFIX + mProps.voiceBridge))
        } else {
          //we dont't stop global transcoder, but let it die for timeout
          //outGW.send(new StopTranscoderRequest(mProps.meetingID, meetingModel.globalCallCallername))
        }
      }
      case None => {}
    }
  }

  def stopAllTranscoders() {
    getUser(meetingModel.talkerUserId()) match {
      case Some(user) => {
        outGW.send(new StopTranscoderRequest(mProps.meetingID, user.userID))
        outGW.send(new StopTranscoderRequest(mProps.meetingID, meetingModel.VIDEOCONFERENCE_LOGO_PREFIX + mProps.voiceBridge))
      }
      case None =>
    }
    if (!meetingModel.globalCallCallername.isEmpty)
      outGW.send(new StopTranscoderRequest(mProps.meetingID, meetingModel.globalCallCallername))
  }

  private def handleActiveTalkerChangedInWebconference(oldTalkerUserId: String, newTalkerUserId: String) {
    if (usersModel.activeTalkerChangedInWebconference(oldTalkerUserId, newTalkerUserId) && meetingModel.isSipPhonePresent()) {
      log.debug("Changing transcoder. oldTalkerUserId = " + oldTalkerUserId + " newTalkerUserId = " + newTalkerUserId)
      stopTranscoder(oldTalkerUserId)
      startTranscoder(newTalkerUserId)
    }
  }

  def handleUserShareWebcamTranscoder(userId: String) {
    if (meetingModel.isTalker(userId)) {
      val params = new scala.collection.mutable.HashMap[String, String]
      params += MessagesConstants.TRANSCODER_TYPE -> MessagesConstants.TRANSCODE_RTMP_TO_RTP
      params += MessagesConstants.INPUT -> usersModel.getUserMainWebcamStream(userId)
      log.debug("User [{}] shared webcam, updating his transcoder", userId)
      outGW.send(new UpdateTranscoderRequest(mProps.meetingID, userId, params))
    }
  }

  def handleUserUnshareWebcamTranscoder(userId: String) {
    if (meetingModel.isTalker(userId)) {
      getUser(userId) match {
        case Some(user) => {
          val params = new scala.collection.mutable.HashMap[String, String]
          if (user.hasStream) {
            params += MessagesConstants.TRANSCODER_TYPE -> MessagesConstants.TRANSCODE_RTMP_TO_RTP
            params += MessagesConstants.INPUT -> usersModel.getUserMainWebcamStream(user.userID)
          } else {
            params += MessagesConstants.TRANSCODER_TYPE -> MessagesConstants.TRANSCODE_FILE_TO_RTP
          }
          log.debug("User [{}] unshared webcam, updating his transcoder", userId)
          outGW.send(new UpdateTranscoderRequest(mProps.meetingID, userId, params))
        }
        case None => log.debug("")
      }
    }
  }

  def handleStartTranscoderReply(msg: StartTranscoderReply) {
    System.out.println("Received StartTranscoderReply. Params: [\n"
      + "meetingID = " + msg.meetingID + "\n"
      + "transcoderId = " + msg.transcoderId + "\n\n")

    updateVideoConferenceStreamName(msg.params)
  }

  def handleUpdateTranscoderReply(msg: UpdateTranscoderReply) {
    System.out.println("Received UpdateTranscoderReply. Params: [\n"
      + "meetingID = " + msg.meetingID + "\n"
      + "transcoderId = " + msg.transcoderId + "\n\n")

    if (!usersModel.activeTalkerChangedInWebconference(meetingModel.talkerUserId(), msg.transcoderId)) { //make sure this transcoder is the current talker
      updateVideoConferenceStreamName(msg.params)
    }
  }

  def handleStopTranscoderReply(msg: StopTranscoderReply) {
    System.out.println("Received StopTranscoderReply. Params: [\n"
      + "meetingID = " + msg.meetingID + "\n"
      + "transcoderId = " + msg.transcoderId + "\n\n")

    outGW.send(new SipVideoUpdated(mProps.meetingID, mProps.recorded, mProps.voiceBridge, meetingModel.isSipVideoPresent(), meetingModel.globalVideoStreamName(), meetingModel.talkerUserId(), meetingModel.globalVideoStreamWidth(), meetingModel.globalVideoStreamHeight()))
  }

  def handleTranscoderStatusUpdate(msg: TranscoderStatusUpdate) {
    System.out.println("TranscoderStatusUpdate. Params: [\n"
      + "meetingID = " + msg.meetingID + "\n"
      + "transcoderId = " + msg.transcoderId + "\n\n")

    System.out.println(" currentTalker: " + meetingModel.talkerUserId() + ", transcoderId: " + msg.transcoderId + ". activeTalkerChangedInWebconference? " + usersModel.activeTalkerChangedInWebconference(meetingModel.talkerUserId(), msg.transcoderId))
    if (!usersModel.activeTalkerChangedInWebconference(meetingModel.talkerUserId(), msg.transcoderId)) { //make sure this transcoder is the current talker
      updateVideoConferenceStreamName(msg.params)
    }
  }

  def handleStartProbingReply(msg: StartProbingReply) {
    System.out.println("StartProbingReply. Params: [\n"
      + "meetingID = " + msg.meetingID + "\n"
      + "transcoderId = " + msg.transcoderId + "\n\n")

    //TODO
  }

  private def updateVideoConferenceStreamName(params: Map[String, String]) {
    if (meetingModel.isSipPhonePresent()) {
      Option(params) match {
        case Some(map) =>
          params foreach { param =>
            Option(param._1) match {
              case Some(output) =>
                if (!output.isEmpty && (output == MessagesConstants.OUTPUT)) { //no output for user's transcoders
                  val videoconferenceStreamName = param._2
                  if (!videoconferenceStreamName.trim.equals("")) {
                    System.out.println("Updating videoconferenceStreamName to: " + videoconferenceStreamName)
                    meetingModel.setGlobalVideoStreamName(videoconferenceStreamName)
                    outGW.send(new SipVideoUpdated(mProps.meetingID, mProps.recorded, mProps.voiceBridge, meetingModel.isSipVideoPresent(), meetingModel.globalVideoStreamName(), meetingModel.talkerUserId(), meetingModel.globalVideoStreamWidth(), meetingModel.globalVideoStreamHeight()))
                  }
                }
              case _ => //
            }
          }

        case _ => System.out.println("Can't update videoconference stream, unknown parameters")
      }
    }
  }

}
