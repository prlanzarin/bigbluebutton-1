package org.bigbluebutton.core.apps.webcam

/**
 * How many cameras a meeting currently publishes, and which tenant it belongs
 * to. `groupId` is the parent meeting for a breakout and the meeting itself
 * otherwise: a room and its breakouts are one tenant of the server, so a class
 * running 16 breakouts does not draw 17 shares of the budget.
 */
case class CameraCapDemand(meetingId: String, groupId: String, cameras: Int)

/**
 * Splits a server-wide camera budget across meetings by max-min fair share.
 *
 * Meetings whose demand is at or below the fair share keep every camera however
 * many meetings there are; only the meetings above the share are trimmed, and
 * the furthest above loses the most. That is what makes the cap fair to small
 * meetings while hitting the ones actually driving the load.
 */
object GlobalCameraCapAllocator {

  /** No numeric allowance applies - the meeting may publish freely. */
  type Allowance = Option[Int]

  /**
   * @param budget total cameras allowed on the server, or None for unlimited
   * @param floor  cameras a publishing meeting keeps regardless of pressure
   * @return per-meeting allowance; None means unconstrained
   */
  def allocate(
      demands: Vector[CameraCapDemand],
      budget:  Option[Int],
      floor:   Int
  ): Map[String, Allowance] = budget match {
    case None             => demands.map(d => d.meetingId -> (None: Allowance)).toMap
    case Some(b) if b < 0 => demands.map(d => d.meetingId -> (None: Allowance)).toMap

    case Some(b) =>
      val total = demands.map(_.cameras).sum

      if (total < b) {
        // Spare capacity leaves everyone unconstrained rather than being divided
        // up: whoever shares next takes it, and that share re-runs this
        // allocation, withdrawing the offer from everyone at once. Publishing a
        // number here instead would make every meeting's cap depend on every
        // other meeting's instantaneous camera count - one click anywhere would
        // rewrite every meeting's row.
        demands.map(d => d.meetingId -> (None: Allowance)).toMap
      } else {
        // At or over budget. A meeting publishing nothing gets nothing: the floor
        // protects a meeting's existing cameras, it does not reserve capacity for
        // one that may never share - that could only come out of someone else's.
        val (active, idle) = demands.partition(_.cameras > 0)
        idle.map(d => d.meetingId -> (Some(0): Allowance)).toMap ++ allocateActive(active, b, floor)
      }
  }

  private def allocateActive(
      active: Vector[CameraCapDemand],
      budget: Int,
      floor:  Int
  ): Map[String, Allowance] = {
    if (active.isEmpty) return Map.empty

    val reserved = active.map(d => d.meetingId -> math.min(d.cameras, floor)).toMap
    val totalReserved = reserved.values.sum

    if (totalReserved > budget) {
      // The floors alone do not fit, so nothing is guaranteed any more. Sharing
      // the budget out over the floors keeps it max-min fair instead of letting
      // the first meetings take it all. At exact equality this branch is skipped:
      // the floors fit, and the demand-aware path below is the better answer.
      waterFill(reserved, budget).map { case (id, n) => id -> (Some(n): Allowance) }
    } else {
      val surplus = budget - totalReserved
      val residualByMeeting = active.map(d => d.meetingId -> (d.cameras - reserved(d.meetingId))).toMap
      val groupOf = active.map(d => d.meetingId -> d.groupId).toMap
      val residualByGroup = residualByMeeting.toVector
        .groupBy { case (id, _) => groupOf(id) }
        .map { case (g, members) => g -> members.map(_._2).sum }

      val groupExtra = waterFill(residualByGroup, surplus)

      val memberExtra = residualByMeeting.toVector
        .groupBy { case (id, _) => groupOf(id) }
        .flatMap { case (g, members) => waterFill(members.toMap, groupExtra.getOrElse(g, 0)) }

      active.map { d =>
        d.meetingId -> (Some(reserved(d.meetingId) + memberExtra.getOrElse(d.meetingId, 0)): Allowance)
      }.toMap
    }
  }

  /**
   * Max-min fair share: hand every claimant an equal slice, give back whatever
   * the ones below the slice do not need, and repeat over the rest.
   */
  private def waterFill(demands: Map[String, Int], budget: Int): Map[String, Int] = {
    if (budget <= 0 || demands.isEmpty) return demands.map { case (k, _) => k -> 0 }

    var remaining = budget
    var active = demands.filter { case (_, d) => d > 0 }
    val settled = scala.collection.mutable.Map.empty[String, Int]
    demands.foreach { case (k, d) => if (d <= 0) settled(k) = 0 }

    var done = false
    while (!done) {
      if (active.isEmpty || remaining <= 0) {
        active.foreach { case (k, _) => settled(k) = 0 }
        done = true
      } else {
        val share = remaining / active.size
        val (fits, exceeds) = active.partition { case (_, d) => d <= share }

        if (fits.nonEmpty) {
          fits.foreach { case (k, d) => settled(k) = d }
          remaining -= fits.values.sum
          active = exceeds
        } else {
          // Everyone is above the share: each takes `share`, and the integer
          // remainder goes to the smallest demands first so the result is stable
          // across evaluations (a churning allocation is a flapping camera).
          val ordered = active.toVector.sortBy { case (k, d) => (d, k) }
          val leftover = remaining - (share * active.size)
          ordered.zipWithIndex.foreach {
            case ((k, _), idx) => settled(k) = share + (if (idx < leftover) 1 else 0)
          }
          done = true
        }
      }
    }

    settled.toMap
  }

}
