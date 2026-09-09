import { type Page as PlaywrightPage } from '@playwright/test';

// Passed into page.evaluate rather than closed over: those callbacks are
// serialized and run in the browser, where this module's scope does not exist.
const LK_NOT_EXPOSED_ERR_MSG = 'window.liveKitRoom is not exposed';

// Reads the page's own LiveKit Room through window.liveKitRoom, which the client
// only publishes when BBB_EXPOSE_LIVEKIT_ROOM (same as other specs like muteReinforcement)

export interface TestPublication {
  source: string;
  isMuted: boolean;
  trackSid?: string;
}

export interface TestRemotePublication extends TestPublication {
  isSubscribed: boolean;
  track?: { mediaStreamTrack?: MediaStreamTrack; receiver?: RTCRtpReceiver };
}

export interface TestRemoteParticipant {
  identity: string;
  audioTrackPublications: Map<string, TestRemotePublication>;
}

export interface TestRoom {
  state: string;
  localParticipant: {
    audioTrackPublications: Map<string, TestPublication>;
    setMicrophoneEnabled: (enabled: boolean) => Promise<unknown>;
    permissions?: { canPublish: boolean; canSubscribe: boolean };
  };
  remoteParticipants: Map<string, TestRemoteParticipant>;
  simulateScenario: (scenario: string) => Promise<void>;
  disconnect: (stopTracks?: boolean) => Promise<void>;
}

export type TestWindow = Window & {
  BBB_EXPOSE_LIVEKIT_ROOM?: boolean;
  liveKitRoom?: TestRoom;
  BBB_TEST_LK_CREDENTIALS?: { url: string; token: string };
};

// Sets the opt-in room expose flag, then wraps Room.connect to capture the (url, token) the
// client connects with. To be used for reconnect tests (see reconnectWithExistingToken).
export const exposeLiveKitRoom = (page: PlaywrightPage): Promise<void> =>
  page.addInitScript(() => {
    const w = window as TestWindow & { bbbLkConnectWrapped?: boolean };

    w.BBB_EXPOSE_LIVEKIT_ROOM = true;

    const wrap = setInterval(() => {
      const room = w.liveKitRoom as (TestRoom & { connect?: unknown }) | undefined;

      if (!room || w.bbbLkConnectWrapped) return;

      w.bbbLkConnectWrapped = true;

      const original = (room.connect as (url: string, token: string, opts?: unknown) => Promise<void>).bind(room);

      (room as { connect: unknown }).connect = (url: string, token: string, opts?: unknown) => {
        w.BBB_TEST_LK_CREDENTIALS = { url, token };
        return original(url, token, opts);
      };

      clearInterval(wrap);
    }, 50);
  });

// Re-enters the LK room with the credentials the client already used.
export const reconnectWithExistingToken = (page: PlaywrightPage): Promise<void> =>
  page.evaluate(async (notExposed) => {
    const w = window as TestWindow;
    const room = w.liveKitRoom as (TestRoom & { connect: (u: string, t: string) => Promise<void> }) | undefined;

    if (!room) throw new Error(notExposed);

    if (!w.BBB_TEST_LK_CREDENTIALS) throw new Error('no LiveKit credentials were captured');

    await room.connect(w.BBB_TEST_LK_CREDENTIALS.url, w.BBB_TEST_LK_CREDENTIALS.token);
  }, LK_NOT_EXPOSED_ERR_MSG);

export interface LocalMicState {
  roomState: string;
  micPublications: number;
  allMuted: boolean;
  canPublish: boolean;
  canSubscribe: boolean;
  trackSids: string[];
}

export const getLocalMicState = (page: PlaywrightPage): Promise<LocalMicState> =>
  page.evaluate((notExposed) => {
    const room = (window as TestWindow).liveKitRoom;

    if (!room) throw new Error(notExposed);

    const pubs = Array.from(room.localParticipant.audioTrackPublications.values()).filter(
      (pub) => pub.source === 'microphone',
    );

    return {
      roomState: room.state,
      micPublications: pubs.length,
      allMuted: pubs.length === 0 || pubs.every((pub) => pub.isMuted),
      canPublish: room.localParticipant.permissions?.canPublish ?? false,
      canSubscribe: room.localParticipant.permissions?.canSubscribe ?? false,
      trackSids: pubs.map((pub) => pub.trackSid).filter((sid): sid is string => !!sid),
    };
  }, LK_NOT_EXPOSED_ERR_MSG);

export interface MicContinuitySample {
  atMs: number;
  trackSids: string[];
  micPublications: number;
  allMuted: boolean;
}

