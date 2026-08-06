package org.bigbluebutton.core.apps.webcam

import org.bigbluebutton.core.models.CameraCapLoadThreshold

/** Host-wide CPU and memory utilisation, both fractions in [0,1]. */
case class ServerLoad(cpu: Double, memory: Double)

/**
 * Turns configuration and host load into the server's camera budget.
 *
 * `None` everywhere means "no ceiling"; the tighter of the two axes wins, so
 * adding an axis can only ever make the server stricter.
 */
object LoadPolicy {

  /**
   * Exponentially weighted moving average. Load spikes are noisy and a raw
   * sample would move the cap on every tick; smoothing is what makes the
   * release timer in [[ReleaseGate]] meaningful rather than a coin flip.
   */
  def smooth(previous: Option[ServerLoad], sample: ServerLoad, alpha: Double): ServerLoad =
    previous match {
      case None => sample
      case Some(prev) => ServerLoad(
        cpu = (alpha * sample.cpu) + ((1 - alpha) * prev.cpu),
        memory = (alpha * sample.memory) + ((1 - alpha) * prev.memory)
      )
    }

  /** Lowest ceiling among the thresholds either measurement has reached. */
  def loadCeiling(load: ServerLoad, thresholds: Vector[CameraCapLoadThreshold]): Option[Int] = {
    val applicable = thresholds.filter(t => load.cpu >= t.cpu || load.memory >= t.memory)
    if (applicable.isEmpty) None else Some(applicable.map(_.max).min)
  }

  /**
   * @param configuredMax  admin ceiling; 0 means the admin sets no ceiling
   * @param requestedMax   tightest value asked for through the /create parameter
   * @param loadCeiling    ceiling implied by the current host load
   */
  def budget(configuredMax: Int, requestedMax: Option[Int], loadCeiling: Option[Int]): Option[Int] = {
    val configured = if (configuredMax > 0) Some(configuredMax) else None
    Vector(configured, requestedMax.filter(_ > 0), loadCeiling).flatten match {
      case Vector() => None
      case ceilings => Some(ceilings.min)
    }
  }
}

/**
 * Hysteresis around budget changes.
 *
 * Tightening is applied on the spot - the server is already under pressure and
 * waiting makes it worse. Loosening waits out `releaseDelay`, so a load dip does
 * not hand cameras back that the next tick takes away again.
 */
case class ReleaseGate(applied: Option[Int], pending: Option[(Option[Int], Long)]) {

  /** True when `candidate` allows strictly fewer cameras than `applied`. */
  private def isTighter(candidate: Option[Int]): Boolean = (candidate, applied) match {
    case (Some(_), None)    => true
    case (Some(c), Some(a)) => c < a
    case (None, _)          => false
  }

  private def isSame(candidate: Option[Int]): Boolean = candidate == applied

  /**
   * @return the new gate state and the budget that is actually in force
   */
  def next(proposed: Option[Int], now: Long, releaseDelay: Long): (ReleaseGate, Option[Int]) = {
    if (isTighter(proposed)) {
      val gate = ReleaseGate(proposed, None)
      (gate, proposed)
    } else if (isSame(proposed)) {
      (ReleaseGate(applied, None), applied)
    } else {
      // The clock restarts whenever the candidate changes, so a bigger loosening
      // cannot inherit the dwell time a smaller one earned.
      val since = pending.collect { case (candidate, at) if candidate == proposed => at }.getOrElse(now)

      if (now - since >= releaseDelay) {
        (ReleaseGate(proposed, None), proposed)
      } else {
        (ReleaseGate(applied, Some((proposed, since))), applied)
      }
    }
  }
}

object ReleaseGate {
  /** Nothing applied yet: the server starts unconstrained. */
  val Initial: ReleaseGate = ReleaseGate(None, None)
}
