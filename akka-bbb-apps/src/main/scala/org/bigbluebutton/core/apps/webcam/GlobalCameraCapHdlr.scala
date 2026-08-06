package org.bigbluebutton.core.apps.webcam

import org.bigbluebutton.core.GlobalCameraCapActor
import org.bigbluebutton.core.api.{ MeetingCameraCountInternalMsg, SetGlobalCameraAllowanceInternalMsg }
import org.bigbluebutton.core.bus.BigBlueButtonEvent
import org.bigbluebutton.core.db.{ MeetingDAO, NotificationDAO }
import org.bigbluebutton.core.models._
import org.bigbluebutton.core.running.MeetingActor
import org.bigbluebutton.core2.MeetingStatus2x
import org.bigbluebutton.core2.message.senders.MsgBuilder

/**
 * The meeting's half of the server-wide camera cap: it reports what it publishes
 * and applies the allowance GlobalCameraCapActor sends back.
 */
trait GlobalCameraCapHdlr {
  this: MeetingActor =>

  private def capState = liveMeeting.globalCameraCapState

  private def meetingId = liveMeeting.props.meetingProp.intId

  /** How long to wait for the SFU to confirm a stop before re-issuing it. */
  private val PendingEjectionTimeout = 10000L

  /**
   * Tells the arbiter what this meeting currently publishes. Called on every
   * camera transition for immediacy, and on the housekeeping tick so a meeting
   * that has never shared a camera still registers (and so a lost report heals).
   */
  def reportCameraDemand(): Unit = {
    // A meeting keeps ticking for a couple of seconds after it is torn down, and a
    // report from that window would land after the arbiter already dropped it -
    // permanently inflating the server's camera count with a dead meeting's.
    if (!globalCameraCap.enabled || MeetingStatus2x.hasMeetingEnded(liveMeeting.status)) return

    val breakoutProps = liveMeeting.props.breakoutProps
    val parentMeetingId =
      if (liveMeeting.props.meetingProp.isBreakout) breakoutProps.parentId else ""

    eventBus.publish(BigBlueButtonEvent(
      GlobalCameraCapActor.Channel,
      MeetingCameraCountInternalMsg(
        meetingId,
        parentMeetingId,
        Webcams.findAll(liveMeeting.webcams).length,
        liveMeeting.props.meetingProp.globalCameraCap
      )
    ))

    // The published state includes whether sharing is blocked right now, which
    // moves with the camera count and not only with the allowance.
    persistCameraCapState()
    reEjectStalePending()
  }

  /**
   * Re-issues an ejection the SFU never acknowledged. The stream is still
   * published, so dropping it silently would leave the meeting permanently over
   * its allowance.
   */
  private def reEjectStalePending(): Unit = {
    val expired = GlobalCameraCapState.expirePendingEjections(
      capState,
      System.currentTimeMillis(),
      PendingEjectionTimeout
    )

    expired
      .flatMap(streamId => Webcams.findWithStreamId(liveMeeting.webcams, streamId))
      .foreach { stream =>
        log.warning(
          "Camera ejection unacknowledged, retrying. meetingId={} streamId={}",
          meetingId, stream.streamId
        )
        GlobalCameraCapState.markPendingEjection(capState, stream.streamId, System.currentTimeMillis())
        CameraHdlrHelpers.requestBroadcastedCamEjection(meetingId, stream.userId, stream.streamId, outGW)
      }
  }

  def handleSetGlobalCameraAllowance(msg: SetGlobalCameraAllowanceInternalMsg): Unit = {
    if (GlobalCameraCapState.setAllowance(capState, msg.allowance)) {
      persistCameraCapState()
      enforceGlobalCameraCap()
    }

    // Deliberately keyed on the server budget rather than on this meeting's
    // allowance: trimming a meeting frees a slot inside its own share, so its
    // allowance rises immediately afterwards even though the server has recovered
    // nothing. Announcing that as "sharing is available again" invites the user
    // straight back into the trim.
    if (msg.budgetRelaxed) notifyCapLifted()
  }

