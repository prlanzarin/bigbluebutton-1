package org.bigbluebutton.core.apps

import org.bigbluebutton.core.api._
import scala.collection.mutable.HashMap
import org.bigbluebutton.core.MeetingActor
import org.bigbluebutton.core.LiveMeeting
import org.bigbluebutton.core.OutMessageGateway
import org.bigbluebutton.common.messages.{ Constants => MessagesConstants }

trait KurentoApp {
  this: LiveMeeting =>

  val outGW: OutMessageGateway

  def handleStartMediaSource(msg: StartMediaSource) {
    log.debug("StartMediaSource. [meetingId = " + msg.meetingID + " , mediaSourceId = " + msg.mediaSourceId + " , mediaSourceUri = " + msg.mediaSourceUri + "]")

    var params = new scala.collection.mutable.HashMap[String, String]
    params += MessagesConstants.VOICE_CONF -> mProps.voiceBridge
    params += MessagesConstants.INPUT -> msg.mediaSourceUri
    //Each media uses RTP protocol to send it's data to BBB
    outGW.send(new StartKurentoRtpRequest(msg.meetingID, msg.mediaSourceId, params))
  }

  def handleStopMediaSource(msg: StopMediaSource) {
    log.debug("StopMediaSource. [meetingId = " + msg.meetingID + " , mediaSourceId = " + msg.mediaSourceId + "]")
    outGW.send(new StopKurentoRtpRequest(msg.meetingID, msg.mediaSourceId))
  }

  def handleStartKurentoRtpReply(msg: StartKurentoRtpReply) {
    log.debug("StartKurentoRtpReply. [meetingId = " + msg.meetingID + " , kurentoEndpointId = " + msg.kurentoEndpointId + "]")
    if (usersModel.hasUser(msg.kurentoEndpointId)) {
      log.info("User [{}] found, starting transcoder on RtpReply", msg.kurentoEndpointId)
      var params = new scala.collection.mutable.HashMap[String, String]
      msg.params foreach {
        e => params += e
      }
      params += MessagesConstants.TRANSCODER_TYPE -> MessagesConstants.TRANSCODE_RTP_TO_RTMP
      params += MessagesConstants.CODEC -> MessagesConstants.COPY
      params += MessagesConstants.CALLERNAME -> msg.kurentoEndpointId
      params += MessagesConstants.LOCAL_IP_ADDRESS -> msg.params(MessagesConstants.DESTINATION_IP_ADDRESS)
      params += MessagesConstants.LOCAL_VIDEO_PORT -> msg.params(MessagesConstants.DESTINATION_VIDEO_PORT)
      params -= MessagesConstants.DESTINATION_VIDEO_PORT
      outGW.send(new StartTranscoderRequest(mProps.meetingID, msg.params(MessagesConstants.INPUT), params))

      // Desksharing viewing trigger
      if (msg.params(MessagesConstants.STREAM_TYPE) == MessagesConstants.STREAM_TYPE_DESKSHARE) {
        outGW.send(new StartDeskshareViewing(mProps.meetingID, 640, 480))
      }
    }
  }

  def handleStopKurentoRtpReply(msg: StopKurentoRtpReply) {
    log.debug("StopKurentoRtpReply. [meetingId = " + msg.meetingID + " , kurentoEndpointId = " + msg.kurentoEndpointId + "]")
    outGW.send(new StopTranscoderRequest(mProps.meetingID, msg.kurentoEndpointId))
  }

  def handleUpdateKurentoRtp(msg: UpdateKurentoRtp) {
    log.debug("UpdateKurentoRtp. [meetingId = " + msg.meetingID + " , kurentoEndpointId = " + msg.kurentoEndpointId + "]")
    var params = new scala.collection.mutable.HashMap[String, String]
    msg.params foreach {
      e => params += e
    }
    params += MessagesConstants.CALLERNAME -> msg.kurentoEndpointId
    params += MessagesConstants.LOCAL_IP_ADDRESS -> msg.params(MessagesConstants.DESTINATION_IP_ADDRESS)
    params += MessagesConstants.LOCAL_VIDEO_PORT -> msg.params(MessagesConstants.DESTINATION_VIDEO_PORT)
    params += MessagesConstants.STREAM_TYPE -> MessagesConstants.STREAM_TYPE_VIDEO
    params -= MessagesConstants.DESTINATION_VIDEO_PORT
    outGW.send(new UpdateTranscoderRequest(mProps.meetingID, msg.kurentoEndpointId, params))
  }

