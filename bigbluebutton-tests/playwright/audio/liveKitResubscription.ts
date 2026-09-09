import { execFileSync } from 'node:child_process';

import { expect, type Page as PlaywrightPage } from '@playwright/test';

import { ELEMENT_WAIT_EXTRA_LONG_TIME, ELEMENT_WAIT_LONGER_TIME, ELEMENT_WAIT_TIME } from '../core/constants';
import { elements as e } from '../core/elements';
import { Page } from '../core/page';
import { startScreenshare } from '../screenshare/util';
import { type TestWindow } from './liveKitProbe';
import { UNPUBLISH_SETTLE_TIME } from './liveKitReconnection';

// A flowing video adds frames in this; a black tile adds none.
export const VIDEO_SAMPLE_WINDOW = 1_500;

export interface RemotePublicationState {
  sid: string;
  name: string;
  source: string;
  isSubscribed: boolean;
  trackLive: boolean;
}

export interface RemoteParticipantMediaState {
  identity: string;
  publications: RemotePublicationState[];
}

interface RoomWithPublications {
  name: string;
  remoteParticipants: Map<
    string,
    {
      identity: string;
      trackPublications: Map<
        string,
        {
          trackSid: string;
          trackName: string;
          source: string;
          isSubscribed: boolean;
          track?: { mediaStreamTrack?: { readyState: string } };
        }
      >;
    }
  >;
}

// What the SDK believes each remote participant is publishing, by sid. A sid
// the server no longer knows shows up here as a publication that never
// becomes subscribed.
export const getRemoteMediaState = (page: PlaywrightPage): Promise<RemoteParticipantMediaState[]> =>
  page.evaluate(() => {
    const room = (window as TestWindow).liveKitRoom as unknown as RoomWithPublications | undefined;
    if (!room) return [];
    return Array.from(room.remoteParticipants.values()).map((participant) => ({
      identity: participant.identity,
      publications: Array.from(participant.trackPublications.values()).map((pub) => ({
        sid: pub.trackSid,
        name: pub.trackName,
        source: pub.source,
        isSubscribed: pub.isSubscribed,
        trackLive: pub.track?.mediaStreamTrack?.readyState === 'live',
      })),
    }));
  });

export const getLiveKitRoomName = (page: PlaywrightPage): Promise<string> =>
  page.evaluate(() => ((window as TestWindow).liveKitRoom as unknown as RoomWithPublications | undefined)?.name ?? '');

export const getRemotePublications = async (
  page: PlaywrightPage,
  identity: string,
  source: string,
): Promise<RemotePublicationState[]> =>
  (await getRemoteMediaState(page))
    .find((p) => p.identity === identity)
    ?.publications.filter((pub) => pub.source === source) ?? [];

export interface CameraTileState {
  stream: string;
  local: boolean;
  hasSource: boolean;
  readyState: number;
  currentTime: number;
  frames: number;
}

// The rendered tiles, not the SDK's view of them: a subscription the SDK
// reports as live can still be attached to nothing.
export const getCameraTileStates = (page: PlaywrightPage): Promise<CameraTileState[]> =>
  page.evaluate(
    (selector) =>
      Array.from(document.querySelectorAll<HTMLVideoElement>(selector)).map((video) => ({
        stream: video.closest('[data-stream]')?.getAttribute('data-stream') ?? '',
        local: video.dataset.localStream === 'true',
        hasSource: !!video.srcObject,
        readyState: video.readyState,
        currentTime: video.currentTime,
        frames:
          typeof video.getVideoPlaybackQuality === 'function' ? video.getVideoPlaybackQuality().totalVideoFrames : -1,
      })),
    `${e.webcamContainer}, ${e.webcamMirroredVideoContainer}`,
  );

const remoteTileFor = (tiles: CameraTileState[], ownerUserId: string): CameraTileState | undefined =>
  tiles.find((tile) => !tile.local && tile.stream.startsWith(ownerUserId));

// Frames must keep arriving: a tile with a source and a frozen frame count
// is black.
export const expectRemoteCameraRendering = async (
  page: PlaywrightPage,
  ownerUserId: string,
  message: string,
): Promise<CameraTileState> => {
  let tile: CameraTileState | undefined;
  await expect(async () => {
    const before = remoteTileFor(await getCameraTileStates(page), ownerUserId);
    expect(before, `${message}: the tile should be rendered`).toBeDefined();
    expect(before?.hasSource, `${message}: the tile should have a media source`).toBe(true);
    await page.waitForTimeout(VIDEO_SAMPLE_WINDOW);
    const after = remoteTileFor(await getCameraTileStates(page), ownerUserId);
    expect(after?.frames ?? -1, `${message}: frames should keep arriving`).toBeGreaterThan(before?.frames ?? -1);
    tile = after;
  }).toPass({ timeout: ELEMENT_WAIT_EXTRA_LONG_TIME });

  if (!tile) throw new Error(`${message}: no tile`);

  return tile;
};