// Samples the local mic publication continuously. The bug this guards against
// is transient: a republish mutes the new track and a corrective unmute may win
// the race. Sampling proves the invariant held throughout, not just at the end.
export const startLocalMicWatch = (page: PlaywrightPage, intervalMs = 100): Promise<void> =>
  page.evaluate((interval) => {
    const w = window as TestWindow & {
      bbbMicWatchSamples?: MicContinuitySample[];
      bbbMicWatchTimer?: ReturnType<typeof setInterval>;
    };

    if (w.bbbMicWatchTimer) clearInterval(w.bbbMicWatchTimer);

    w.bbbMicWatchSamples = [];

    w.bbbMicWatchTimer = setInterval(() => {
      const room = w.liveKitRoom;

      if (!room) return;

      const pubs = Array.from(room.localParticipant.audioTrackPublications.values()).filter(
        (pub) => pub.source === 'microphone',
      );

      w.bbbMicWatchSamples?.push({
        atMs: Date.now(),
        trackSids: pubs.map((pub) => pub.trackSid).filter((sid): sid is string => !!sid),
        micPublications: pubs.length,
        allMuted: pubs.length === 0 || pubs.every((pub) => pub.isMuted),
      });
    }, interval);
  }, intervalMs);

export const stopLocalMicWatch = (page: PlaywrightPage): Promise<MicContinuitySample[]> =>
  page.evaluate(() => {
    const w = window as TestWindow & {
      bbbMicWatchSamples?: MicContinuitySample[];
      bbbMicWatchTimer?: ReturnType<typeof setInterval>;
    };

    if (w.bbbMicWatchTimer) clearInterval(w.bbbMicWatchTimer);
    w.bbbMicWatchTimer = undefined;

    return w.bbbMicWatchSamples ?? [];
  });

// Identities of LK users publishing an unmuted mic track. LiveKit identity == BBB intId for web users.
export const getAudioPublisherIdentities = (page: PlaywrightPage): Promise<string[]> =>
  page.evaluate((notExposed) => {
    const room = (window as TestWindow).liveKitRoom;

    if (!room) throw new Error(notExposed);

    return Array.from(room.remoteParticipants.values())
      .filter((participant) =>
        Array.from(participant.audioTrackPublications.values()).some(
          (pub) => pub.source === 'microphone' && !pub.isMuted,
        ),
      )
      .map((participant) => participant.identity);
  }, LK_NOT_EXPOSED_ERR_MSG);

export interface RemoteAudioState {
  identity: string;
  micPublications: number;
  unmuted: number;
  subscribed: number;
  liveTracks: number;
  packetsReceived: number;
}

// What this page could hear from each remote participant. The distinctions matter:
// remoteParticipants lists everyone whether subscribed or not, audioTrackPublications
// includes unsubscribed publications, and isMuted is only signalled state. Just
// packetsReceived proves audio is arriving.
export const getRemoteAudioStates = (page: PlaywrightPage): Promise<RemoteAudioState[]> =>
  page.evaluate(async (notExposed) => {
    const room = (window as TestWindow).liveKitRoom;

    if (!room) throw new Error(notExposed);

    return Promise.all(
      Array.from(room.remoteParticipants.values()).map(async (participant) => {
        const mics = Array.from(participant.audioTrackPublications.values()).filter(
          (pub) => pub.source === 'microphone',
        );

        let packetsReceived = 0;

        await Promise.all(
          mics.map(async (pub) => {
            const receiver = pub.track?.receiver;

            if (!receiver) return;

            const stats = await receiver.getStats();

            stats.forEach((report: { type: string; packetsReceived?: number }) => {
              if (report.type === 'inbound-rtp') packetsReceived += report.packetsReceived ?? 0;
            });
          }),
        );

        return {
          identity: participant.identity,
          micPublications: mics.length,
          unmuted: mics.filter((pub) => !pub.isMuted).length,
          subscribed: mics.filter((pub) => pub.isSubscribed).length,
          liveTracks: mics.filter((pub) => pub.track?.mediaStreamTrack?.readyState === 'live').length,
          packetsReceived,
        };
      }),
    );
  }, LK_NOT_EXPOSED_ERR_MSG);

// Suppresses client-side LK teardown, which funnels down to Room.disconnect().
// Emulates a tab that never acts on its own removal. False if the room is not exposed.
export const suppressRoomDisconnect = (page: PlaywrightPage): Promise<boolean> =>
  page.evaluate(() => {
    const room = (window as TestWindow).liveKitRoom;

    if (!room) return false;

    room.disconnect = async () => {};

    return true;
  });

// Publishes the mic straight through the LK SDK.
export const republishMicrophone = (page: PlaywrightPage): Promise<void> =>
  page.evaluate(async (notExposed) => {
    const room = (window as TestWindow).liveKitRoom;

    if (!room) throw new Error(notExposed);

    await room.localParticipant.setMicrophoneEnabled(true);
  }, LK_NOT_EXPOSED_ERR_MSG);