  def userSharedKurentoRtpStream(user: UserVO, params: Map[String, String]) {
    log.debug("UserSharedKurentoRtpStream => UserID [{}], params [{}]", user.userID, params)
    getTranscoderParam(MessagesConstants.OUTPUT, params) match {
      case Some(streamName) =>
        log.debug("Updating Kurento RTP stream to: " + streamName + " , userId = " + user.userID)
        val streams = user.webcamStreams + streamName
        val uvo = user.copy(hasStream = true, webcamStreams = streams, mediaSourceUser = true)
        usersModel.addUser(uvo)
        outGW.send(new UserSharedWebcam(mProps.meetingID, mProps.recorded, user.userID, streamName))

      case _ => log.debug("Can't update Kurento RTP stream: invalid stream")
    }
  }

  def handleUpdateKurentoToken(msg: UpdateKurentoToken) {
    meetingModel.setKurentoToken(msg.token)
  }

  def handleAllMediaSourcesStopped(msg: AllMediaSourcesStopped) {
    log.debug("KurentoApp - Received AllMediaSourcesStopped");
    usersModel.getMediaSourceUsers() foreach {
      u => outGW.send(new StopTranscoderRequest(mProps.meetingID, u.voiceUser.callerName))
    }
  }

  def handleStartKurentoSendRtpReply(msg: StartKurentoSendRtpReply) {
    log.debug("StartKurentoSendRtpReply. [meetingId = " + msg.meetingID + "]")
    var params = new scala.collection.mutable.HashMap[String, String]
    msg.params foreach {
      e => params += e
    }

    params += MessagesConstants.CODEC -> MessagesConstants.COPY
    params += MessagesConstants.STREAM_TYPE -> msg.params(MessagesConstants.STREAM_TYPE)

    // Deskshare stream from Mconf to endpoint
    if (msg.params(MessagesConstants.STREAM_TYPE) == MessagesConstants.STREAM_TYPE_DESKSHARE) {
      params += MessagesConstants.TRANSCODER_TYPE -> MessagesConstants.TRANSCODE_RTMP_TO_RTP
      outGW.send(new StartTranscoderRequest(mProps.meetingID, msg.params(MessagesConstants.INPUT), params))
    } else {
      // Webcam stream from Mconf to endpoint
      startOutboundStreamTranscoder(msg.params(MessagesConstants.INPUT), params)
    }
  }

  def getTranscoderParam(key: String, params: Map[String, String]): Option[String] = {
    Option(params) match {
      case Some(map) => map.get(key)
      case _ => None
    }
  }

  def handleOutboundStream() {
    usersModel.getCurrentPresenter match {
      case Some(curPres) =>
        log.info("Starting video poll for presenter " + curPres.userID)
        val params = new scala.collection.mutable.HashMap[String, String]
        params += MessagesConstants.INPUT -> curPres.userID
        params += MessagesConstants.STREAM_TYPE -> MessagesConstants.STREAM_TYPE_VIDEO
        outGW.send(new StartKurentoSendRtpRequest(mProps.meetingID, params))
      case None => // do nothing
        log.info("No presenter found!")
    }
  }

  def startOutboundStream(userId: String, streamType: String) = {
    val params = new scala.collection.mutable.HashMap[String, String]
    params += MessagesConstants.INPUT -> userId
    params += MessagesConstants.STREAM_TYPE -> streamType
    outGW.send(new StartKurentoSendRtpRequest(mProps.meetingID, params))
  }

  def startOutboundStreamTranscoder(userId: String, params: scala.collection.mutable.HashMap[String, String]) = {
    usersModel.getUser(userId) foreach { user =>
      if (!user.phoneUser) {
        //User's RTP transcoder
        if (user.hasStream) {
          params += MessagesConstants.TRANSCODER_TYPE -> MessagesConstants.TRANSCODE_RTMP_TO_RTP
          params -= MessagesConstants.INPUT
          params += MessagesConstants.INPUT -> usersModel.getUserMainWebcamStream(userId)
          log.info("Starting VIDEO transcoder for Web User [{}] with params [{}]", userId, params)
          outGW.send(new StartTranscoderRequest(mProps.meetingID, user.userID, params))
        }
      }
    }
  }

  def stopOutboundTranscoder(userId: String) {
    getUser(userId) match {
      case Some(user) => {
        if (!user.phoneUser) {
          outGW.send(new StopTranscoderRequest(mProps.meetingID, userId))
        }
      }
      case None => {}
    }
  }

