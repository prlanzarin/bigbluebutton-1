# Dynamic, server-wide camera sharing cap

## The gap this closes

A BigBlueButton server is a monolith. Load balancers spread meetings across
servers, but nothing bounds what a single server ends up carrying, and every
camera control BigBlueButton has today is scoped to one meeting:

| Control | Scope |
|---|---|
| `meetingCameraCap` | one meeting's publishers |
| `userCameraCap` | one user's publishers |
| camera pagination / `paginationThresholds` | one meeting's *subscriptions* |
| `cameraQualityThresholds` | one meeting's encoding profiles |

Forty small meetings can each be individually compliant and still saturate the
box together, and a meeting that grows to a hundred participants is throttled by
rules that never look outside itself. This adds the missing axis: a cap on the
number of camera publishers **across every meeting on the server**, which reacts
to how loaded the server actually is.

Viewing is out of scope. Pagination keeps owning what a client subscribes to;
this owns what gets published in the first place.

## Shape

A single arbiter actor in `akka-bbb-apps`, `GlobalCameraCapActor`, created as a
child of `BigBlueButtonActor` — the only place in the system that sees every
meeting.

```
MeetingActor ──(camera count, InternalEventBus "GlobalCameraCapChannel")──┐
                                                                          ▼
BigBlueButtonActor ──(meeting gone)──────────────────────► GlobalCameraCapActor
                                                            │  samples host CPU/memory
                                                            │  derives the server budget
                                                            │  water-fills per meeting
MeetingActor ◄──(allowance, InternalEventBus topic=meetingId)┘
   │
   ├─ stores the allowance in its own state
   ├─ stops excess publishers (CameraHdlrHelpers.requestBroadcastedCamEjection)
   ├─ notifies the affected users and the meeting's moderators
   └─ MeetingDAO.updateCameraCapState → v_meeting → the client's camera button
```

Every mutation stays inside the MeetingActor that owns the state, so the
single-writer invariant holds; the arbiter only ever computes and publishes
numbers.

### Why akka and not bbb-webrtc-sfu

With `cameraBridge=livekit` the client publishes straight to LiveKit and the
SFU's video module is not in the path, so it has no camera-admission chokepoint.
akka owns meeting state, the client notification path, the configuration and —
already — the server-side ejection primitive. **bbb-webrtc-sfu is unchanged**,
and because `CamBroadcastStopSysMsg` is handled by both `lib/video/video.js`
(mediasoup) and `lib/livekit/livekit-controller.js` (LiveKit, server-side
unpublish), mediasoup support comes for free.

## The budget

```
budget = min( globalCameraCap.max            # admin ceiling, 0 = none
            , tightest /create globalCameraCap over live meetings
            , ceiling implied by current host load )
```

`None` at every position means "no ceiling"; the tightest one wins, so adding an
axis can only ever make the server stricter.

**Host load** is sampled in-JVM: CPU from
`com.sun.management.OperatingSystemMXBean.getCpuLoad`, memory from
`/proc/meminfo`'s `MemAvailable` — *not* the MXBean's free memory, which on Linux
counts reclaimable page cache as used and would report a nearly full machine on
an idle server. Neither axis has a fallback: a reading the JVM cannot take is
reported as absent, and the last known load stays in force. Substituting a
different metric — the load average for CPU, free memory for available — would
peg a freshly restarted or perfectly healthy server at its tightest ceiling.
Samples are EWMA-smoothed, and only the interval tick advances the average:
camera transitions re-run the allocation too, and folding a sample in on each one
would collapse the smoothing exactly when cameras are churning most.

Because BigBlueButton is a monolith, the host figures already include
mediasoup, LiveKit and FreeSWITCH — which is the point. A containerised or split
deployment would read the wrong machine; that is one reason the feature ships
off.

## Fairness

Max-min fair share (water filling) over the meetings' current publisher counts,
with a floor:

1. Reserve `minPerMeeting` for every publishing meeting.
2. Water-fill the surplus across **tenants** — a meeting and its breakouts count
   as one, so a class running 16 breakouts does not draw 17 shares.
3. Water-fill each tenant's share across its own meetings.
4. Integer remainders go to the smallest demands first, so the allocation is
   stable across evaluations. A churning allocation is a flapping camera.

Meetings at or below the fair share keep every camera however many meetings
exist; only the meetings above it are trimmed, and the furthest above loses the
most. When the floors alone do not fit the budget, the budget is shared out over
the floors themselves, still max-min fair — nothing is guaranteed any more, so
nothing pretends to be.

`minPerMeeting` protects a **publishing** meeting's existing cameras. It does
not reserve capacity for a meeting that may never share one: on a full server an
idle meeting is allowed 0, because the only place a reservation could come from
is somebody else's live camera.

While the server has spare capacity nobody is given a number at all — every
meeting is simply unconstrained. Dividing the spare capacity up instead would
make each meeting's cap depend on every other meeting's instantaneous camera
count, so a single camera toggle anywhere would rewrite every meeting's row and
push a subscription update to every client on the server.

**Known limitation.** Floors are per meeting while the fair share is per tenant,
and the two pull against each other. A class running 16 breakout rooms reserves
16 floors, so under heavy pressure it can hold more of the budget than a single
large meeting that is contributing the same load. Reserving per tenant instead
would starve the breakouts, which are real rooms with real people in them; the
per-meeting floor is the lesser evil, but the asymmetry is real.

