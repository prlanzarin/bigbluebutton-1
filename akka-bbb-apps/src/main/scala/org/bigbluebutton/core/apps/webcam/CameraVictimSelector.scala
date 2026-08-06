package org.bigbluebutton.core.apps.webcam

/**
 * A published camera and the properties that decide whether it survives a trim.
 * Flattened out of the meeting models on purpose so the ordering rules can be
 * reasoned about (and exercised) without a LiveMeeting.
 */
case class CameraCandidate(
    streamId:    String,
    userId:      String,
    startedAt:   Long,
    isContent:   Boolean,
    isPresenter: Boolean,
    isPinned:    Boolean,
    hasFloor:    Boolean
)

/**
 * Chooses which cameras a meeting gives up when its allowance shrinks.
 *
 * Content streams, the presenter, pinned cameras and the current voice floor are
 * kept for as long as the allowance permits - losing those changes what the
 * meeting is about, while losing an ordinary camera does not. Beyond that the
 * newest camera goes first: its absence surprises the fewest people, and it
 * keeps a burst of late joiners from displacing the people already on screen.
 */
object CameraVictimSelector {

  private def keepRank(c: CameraCandidate): Int =
    if (c.isContent) 0
    else if (c.isPresenter) 1
    else if (c.isPinned) 2
    else if (c.hasFloor) 3
    else 4

  /** Streams to stop, in the order they should be stopped. */
  def selectVictims(candidates: Vector[CameraCandidate], allowance: Int): Vector[CameraCandidate] = {
    if (allowance < 0 || candidates.size <= allowance) return Vector.empty

    val bySurvival = candidates.sortBy(c => (keepRank(c), c.startedAt, c.streamId))
    bySurvival.drop(allowance).reverse
  }
}