export interface CameraRecoveryWatch {
  placeholderAfterMs: number | null;
  placeholderGoneAfterMs: number | null;
  firstFrameAfterMs: number | null;
  // From the last frame presented to the placeholder appearing.
  deadVideoMs: number | null;
  // From the placeholder going to the first frame of the source that came
  // back (zero when that frame was already on screen).
  emptyVideoMs: number | null;
  // What the element had to show at the instant the placeholder went.
  readyStateAtPlaceholderGone: number | null;
  trace: { atMs: number; ev: string }[];
}

// Follows the owner's tile in-page from a media loss to the first frame
// back: frames timed by requestVideoFrameCallback, the placeholder by a
// MutationObserver, so neither depends on the polling tick, which only
// re-hooks the element and decides when to stop.
export const watchRemoteCameraRecovery = (
  page: PlaywrightPage,
  ownerUserId: string,
  timeout: number,
  intervalMs = 200,
): Promise<CameraRecoveryWatch> =>
  page.evaluate(
    async ({ owner, placeholderSelector, itemSelector, limit, interval }) => {
      const start = performance.now();
      const at = () => Math.round(performance.now() - start);
      const trace: { atMs: number; ev: string }[] = [];
      const note = (ev: string) => {
        if (trace.length < 20) trace.push({ atMs: at(), ev });
      };
      let lastFrameAt: number | null = null;
      let placeholderAppearedAt: number | null = null;
      let placeholderGoneAt: number | null = null;
      // The element falls below HAVE_CURRENT_DATA when its source is taken
      // away; any frame after that is the new source's, not a tail of the old.
      let sourceLostAt: number | null = null;
      let readyStateAtPlaceholderGone: number | null = null;
      let firstFrameAfterLossAt: number | null = null;
      let firstFrameAfterGoneAt: number | null = null;
      let framesAfterPlaceholderGone = 0;

      type FrameCallbackVideo = HTMLVideoElement & {
        requestVideoFrameCallback?: (cb: () => void) => number;
      };
      let hooked: HTMLVideoElement | null = null;
      const hook = (video: FrameCallbackVideo) => {
        if (hooked === video || typeof video.requestVideoFrameCallback !== 'function') return;
        hooked = video;
        const onFrame = () => {
          const now = performance.now();
          if (placeholderAppearedAt === null) lastFrameAt = now;
          if (sourceLostAt !== null && firstFrameAfterLossAt === null) {
            firstFrameAfterLossAt = now;
            note('first frame after the source loss');
          }
          if (placeholderGoneAt !== null) {
            if (firstFrameAfterGoneAt === null) firstFrameAfterGoneAt = now;
            framesAfterPlaceholderGone += 1;
          }
          if (hooked === video) video.requestVideoFrameCallback?.(onFrame);
        };
        video.requestVideoFrameCallback(onFrame);
      };

      const findItem = () =>
        Array.from(document.querySelectorAll<HTMLElement>(itemSelector)).find((el) =>
          el.querySelector('[data-stream]')?.getAttribute('data-stream')?.startsWith(owner),
        ) ?? null;
      const readPlaceholder = () => {
        const present = !!findItem()?.querySelector(placeholderSelector);
        if (present && placeholderAppearedAt === null) {
          placeholderAppearedAt = performance.now();
          note('placeholder shown');
        } else if (!present && placeholderAppearedAt !== null && placeholderGoneAt === null) {
          placeholderGoneAt = performance.now();
          readyStateAtPlaceholderGone = findItem()?.querySelector('video')?.readyState ?? -1;
          note(`placeholder gone at readyState ${readyStateAtPlaceholderGone}`);
        }
      };
      const observer = new MutationObserver(readPlaceholder);
      observer.observe(document.body, { childList: true, subtree: true });
      readPlaceholder();

      // Which path told the tile, the SDK's unsubscribe or the bridge's watch.
      const streamId = findItem()?.querySelector('[data-stream]')?.getAttribute('data-stream') ?? '';
      const onStreamState = (event: Event) => note(`bridge: ${(event as CustomEvent).detail?.streamState}`);
      if (streamId) window.addEventListener(`streamStateChanged:${streamId}`, onStreamState);
      const room = (
        window as {
          liveKitRoom?: {
            on: (e: string, cb: (...a: unknown[]) => void) => void;
            off: (e: string, cb: (...a: unknown[]) => void) => void;
          };
        }
      ).liveKitRoom;
      const onUnsubscribed = (...args: unknown[]) =>
        note(`sdk: trackUnsubscribed ${(args[1] as { trackSid?: string })?.trackSid ?? ''}`);
      const onSubscribed = (...args: unknown[]) =>
        note(`sdk: trackSubscribed ${(args[1] as { trackSid?: string })?.trackSid ?? ''}`);
      room?.on('trackUnsubscribed', onUnsubscribed);
      room?.on('trackSubscribed', onSubscribed);

      while (performance.now() - start < limit) {
        const video = findItem()?.querySelector('video') ?? null;
        if (video) {
          hook(video);
          if (
            placeholderAppearedAt !== null &&
            sourceLostAt === null &&
            video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA
          ) {
            sourceLostAt = performance.now();
            note('source lost');
          }
        }
        if (framesAfterPlaceholderGone >= 3) break;
        // eslint-disable-next-line no-await-in-loop
        await new Promise((resolve) => {
          setTimeout(resolve, interval);
        });
      }
      observer.disconnect();
      if (streamId) window.removeEventListener(`streamStateChanged:${streamId}`, onStreamState);
      room?.off('trackUnsubscribed', onUnsubscribed);
      room?.off('trackSubscribed', onSubscribed);
      hooked = null;

      const ms = (later: number | null, earlier: number | null) =>
        later === null || earlier === null ? null : Math.max(0, Math.round(later - earlier));
      const firstFrameBack = firstFrameAfterLossAt ?? firstFrameAfterGoneAt;
      return {
        placeholderAfterMs: ms(placeholderAppearedAt, start),
        placeholderGoneAfterMs: ms(placeholderGoneAt, start),
        firstFrameAfterMs: ms(firstFrameBack, start),
        deadVideoMs: ms(placeholderAppearedAt, lastFrameAt ?? start),
        emptyVideoMs: placeholderGoneAt === null ? null : ms(firstFrameBack, placeholderGoneAt),
        readyStateAtPlaceholderGone,
        trace,
      };
    },
    {
      owner: ownerUserId,
      placeholderSelector: `${e.webcamConnecting}, ${e.webcamConnectingSqueezed}`,
      itemSelector: e.webcamVideoItem,
      limit: timeout,
      interval: intervalMs,
    },
  );

