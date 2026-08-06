package org.bigbluebutton.core

import org.apache.pekko.actor.{ Actor, ActorLogging, Props }
import org.bigbluebutton.SystemConfiguration
import org.bigbluebutton.core.api._
import org.bigbluebutton.core.apps.webcam._
import org.bigbluebutton.core.bus.{ BigBlueButtonEvent, InternalEventBus }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object GlobalCameraCapActor {
  /** InternalEventBus topic the meetings report their camera counts on. */
  val Channel = "GlobalCameraCapChannel"

  /**
   * Child name under BigBlueButtonActor. MeetingActors are children named after
   * their internal meeting id, so this must not look like one.
   */
  val Name = "global-camera-cap"

  def props(eventBus: InternalEventBus): Props =
    Props(classOf[GlobalCameraCapActor], eventBus)
}

/**
 * Server-wide camera arbiter.
 *
 * Meetings report what they publish; this actor turns configuration and host
 * load into a budget, splits it fairly, and pushes each meeting its allowance.
 * It never touches meeting state itself - every mutation stays inside the
 * owning MeetingActor, so the single-writer invariant holds.
 */
class GlobalCameraCapActor(val eventBus: InternalEventBus)
  extends Actor with ActorLogging with SystemConfiguration {

  private case class MeetingDemand(parentMeetingId: String, cameras: Int, requestedCap: Int, reportedAt: Long)

  private object EvaluateInternalMsg

  private var demands = Map.empty[String, MeetingDemand]
  private var published = Map.empty[String, Option[Int]]
  private var smoothedLoad: Option[ServerLoad] = None
  private var gate = ReleaseGate.Initial

  override def preStart(): Unit = {
    eventBus.subscribe(self, GlobalCameraCapActor.Channel)

    val interval = globalCameraCap.evaluationInterval.millis
    context.system.scheduler.scheduleWithFixedDelay(interval, interval, self, EvaluateInternalMsg)

    log.info(
      "Global camera cap active. max={} minPerMeeting={} loadThresholds={} allowCreateOverride={}",
      globalCameraCap.max,
      globalCameraCap.minPerMeeting,
      globalCameraCap.loadThresholds.size,
      globalCameraCap.allowCreateOverride
    )
  }

  override def postStop(): Unit = {
    eventBus.unsubscribe(self, GlobalCameraCapActor.Channel)
  }

  def receive = {
    case msg: MeetingCameraCountInternalMsg => handleMeetingCameraCount(msg)
    case msg: MeetingGoneInternalMsg        => handleMeetingGone(msg)
    case EvaluateInternalMsg                => evaluate(sampleLoad = true)
    case _                                  => // do nothing
  }

  private def handleMeetingCameraCount(msg: MeetingCameraCountInternalMsg): Unit = {
    val now = System.currentTimeMillis()
    val previous = demands.get(msg.meetingId)
    val demand = MeetingDemand(msg.parentMeetingId, msg.cameras, msg.requestedCap, now)
    demands += msg.meetingId -> demand

    if (!previous.exists(p => p.cameras == demand.cameras && p.requestedCap == demand.requestedCap)) {
      // Re-split immediately: waiting for the next tick would leave a meeting
      // over its share for up to a full evaluation interval.
      evaluate(sampleLoad = false)
    }
  }

  private def handleMeetingGone(msg: MeetingGoneInternalMsg): Unit = {
    if (demands.contains(msg.meetingId)) {
      demands -= msg.meetingId
      published -= msg.meetingId
      evaluate(sampleLoad = false)
    }
  }

  /**
   * @param sampleLoad only the interval tick advances the load average. Camera
   *                   transitions also re-run the allocation, and folding a sample
   *                   into the average on each one would collapse the smoothing
   *                   exactly when cameras are churning most.
   */
  private def evaluate(sampleLoad: Boolean): Unit = {
    val now = System.currentTimeMillis()
    dropStaleDemands(now)

    if (sampleLoad) {
      smoothedLoad = ServerLoadSampler
        .sample()
        .map(s => LoadPolicy.smooth(smoothedLoad, s, globalCameraCap.loadSmoothingFactor))
        .orElse(smoothedLoad)
    }

    val loadCeiling = smoothedLoad.flatMap(LoadPolicy.loadCeiling(_, globalCameraCap.loadThresholds))
    val proposed = LoadPolicy.budget(globalCameraCap.max, requestedCap(), loadCeiling)
    val (nextGate, effective) = gate.next(proposed, now, globalCameraCap.releaseDelay)

    if (gate.applied != nextGate.applied) {
      log.info(
        "Global camera budget changed. from={} to={} cpu={} memory={}",
        gate.applied.map(_.toString).getOrElse("unlimited"),
        nextGate.applied.map(_.toString).getOrElse("unlimited"),
        smoothedLoad.map(_.cpu).getOrElse(-1d),
        smoothedLoad.map(_.memory).getOrElse(-1d)
      )
    }
    val budgetRelaxed = isLaxer(effective, gate.applied)
    gate = nextGate

    val allocation = GlobalCameraCapAllocator.allocate(
      demands.map { case (id, d) => CameraCapDemand(id, groupOf(id, d), d.cameras) }.toVector,
      effective,
      globalCameraCap.minPerMeeting
    )

    // On a relaxation every meeting is told, not just the ones whose number moved:
    // a meeting trimmed earlier may land on the same allowance it already had, and
    // it is exactly the one owing its users a "sharing is available again".
    val changed =
      if (budgetRelaxed) allocation
      else allocation.filterNot { case (meetingId, allowance) => published.get(meetingId).contains(allowance) }

    if (changed.nonEmpty) {
      log.info(
        "Global camera allowances updated. budget={} meetings={} cameras={} changed={}",
        effective.map(_.toString).getOrElse("unlimited"),
        demands.size,
        demands.values.map(_.cameras).sum,
        changed.map { case (id, a) => s"$id=${a.map(_.toString).getOrElse("unlimited")}" }.mkString(",")
      )

      changed.foreach {
        case (meetingId, allowance) =>
          published += meetingId -> allowance
          eventBus.publish(BigBlueButtonEvent(
            meetingId,
            SetGlobalCameraAllowanceInternalMsg(meetingId, allowance, budgetRelaxed)
          ))
      }
    }

    published = published.filter { case (id, _) => demands.contains(id) }
  }

  /**
   * Backstop for a meeting that stopped reporting without being announced gone -
   * a stuck demand would hold part of the budget hostage for the server's lifetime.
   * Live meetings report on every housekeeping tick, so silence is conclusive.
   */
  private def dropStaleDemands(now: Long): Unit = {
    val maxSilence = math.max(3 * globalCameraCap.evaluationInterval, 20000L)
    val stale = demands.filter { case (_, d) => now - d.reportedAt > maxSilence }.keys

    if (stale.nonEmpty) {
      log.warning("Dropping camera demands with no report in {}ms. meetings={}", maxSilence, stale.mkString(","))
      stale.foreach { meetingId =>
        demands -= meetingId
        published -= meetingId
      }
    }
  }

  /** Tightest cap any live meeting asked for through the /create parameter. */
  private def requestedCap(): Option[Int] = {
    if (!globalCameraCap.allowCreateOverride) None
    else {
      val requested = demands.values.map(_.requestedCap).filter(_ > 0)
      if (requested.isEmpty) None else Some(requested.min)
    }
  }

  private def isLaxer(candidate: Option[Int], previous: Option[Int]): Boolean = (candidate, previous) match {
    case (None, Some(_))    => true
    case (Some(c), Some(p)) => c > p
    case _                  => false
  }

  /** A meeting and its breakouts are one tenant of the server. */
  private def groupOf(meetingId: String, demand: MeetingDemand): String =
    if (demand.parentMeetingId.nonEmpty) demand.parentMeetingId else meetingId
}