  /** Stops however many cameras the meeting is over its allowance, newest first. */
  def enforceGlobalCameraCap(): Unit = {
    GlobalCameraCapState.getAllowance(capState) foreach { allowance =>
      val live = Webcams
        .findAll(liveMeeting.webcams)
        .filterNot(s => GlobalCameraCapState.isPendingEjection(capState, s.streamId))

      val victims = CameraVictimSelector.selectVictims(live.map(toCandidate), allowance)

      if (victims.nonEmpty) {
        log.info(
          "Global camera cap trimming meeting. meetingId={} allowance={} publishers={} stopping={}",
          meetingId, allowance, live.length, victims.length
        )

        val now = System.currentTimeMillis()
        victims.foreach { victim =>
          GlobalCameraCapState.markPendingEjection(capState, victim.streamId, now)
          GlobalCameraCapState.markTrimmed(capState, victim.userId)
          CameraHdlrHelpers.requestBroadcastedCamEjection(meetingId, victim.userId, victim.streamId, outGW)
          notify(MsgBuilder.buildNotifyUserInMeetingEvtMsg(
            victim.userId,
            meetingId,
            "warning",
            "video_off",
            "app.video.notification.cameraCapEnforced",
            "Notification shown when the server stops a user's camera to stay within its capacity",
            Map.empty
          ))
        }

        notify(MsgBuilder.buildNotifyRoleInMeetingEvtMsg(
          Roles.MODERATOR_ROLE,
          meetingId,
          "warning",
          "video_off",
          "app.video.notification.cameraCapEnforcedModerator",
          "Notification telling moderators the server stopped cameras to stay within its capacity",
          Map("count" -> victims.length.toString)
        ))
      }
    }
  }

  private def notifyCapLifted(): Unit = {
    val trimmed = GlobalCameraCapState.takeTrimmedUsers(capState)

    if (trimmed.nonEmpty) {
      trimmed.foreach { userId =>
        notify(MsgBuilder.buildNotifyUserInMeetingEvtMsg(
          userId,
          meetingId,
          "info",
          "video",
          "app.video.notification.cameraCapLifted",
          "Notification telling a user they can share their camera again",
          Map.empty
        ))
      }

      notify(MsgBuilder.buildNotifyRoleInMeetingEvtMsg(
        Roles.MODERATOR_ROLE,
        meetingId,
        "info",
        "video",
        "app.video.notification.cameraCapLifted",
        "Notification telling moderators camera sharing is available again",
        Map.empty
      ))
    }
  }

  private def notify(event: org.bigbluebutton.common2.msgs.BbbCommonEnvCoreMsg): Unit = {
    outGW.send(event)
    NotificationDAO.insert(event)
  }

  /**
   * Publishes the cap the client should honour, folding the server's allowance
   * together with the meeting's own cap so the client reads one number and keeps
   * enforcing whichever is stricter.
   *
   * `globalCameraCapActive` says the server is refusing a new camera *now*, which
   * is why it is computed here rather than by the client: a viewer's stream rows
   * are row-filtered (webcamsOnlyForModerator), so a client count can silently
   * under-report and leave the button enabled on a full server.
   */
  private def persistCameraCapState(): Unit = {
    val meetingCap = liveMeeting.props.meetingProp.meetingCameraCap
    val allowance = GlobalCameraCapState.getAllowance(capState)
    val cameras = Webcams.findAll(liveMeeting.webcams).length

    val effectiveCap = (allowance, meetingCap) match {
      case (None, m)    => m
      case (Some(a), 0) => a
      case (Some(a), m) => math.min(a, m)
    }

    // Attributed to the server only when it is strictly stricter than the
    // meeting's own cap - on a tie the meeting's limit is the actionable one.
    val serverIsBinding = allowance.exists(a => (meetingCap == 0 || a < meetingCap) && cameras >= a)

    if (GlobalCameraCapState.recordPersisted(capState, effectiveCap, serverIsBinding)) {
      MeetingDAO.updateCameraCapState(meetingId, effectiveCap, serverIsBinding)
    }
  }

  private def toCandidate(stream: WebcamStream): CameraCandidate = {
    val user = Users2x.findWithIntId(liveMeeting.users2x, stream.userId)

    CameraCandidate(
      streamId = stream.streamId,
      userId = stream.userId,
      startedAt = stream.startedAt,
      isContent = stream.showAsContent || stream.contentType != "camera",
      isPresenter = user.exists(_.presenter),
      isPinned = user.exists(_.pin),
      hasFloor = VoiceUsers.findWithIntId(liveMeeting.voiceUsers, stream.userId).exists(_.floor)
    )
  }
}
