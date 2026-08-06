package org.bigbluebutton.core.models

/**
 * A meeting's slice of the server-wide camera cap.
 *
 * The allowance is pushed here by GlobalCameraCapActor; the meeting itself owns
 * every write, so no other actor ever touches this state.
 */
class GlobalCameraCapState {
  private var allowance: Option[Int] = None
  // Streams told to stop but not yet gone: ejection is a round trip through the
  // SFU, so the webcam model still lists them for a few hundred milliseconds.
  private var pendingEjections: Map[String, Long] = Map.empty
  private var trimmedUsers: Set[String] = Set.empty
  private var persisted: Option[(Int, Boolean)] = None
}

object GlobalCameraCapState {

  def getAllowance(state: GlobalCameraCapState): Option[Int] = state.allowance

  def isConstrained(state: GlobalCameraCapState): Boolean = state.allowance.isDefined

  /** @return true when the value actually moved */
  def setAllowance(state: GlobalCameraCapState, allowance: Option[Int]): Boolean = {
    val changed = state.allowance != allowance
    state.allowance = allowance
    changed
  }

  def markPendingEjection(state: GlobalCameraCapState, streamId: String, now: Long): Unit =
    state.pendingEjections += streamId -> now

  def clearPendingEjection(state: GlobalCameraCapState, streamId: String): Unit =
    state.pendingEjections -= streamId

  def isPendingEjection(state: GlobalCameraCapState, streamId: String): Boolean =
    state.pendingEjections.contains(streamId)

  /**
   * Forgets ejections the SFU never acknowledged. Without this a stream whose stop
   * was lost - a bridge mismatch, an SFU restart - stays exempt from the cap for
   * the rest of the meeting while still consuming a slot.
   */
  def expirePendingEjections(state: GlobalCameraCapState, now: Long, timeout: Long): Set[String] = {
    val (expired, live) = state.pendingEjections.partition { case (_, at) => now - at > timeout }
    state.pendingEjections = live
    expired.keySet
  }

  /** @return true when the value differs from what was last written */
  def recordPersisted(state: GlobalCameraCapState, cap: Int, globalActive: Boolean): Boolean = {
    val changed = !state.persisted.contains((cap, globalActive))
    state.persisted = Some((cap, globalActive))
    changed
  }

  def markTrimmed(state: GlobalCameraCapState, userId: String): Unit =
    state.trimmedUsers += userId

  /** The users trimmed since the cap last engaged, cleared as they are told it lifted. */
  def takeTrimmedUsers(state: GlobalCameraCapState): Set[String] = {
    val trimmed = state.trimmedUsers
    state.trimmedUsers = Set.empty
    trimmed
  }
}
