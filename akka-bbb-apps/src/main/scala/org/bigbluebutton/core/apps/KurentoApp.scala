package org.bigbluebutton.core.apps

import org.bigbluebutton.core.api._
import scala.collection.mutable.HashMap
import org.bigbluebutton.core.MeetingActor
import org.bigbluebutton.core.OutMessageGateway
import org.bigbluebutton.common.messages.{ Constants => MessagesConstants }

trait KurentoApp {
  this: MeetingActor =>

  val outGW: OutMessageGateway

  def handleStartMediaSource(msg: StartMediaSource) {
    System.out.println("StartMediaSource. [meetingId = " + msg.meetingID + " , mediaSourceId = " + msg.mediaSourceId + " , mediaSourceUri = " + msg.mediaSourceUri + "]")

    var params = new scala.collection.mutable.HashMap[String, String]
    params += MessagesConstants.VOICE_CONF -> mProps.voiceBridge
    params += MessagesConstants.INPUT -> msg.mediaSourceUri
    //Each media uses RTP protocol to send it's data to BBB
    outGW.send(new StartKurentoRtpRequest(msg.meetingID, msg.mediaSourceId, params))
  }

  def handleStopMediaSource(msg: StopMediaSource) {
    System.out.println("StopMediaSource. [meetingId = " + msg.meetingID + " , mediaSourceId = " + msg.mediaSourceId + "]")
    outGW.send(new StopKurentoRtpRequest(msg.meetingID, msg.mediaSourceId))
  }

  def handleStartKurentoRtpReply(msg: StartKurentoRtpReply) {
    //if (usersModel.hasUser(msg.kurentoEndpointId)) {
    System.out.println("StartKurentoRtpReply. [meetingId = " + msg.meetingID + " , kurentoEndpointId = " + msg.kurentoEndpointId + "]")
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
    outGW.send(new StartTranscoderRequest(mProps.meetingID, msg.kurentoEndpointId, params))
    //}
  }

  def handleStopKurentoRtpReply(msg: StopKurentoRtpReply) {
    //if (usersModel.hasUser(msg.kurentoEndpointId)) {
    System.out.println("StopKurentoRtpReply. [meetingId = " + msg.meetingID + " , kurentoEndpointId = " + msg.kurentoEndpointId + "]")
    outGW.send(new StopTranscoderRequest(mProps.meetingID, msg.kurentoEndpointId))
    //}
  }

  def userSharedKurentoRtpStream(user: UserVO, params: Map[String, String]) {
    getTranscoderParam(MessagesConstants.OUTPUT, params) match {
      case Some(streamName) =>
        System.out.println("Updating Kurento RTP stream to: " + streamName + " , userId = " + user.userID)

        val streams = user.webcamStreams + streamName
        val uvo = user.copy(hasStream = true, webcamStreams = streams, mediaSourceUser = true)
        usersModel.addUser(uvo) // TODO: THIS IS NOT BEING ADDED,
        outGW.send(new UserSharedWebcam(mProps.meetingID, mProps.recorded, user.userID, streamName))
      case _ => System.out.println("Can't update Kurento RTP stream: invalid stream")
    }
  }

  def handleUpdateKurentoToken(msg: UpdateKurentoToken) {
    meetingModel.setKurentoToken(msg.token)
  }

  def getTranscoderParam(key: String, params: Map[String, String]): Option[String] = {
    Option(params) match {
      case Some(map) => map.get(key)
      case _ => None
    }
  }

}