export const getRemoteCameraTile = async (
  page: PlaywrightPage,
  ownerUserId: string,
): Promise<CameraTileState | undefined> => remoteTileFor(await getCameraTileStates(page), ownerUserId);

export interface ScreenshareVideoState {
  hasSource: boolean;
  readyState: number;
  currentTime: number;
  frames: number;
}

export const getScreenshareVideoState = (page: PlaywrightPage): Promise<ScreenshareVideoState | null> =>
  page.evaluate((selector) => {
    const video = document.querySelector<HTMLVideoElement>(selector);
    if (!video) return null;
    return {
      hasSource: !!video.srcObject,
      readyState: video.readyState,
      currentTime: video.currentTime,
      frames:
        typeof video.getVideoPlaybackQuality === 'function' ? video.getVideoPlaybackQuality().totalVideoFrames : -1,
    };
  }, e.screenShareVideo);

export const expectScreenshareRendering = async (page: PlaywrightPage, message: string): Promise<void> => {
  await expect(async () => {
    const before = await getScreenshareVideoState(page);
    expect(before, `${message}: the screenshare video should be rendered`).not.toBeNull();
    expect(before?.hasSource, `${message}: the screenshare video should have a media source`).toBe(true);
    await page.waitForTimeout(VIDEO_SAMPLE_WINDOW);
    const after = await getScreenshareVideoState(page);
    expect(after?.frames ?? -1, `${message}: screenshare frames should keep arriving`).toBeGreaterThan(
      before?.frames ?? -1,
    );
  }).toPass({ timeout: ELEMENT_WAIT_EXTRA_LONG_TIME });
};

export const stopWebcam = async (page: Page): Promise<void> => {
  await page.waitAndClick(e.leaveVideo);
  await page.hasElement(
    e.joinVideo,
    'should offer to share the webcam again once it is stopped',
    ELEMENT_WAIT_LONGER_TIME,
  );
};