  def stopAllTranscoders() {
    getUser(meetingModel.talkerUserId()) match {
      case Some(user) => {
        outGW.send(new StopTranscoderRequest(mProps.meetingID, user.userID))
      }
      case None => // TODO
    }
  }

  def handleActiveTalkerChangedInWebconference(oldTalkerUserId: String, newTalkerUserId: String) {
    log.debug("Changing talker transcoder. Old user = [{}] , New user = [{}]", oldTalkerUserId, newTalkerUserId)

    log.info("Handling talker stream isDesksharePresent " +
      meetingModel.isDesksharePresent() + " hasMediaSourceUsers " +
      usersModel.getPhoneUsersSendingVideo().length +
      " talkerUserId " + newTalkerUserId + " isSipPhonePresent " + meetingModel.isSipPhonePresent())

    // There's a new talker in the conference
    if (usersModel.activeTalkerChangedInWebconference(oldTalkerUserId, newTalkerUserId) && meetingModel.isSipPhonePresent() && !meetingModel.isDesksharePresent()) {
      usersModel.getUser(newTalkerUserId) match {
        case Some(talker) => {
          log.info("Starting video poll for talker " + talker.userID)
          stopOutboundTranscoder(oldTalkerUserId)
          startOutboundStream(talker.userID, MessagesConstants.STREAM_TYPE_VIDEO)
        }
        case None => log.info("Talker user [{}] could not be found", newTalkerUserId)
      }
    }
  }

  def handleUserShareWebcamTranscoder(userId: String) {
    getUser(userId) match {
      case Some(user) => {
        if (userId == meetingModel.talkerUserId ||
          (meetingModel.isDesksharePresent() && user.presenter)) {
          log.debug("User [{}] shared webcam, updating his transcoder for meetingID [{}]", userId, mProps.meetingID)
          handleOutboundStream()
        }
      }
      case None => {}
    }
  }

  def handleUserUnshareWebcamTranscoder(userId: String) {
    getUser(userId) match {
      case Some(user) => {
        // This should guarantee the transcoder session will be flushed
        stopOutboundTranscoder(userId)
      }
      case None => log.debug("")
    }
  }

  def handleStartTranscoderReply(msg: StartTranscoderReply) {
    log.debug("Received StartTranscoderReply. Params: [\n"
      + "meetingID = " + msg.meetingID + "\n"
      + "transcoderId = " + msg.transcoderId + "\n\n")

    usersModel.getUser(msg.transcoderId) match {
      case Some(user) => if (user.mediaSourceUser) {
        userSharedKurentoRtpStream(user, msg.params)
      }
      case _ => log.debug("User could not be found for transcoder [{}]", msg.transcoderId);
    }
  }

  def handleUpdateTranscoderReply(msg: UpdateTranscoderReply) {
    log.debug("Received UpdateTranscoderReply. Params: [\n"
      + "meetingID = " + msg.meetingID + "\n"
      + "transcoderId = " + msg.transcoderId + "\n\n")

    usersModel.getMediaSourceUser(msg.transcoderId) match {
      case Some(user) => if (user.mediaSourceUser) {
        userSharedKurentoRtpStream(user, msg.params)
      }
      case _ =>
        if (!usersModel.activeTalkerChangedInWebconference(meetingModel.talkerUserId(), msg.transcoderId)) { //make sure this transcoder is the current talker
          // TODO smart switch transcoder instances
        }
    }
  }

  def handleStopTranscoderReply(msg: StopTranscoderReply) {
    log.debug("Received StopTranscoderReply. Params: [\n"
      + "meetingID = " + msg.meetingID + "\n"
      + "transcoderId = " + msg.transcoderId + "\n\n")
  }

  def handleTranscoderStatusUpdate(msg: TranscoderStatusUpdate) {
    log.debug("TranscoderStatusUpdate. Params: [\n"
      + "meetingID = " + msg.meetingID + "\n"
      + "transcoderId = " + msg.transcoderId + "\n\n")

    log.debug(" currentTalker: " + meetingModel.talkerUserId() + ", transcoderId: " + msg.transcoderId + ". activeTalkerChangedInWebconference? " + usersModel.activeTalkerChangedInWebconference(meetingModel.talkerUserId(), msg.transcoderId))

    if (!usersModel.activeTalkerChangedInWebconference(meetingModel.talkerUserId(), msg.transcoderId)) { //make sure this transcoder is the current talker
      // TODO smart switch transcoder instances
    }
  }

}