// Drops and re-establishes the LK session, making LiveKit emit a fresh
// participant_joined. Uses LK's own simulation mechanism for this (see SDK docs).
export const forceRoomReconnect = (page: PlaywrightPage): Promise<void> =>
  page.evaluate(async (notExposed) => {
    const room = (window as TestWindow).liveKitRoom;

    if (!room) throw new Error(notExposed);

    await room.simulateScenario('full-reconnect');
  }, LK_NOT_EXPOSED_ERR_MSG);

type PeerWindow = Window & { bbbPeerConnections?: RTCPeerConnection[] };

// Keeps every RTCPeerConnection the page builds, so a spec can reach the
// media path the SDK never exposes. Must be installed before the page loads.
export const exposePeerConnections = (page: PlaywrightPage): Promise<void> =>
  page.addInitScript(() => {
    const w = window as PeerWindow;
    const registry: RTCPeerConnection[] = [];
    w.bbbPeerConnections = registry;
    window.RTCPeerConnection = new Proxy(window.RTCPeerConnection, {
      construct(target, args, newTarget) {
        const pc = Reflect.construct(target, args, newTarget) as RTCPeerConnection;
        registry.push(pc);
        return pc;
      },
    });
  });

// Closes the peer connection(s) the page receives on (LiveKit's subscriber,
// the side with recvonly transceivers) under the SDK, with no signalling:
// the SDK notices nothing until the server's ICE failure timeout (~15 s)
// forces a reconnect. Returns how many were closed.
export const killSubscriberMedia = (page: PlaywrightPage): Promise<number> =>
  page.evaluate(() => {
    const w = window as PeerWindow;
    if (!w.bbbPeerConnections) throw new Error('RTCPeerConnection is not wrapped - the test must opt in before load');
    const subscribers = w.bbbPeerConnections.filter(
      (pc) => pc.connectionState !== 'closed' && pc.getTransceivers().some((t) => t.currentDirection === 'recvonly'),
    );
    subscribers.forEach((pc) => pc.close());
    return subscribers.length;
  });

export interface CameraResubscription {
  sid: string;
  // livekit-client wraps every subscribe in a new track object; the receiver
  // (and its MediaStreamTrack, which the bridge's watch is keyed on) comes
  // back the same only when the server reused the transceiver. Compared by
  // identity: the track id is derived from the sid either way.
  sameSdkTrack: boolean;
  sameReceiver: boolean;
  sameMediaStreamTrack: boolean;
  sameTrackId: boolean;
}

// Unsubscribes and re-subscribes the owner's camera on the same peer
// connection, and reports what came back.
export const resubscribeRemoteCamera = (
  page: PlaywrightPage,
  ownerUserId: string,
  timeoutMs: number,
): Promise<CameraResubscription> =>
  page.evaluate(
    async ({ owner, limit }) => {
      type Pub = TestRemotePublication & {
        trackName: string;
        setSubscribed: (subscribed: boolean) => void;
      };
      type Room = {
        remoteParticipants: Map<string, { videoTrackPublications: Map<string, Pub> }>;
        once: (event: string, cb: (...args: unknown[]) => void) => void;
      };
      const room = (window as TestWindow).liveKitRoom as unknown as Room | undefined;
      if (!room) throw new Error('window.liveKitRoom is not exposed');
      let pub: Pub | undefined;
      room.remoteParticipants.forEach((participant) =>
        participant.videoTrackPublications.forEach((candidate) => {
          if (candidate.source === 'camera' && candidate.trackName.startsWith(owner)) pub = candidate;
        }),
      );
      if (!pub || !pub.track) throw new Error('the owner has no subscribed camera');
      const before = pub.track;
      const beforeMediaTrack = before.mediaStreamTrack;
      const beforeReceiver = before.receiver;
      const sid = pub.trackSid ?? '';
      const on = (event: string) =>
        new Promise<void>((resolve, reject) => {
          const timer = setTimeout(() => reject(new Error(`${event} did not follow within ${limit} ms`)), limit);
          room.once(event, (_track: unknown, publication: unknown) => {
            if ((publication as { trackSid?: string }).trackSid !== sid) return;
            clearTimeout(timer);
            resolve();
          });
        });
      const unsubscribed = on('trackUnsubscribed');
      pub.setSubscribed(false);
      await unsubscribed;
      const subscribed = on('trackSubscribed');
      pub.setSubscribed(true);
      await subscribed;
      const after = pub.track;
      return {
        sid,
        sameSdkTrack: after === before,
        sameReceiver: !!after?.receiver && after.receiver === beforeReceiver,
        sameMediaStreamTrack: !!after?.mediaStreamTrack && after.mediaStreamTrack === beforeMediaTrack,
        sameTrackId: after?.mediaStreamTrack?.id === beforeMediaTrack?.id,
      };
    },
    { owner: ownerUserId, limit: timeoutMs },
  );