// Stop and share again: the BBB stream id is stable per device, so what
// changes is the LiveKit publication sid, which is what the viewers hold.
export const restartWebcam = async (page: Page): Promise<void> => {
  await stopWebcam(page);
  await page.shareWebcam();
};

export const stopScreenshare = async (page: Page): Promise<void> => {
  await page.waitAndClick(e.stopScreenSharing);
  await page.hasElement(
    e.startScreenSharing,
    'should offer to share the screen again once it is stopped',
    ELEMENT_WAIT_LONGER_TIME,
  );
};

export const restartScreenshare = async (page: Page): Promise<void> => {
  await stopScreenshare(page);
  await startScreenshare(page);
};

// The moderator hands the presenter role to the only other user in the list.
export const makeViewerPresenter = async (modPage: Page, viewerPage: Page): Promise<void> => {
  await modPage.waitAndClick(e.usersListSidebarButton);
  await modPage.waitAndClick(e.userListItem);
  await modPage.waitAndClick(e.moreOptionsUserItemButton);
  await modPage.waitAndClick(e.makePresenter);
  await viewerPage.hasElement(
    e.startScreenSharing,
    'the viewer should be offered screen sharing once presenter',
    ELEMENT_WAIT_LONGER_TIME,
  );
};

export const clickMute = async (page: Page): Promise<void> => {
  await page.waitAndClick(e.muteMicButton);
  await page.hasElement(e.unmuteMicButton, 'should show the unmute button after muting');
};

export const clickUnmute = async (page: Page): Promise<void> => {
  await page.waitAndClick(e.unmuteMicButton);
  await page.hasElement(e.muteMicButton, 'should show the mute button after unmuting');
};

// A mute unpublishes the microphone after unpublishAfterMuteMs, so waiting it
// out before unmuting guarantees the unmute publishes under a new sid.
export const republishMicrophoneByMuteCycle = async (page: Page): Promise<void> => {
  await clickMute(page);
  await page.page.waitForTimeout(UNPUBLISH_SETTLE_TIME);
  await clickUnmute(page);
};

export const waitForNewPublicationSid = async (
  page: PlaywrightPage,
  identity: string,
  source: string,
  previousSid: string | undefined,
  message: string,
): Promise<string> => {
  let sid = '';
  await expect(async () => {
    const pubs = await getRemotePublications(page, identity, source);
    const fresh = pubs.find((pub) => pub.sid !== previousSid);
    expect(fresh, message).toBeDefined();
    sid = fresh?.sid ?? '';
  }).toPass({ timeout: ELEMENT_WAIT_EXTRA_LONG_TIME });

  return sid;
};

export const readLiveKitServerLog = (sinceEpochMs: number, roomName: string): string[] => {
  const output = execFileSync(
    'journalctl',
    ['-u', 'livekit-server', '--since', `@${Math.floor(sinceEpochMs / 1000)}`, '--no-pager', '-q', '-o', 'cat'],
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 },
  );
  return output.split('\n').filter((line) => line.includes(roomName));
};

// How far back a room that was just joined must show up in the journal.
const ROOM_LOG_LOOKBACK_MS = 5 * 60_000;

// True only when the server under test logs into this host's journal (the
// room must appear in it); server-side assertions are gated on it, so a
// remote server, or a host that merely runs a livekit-server unit, skips
// them instead of failing them.
export const liveKitServerLogAvailable = (roomName: string): boolean => {
  if (!roomName) return false;
  try {
    return readLiveKitServerLog(Date.now() - ROOM_LOG_LOOKBACK_MS, roomName).length > 0;
  } catch {
    return false;
  }
};

// Resolves with the first line since `sinceEpochMs` that carries every
// needle, so a close can be pinned to one participant, not just the room.
export const waitForLiveKitServerLogLine = async (
  sinceEpochMs: number,
  roomName: string,
  needles: string[],
  message: string,
  timeout = ELEMENT_WAIT_EXTRA_LONG_TIME * 2,
): Promise<string> => {
  let found = '';
  await expect(async () => {
    const line = readLiveKitServerLog(sinceEpochMs, roomName).find((l) => needles.every((n) => l.includes(n)));
    expect(line, message).toBeDefined();
    found = line ?? '';
  }).toPass({ timeout, intervals: [ELEMENT_WAIT_TIME / 5] });

  return found;
};