### What actually triggers a trim

Admission control alone never trims. While the budget holds, a meeting at its
share simply cannot add another camera, so the server settles at the cap without
anyone losing a stream. Cameras are stopped only when the **budget drops** under
streams that are already live — load rising past a threshold, an admin lowering
the ceiling, or a new meeting asking for a stricter cap. That is the intended
shape: no gratuitous camera stops.

### Which camera goes

Content streams, the presenter, pinned cameras and the current voice floor are
kept as long as the allowance permits — losing those changes what the meeting is
*about*. Beyond that the newest camera goes first: its absence surprises the
fewest people, and it stops a burst of late joiners from displacing people
already on screen.

Ejection is a round trip (akka → SFU → akka), so the meeting tracks the streams
it has told to stop and does not count them twice on the next evaluation. A stop
the SFU never acknowledges is retried rather than forgotten — otherwise that
stream would stay exempt from the cap for the rest of the meeting while still
occupying a slot.

## Transitions

- **Tightening is immediate.** The server is already under pressure; waiting
  makes it worse.
- **Loosening waits out `releaseDelay`** (default 30 s) of the laxer budget
  holding continuously. A load dip does not hand back cameras that the next tick
  takes away again. The load input is already EWMA-smoothed, so this is a single
  hold timer rather than a second set of thresholds.
- **Cameras are never restarted automatically.** Re-publishing someone's camera
  without them asking is a privacy problem. On lift they get the button back and
  a notification saying so.
- **The lift notification tracks the server budget, not the meeting's own
  allowance.** Trimming a meeting frees a slot inside its own share, so its
  allowance rises immediately afterwards even though the server has recovered
  nothing — announcing that as "sharing is available again" would invite the user
  straight back into the trim.

## What the user sees

Two server-managed columns on `meeting`, reaching the client through the
existing `v_meeting` subscription:

| Column | Meaning |
|---|---|
| `effectiveCameraCap` | the meeting's publisher allowance: the stricter of its own `meetingCameraCap` and its share of the server's budget, under the usual `0 = unlimited` convention |
| `globalCameraCapActive` | the server-wide cap is refusing a new camera in this meeting *right now* |

Both are computed server-side, and deliberately so. A viewer's `user_camera` rows
are row-filtered under `webcamsOnlyForModerator`, so a client counting streams to
decide whether the cap is reached would under-count and leave the button enabled
on a full server. `globalCameraCapActive` is set only when the server-wide cap is
strictly stricter than the meeting's own — on a tie the meeting's limit is the
more actionable explanation.

The camera button is disabled with a distinct message —
`app.video.globalCamCapReached`, "Camera sharing is limited: the server is at
capacity" — so a blocked user is not told their *meeting* hit a limit it did not
hit. Users whose cameras are stopped get a toast, and the meeting's moderators
get a summary, so cameras never vanish without an explanation.

## Configuration

`bbb-apps-akka.conf`, `globalCameraCap` (see the packaged
`application.conf` for the annotated defaults):

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `false` | master switch |
| `max` | `0` | server-wide publisher ceiling; 0 = only the load thresholds apply |
| `minPerMeeting` | `1` | cameras a publishing meeting keeps under pressure |
| `evaluationInterval` | `5 seconds` | how often the cap is recomputed |
| `releaseDelay` | `30 seconds` | how long a relaxed budget must hold before it applies |
| `loadSmoothingFactor` | `0.3` | EWMA weight of each CPU/memory sample |
| `allowCreateOverride` | `false` | whether `/create` may lower the server's cap |
| `loadThresholds` | two entries | `{cpu, memory, max}`; lowest applicable `max` wins |

### The `/create` parameter

`globalCameraCap` (integer, 0 = no opinion) lets a meeting ask for a **stricter**
server-wide cap. It is a minimum across live meetings, never a maximum, so there
is no value an API caller can pass to escape the cap. The mirror-image abuse —
a caller tightening the server to deny everyone's camera — is why it is ignored
unless `allowCreateOverride` is on.

It is deliberately not a `bigbluebutton.properties` key: the server ceiling
lives in akka's configuration, and a second settable default in bbb-web would
look authoritative without being it.

Breakouts do not inherit it (bbb-web's breakout create path enumerates its own
parameters), which is harmless: they contribute nothing to a minimum, and their
cameras are still counted under their parent's tenancy.

### `meetingCameraCap` is kept

It is not redundant. It expresses a *meeting owner's* intent — "this class never
needs more than six cameras" — which is orthogonal to server pressure. The two
compose as a minimum, and the composition is done server-side so the client
still reads a single number.

## Deliberate non-goals

- **Viewing/subscriptions.** Pagination already owns that axis.
- **Restarting cameras on lift.** See above.
- **A media-server-reported load signal.** More accurate than host CPU, but it
  needs a new SFU→akka telemetry path and a second failure mode; on a monolith
  the host figure already contains that load. Worth revisiting if the host
  signal proves too coarse in practice.
- **Reserving capacity for meetings that are not publishing.** It can only be
  taken from a live camera.
