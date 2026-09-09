import { writeFileSync } from 'node:fs';

import { expect } from '@playwright/test';

import { ELEMENT_WAIT_EXTRA_LONG_TIME, ELEMENT_WAIT_LONGER_TIME, ELEMENT_WAIT_TIME } from '../core/constants';
import { elements as e } from '../core/elements';
import { isLiveKit } from '../core/livekit';
import { test } from '../core/setup/fixtures';
import { startScreenshare } from '../screenshare/util';
import { getLocalMicState, getRemoteAudioStates, killSubscriberMedia, resubscribeRemoteCamera } from './liveKitProbe';
import {
  AUDIO_SAMPLE_WINDOW,
  expectMicUiMatchesPublication,
  expectViewerAudioFlowing,
  getLiveCameraIdentities,
  initReconnectionScenario,
  RECONNECT_WAIT_TIME,
  type ReconnectionFixture,
  simulateRoomScenario,
  waitForRoomReconnected,
} from './liveKitReconnection';
import {
  expectRemoteCameraRendering,
  expectScreenshareRendering,
  getLiveKitRoomName,
  getRemoteCameraTile,
  getRemotePublications,
  liveKitServerLogAvailable,
  makeViewerPresenter,
  readLiveKitServerLog,
  republishMicrophoneByMuteCycle,
  restartScreenshare,
  restartWebcam,
  waitForLiveKitServerLogLine,
  waitForNewPublicationSid,
  watchRemoteCameraRecovery,
} from './liveKitResubscription';
import { ensureUnmuted } from './util';

// livekit-server's own wait for a subscriber answer, plus slack for the
// close it then issues to be logged.
const NEGOTIATION_FAILED_WAIT = 15_000 + 5_000;
// bbb-webrtc-sfu holds a web user's camera stop for livekit.camStopGraceMs
// (15 s); a camera checked before that may merely be not yet stopped.
const SFU_CAM_STOP_GRACE_MS = 15_000;
const CAM_STOP_GRACE_SETTLE = SFU_CAM_STOP_GRACE_MS + ELEMENT_WAIT_TIME;
// A dead media path is only noticed when the server's ICE failure timeout
// (~15 s) forces a reconnect; the tile must not wait for that.
const PLACEHOLDER_WAIT = ELEMENT_WAIT_TIME;
// Up to one frame period may already have passed when the track dies, plus
// the bridge's own reaction.
const DEAD_VIDEO_TOLERANCE = 1_000;
// The placeholder is dropped on loadeddata; the frame follows within a paint
// or two, not after a keyframe wait.
const EMPTY_VIDEO_TOLERANCE = 300;
// HTMLMediaElement.HAVE_CURRENT_DATA; the runner has no DOM to read it from.
const HAVE_CURRENT_DATA = 2;

// Withholds one of the viewer's SDP answers (the moderator's camera start is
// the renegotiation that produces it) and waits for the server to act.
// Answers pass again as soon as one is withheld: the server sends no
// further offer while it waits and the client never resends. Resolves
// once the viewer's room is back.
const forceServerReconnect = async (fixture: ReconnectionFixture, roomName: string, serverLog: boolean) => {
  const { modPage, viewerPage, viewerUserId } = fixture;
  const armedAt = Date.now();

  fixture.dropLiveKitAnswers();
  const oneAnswerWithheld = expect(async () => {
    expect(fixture.droppedAnswerCount(), 'the viewer should have withheld an answer').toBeGreaterThan(0);
  })
    .toPass({ timeout: ELEMENT_WAIT_EXTRA_LONG_TIME })
    .then(() => fixture.passLiveKitAnswers());
  await modPage.shareWebcam();
  await oneAnswerWithheld;

  if (serverLog) {
    await waitForLiveKitServerLogLine(
      armedAt,
      roomName,
      ['participant closing', 'NEGOTIATE_FAILED', viewerUserId],
      'the server should close the viewer over the unanswered offer',
      NEGOTIATION_FAILED_WAIT + ELEMENT_WAIT_LONGER_TIME,
    );
  } else {
    await expect(async () => {
      expect((await getLocalMicState(viewerPage.page)).roomState, 'the server should force the viewer off').not.toBe(
        'connected',
      );
    }).toPass({ timeout: NEGOTIATION_FAILED_WAIT + ELEMENT_WAIT_LONGER_TIME });
  }
  await waitForRoomReconnected(viewerPage.page);
};

const modAudioPacketsAtViewer = async (fixture: ReconnectionFixture): Promise<number> =>
  (await getRemoteAudioStates(fixture.viewerPage.page)).find((state) => state.identity === fixture.modUserId)
    ?.packetsReceived ?? 0;

const expectModeratorAudioAtViewer = async (fixture: ReconnectionFixture, message: string): Promise<void> => {
  await expect(async () => {
    const before = await modAudioPacketsAtViewer(fixture);
    await fixture.viewerPage.page.waitForTimeout(AUDIO_SAMPLE_WINDOW);
    expect(await modAudioPacketsAtViewer(fixture), message).toBeGreaterThan(before);
  }).toPass({ timeout: ELEMENT_WAIT_EXTRA_LONG_TIME });
};

// Whether a viewer gets its subscriptions back after a LiveKit session
// interruption, including media republished under new sids while it
// could not hear about them.
test.describe('LiveKit resubscription', { tag: ['@long-running', '@media'] }, () => {
  let current: ReconnectionFixture | undefined;
  let startedAt = 0;

  test.beforeEach(() => {
    test.skip(!isLiveKit, 'resubscription paths under test are specific to the LiveKit bridge');
    current = undefined;
    startedAt = Date.now();
  });

  // Both consoles and the server's own account of the room, so a failure can
  // be read without re-running it.
  // eslint-disable-next-line no-empty-pattern
  test.afterEach(async ({}, testInfo) => {
    if (!current || testInfo.status === testInfo.expectedStatus) return;
    const attachText = async (name: string, body: string) => {
      const path = testInfo.outputPath(name);
      writeFileSync(path, body);
      await testInfo.attach(name, { path, contentType: 'text/plain' });
    };
    await attachText('viewer-console.log', current.viewerLogLines.join('\n'));
    await attachText('moderator-console.log', current.modLogLines.join('\n'));
    const roomName = await getLiveKitRoomName(current.modPage.page).catch(() => '');
    if (liveKitServerLogAvailable(roomName)) {
      await attachText('livekit-server.log', readLiveKitServerLog(startedAt, roomName).join('\n'));
    }
  });

  test('a full reconnect restores the remote camera a viewer was watching', async ({ browser }, testInfo) => {
    const fixture = await initReconnectionScenario(browser, testInfo, { modWebcam: true });
    current = fixture;
    const { viewerPage, modUserId } = fixture;

    const before = await expectRemoteCameraRendering(viewerPage.page, modUserId, 'before the reconnect');
    const [beforePub] = await getRemotePublications(viewerPage.page, modUserId, 'camera');

    await simulateRoomScenario(viewerPage.page, 'full-reconnect');
    await waitForRoomReconnected(viewerPage.page);

    const after = await expectRemoteCameraRendering(viewerPage.page, modUserId, 'after the reconnect');
    expect(after.stream, 'the same BBB stream should be rendered').toBe(before.stream);
    const [afterPub] = await getRemotePublications(viewerPage.page, modUserId, 'camera');
    expect(afterPub?.sid, 'the publisher did not republish, so the sid is unchanged').toBe(beforePub?.sid);
    expect(afterPub?.isSubscribed, 'the camera should be subscribed again').toBe(true);
  });

  test('a remote camera whose media dies shows the placeholder until it is back', async ({ browser }, testInfo) => {
    const fixture = await initReconnectionScenario(browser, testInfo, { modWebcam: true });
    current = fixture;
    const { viewerPage, modUserId } = fixture;

    await expectRemoteCameraRendering(viewerPage.page, modUserId, 'before the media loss');
    // Re-subscribe first so the media dies on a track the bridge received
    // from a swap, not on the one it first attached.
    const resubscription = await resubscribeRemoteCamera(viewerPage.page, modUserId, ELEMENT_WAIT_LONGER_TIME);
    expect(resubscription.sameSdkTrack, 'the re-subscribe should hand the bridge a new SDK track').toBe(false);
    await expectRemoteCameraRendering(viewerPage.page, modUserId, 'after the re-subscribe');
    const watch = watchRemoteCameraRecovery(viewerPage.page, modUserId, RECONNECT_WAIT_TIME);
    // Awaited below; an earlier failure must not add its teardown rejection.
    watch.catch(() => undefined);
    const killed = await killSubscriberMedia(viewerPage.page);
    expect(killed, 'the viewer should have had a subscriber peer connection to lose').toBeGreaterThan(0);
    // Positive control: the media really died under the SDK.
    await expect(async () => {
      const [camera] = await getRemotePublications(viewerPage.page, modUserId, 'camera');
      expect(camera?.trackLive, 'the camera track should have ended').toBe(false);
    }).toPass({ timeout: ELEMENT_WAIT_TIME });

    const recovery = await watch;
    writeFileSync(
      testInfo.outputPath('camera-recovery.json'),
      JSON.stringify({ resubscription, ...recovery }, null, 2),
    );
    const trace = JSON.stringify(recovery.trace);
    expect(
      recovery.placeholderAfterMs,
      `the tile should show the placeholder after the media loss ${trace}`,
    ).not.toBeNull();
    expect(
      recovery.placeholderAfterMs ?? Number.POSITIVE_INFINITY,
      `the placeholder should show before the server forces the reconnect ${trace}`,
    ).toBeLessThanOrEqual(PLACEHOLDER_WAIT);
    await expectRemoteCameraRendering(viewerPage.page, modUserId, 'after the recovery');
    expect(recovery.firstFrameAfterMs, `frames should flow again ${trace}`).not.toBeNull();
    // Not a timing: at the instant the placeholder goes, the element must
    // already have a picture to show in its place.
    expect(
      recovery.readyStateAtPlaceholderGone ?? -1,
      `the placeholder should only go once the element has a frame ${trace}`,
    ).toBeGreaterThanOrEqual(HAVE_CURRENT_DATA);
    expect
      .soft(recovery.deadVideoMs, `a dead track should not be shown as video ${trace}`)
      .toBeLessThanOrEqual(DEAD_VIDEO_TOLERANCE);
    expect
      .soft(recovery.emptyVideoMs, `the placeholder should hold until the first frame ${trace}`)
      .toBeLessThanOrEqual(EMPTY_VIDEO_TOLERANCE);
  });

  test('a camera republished during a signal outage is shown after the resume', async ({ browser }, testInfo) => {
    test.setTimeout(ELEMENT_WAIT_EXTRA_LONG_TIME * 8);
    const fixture = await initReconnectionScenario(browser, testInfo, { modWebcam: true });
    current = fixture;
    const { modPage, viewerPage, modUserId } = fixture;

    await expectRemoteCameraRendering(viewerPage.page, modUserId, 'before the outage');
    const [oldPub] = await getRemotePublications(viewerPage.page, modUserId, 'camera');
    expect(oldPub?.sid, 'the viewer should hold the camera publication').toBeTruthy();

    // The viewer's signal socket is frozen: the republish below happens while
    // it cannot receive participant updates, so its view of the publisher
    // goes stale. The socket is then closed so the SDK resumes.
    fixture.stallLiveKitSignal();
    await restartWebcam(modPage);
    await fixture.dropLiveKitSignal();
    await waitForRoomReconnected(viewerPage.page);

    const newSid = await waitForNewPublicationSid(
      viewerPage.page,
      modUserId,
      'camera',
      oldPub?.sid,
      'the viewer should learn the republished camera',
    );
    expect(newSid).not.toBe(oldPub?.sid);
    await expectRemoteCameraRendering(viewerPage.page, modUserId, 'after the resume');
    const stale = (await getRemotePublications(viewerPage.page, modUserId, 'camera')).filter(
      (pub) => pub.sid === oldPub?.sid,
    );
    expect(stale, 'the viewer should not keep the pre-outage publication').toHaveLength(0);
  });

  test('a microphone republished during a signal outage is heard after the resume', async ({ browser }, testInfo) => {
    test.setTimeout(ELEMENT_WAIT_EXTRA_LONG_TIME * 8);
    const fixture = await initReconnectionScenario(browser, testInfo);
    current = fixture;
    const { modPage, viewerPage, modUserId } = fixture;

    await ensureUnmuted(modPage);
    await expectModeratorAudioAtViewer(fixture, 'the viewer should hear the moderator before the outage');
    const [oldPub] = await getRemotePublications(viewerPage.page, modUserId, 'microphone');
    expect(oldPub?.sid, 'the viewer should hold the microphone publication').toBeTruthy();

    fixture.stallLiveKitSignal();
    await republishMicrophoneByMuteCycle(modPage);
    await fixture.dropLiveKitSignal();
    await waitForRoomReconnected(viewerPage.page);

    const newSid = await waitForNewPublicationSid(
      viewerPage.page,
      modUserId,
      'microphone',
      oldPub?.sid,
      'the viewer should learn the republished microphone',
    );
    expect(newSid).not.toBe(oldPub?.sid);
    await expectModeratorAudioAtViewer(fixture, 'the viewer should hear the moderator after the resume');
    const stale = (await getRemotePublications(viewerPage.page, modUserId, 'microphone')).filter(
      (pub) => pub.sid === oldPub?.sid,
    );
    expect(stale, 'the viewer should not keep the pre-outage publication').toHaveLength(0);
  });

  test('a screenshare republished during a signal outage is shown after the resume', async ({ browser }, testInfo) => {
    test.setTimeout(ELEMENT_WAIT_EXTRA_LONG_TIME * 8);
    const fixture = await initReconnectionScenario(browser, testInfo);
    current = fixture;
    const { modPage, viewerPage, modUserId } = fixture;

    await startScreenshare(modPage);
    await expectScreenshareRendering(viewerPage.page, 'before the outage');
    const [oldPub] = await getRemotePublications(viewerPage.page, modUserId, 'screen_share');
    expect(oldPub?.sid, 'the viewer should hold the screenshare publication').toBeTruthy();

    // Unlike a camera, a screenshare's BBB stream id is its LiveKit sid, so
    // the republish also replaces the record the viewer's UI is driven by.
    fixture.stallLiveKitSignal();
    await restartScreenshare(modPage);
    await fixture.dropLiveKitSignal();
    await waitForRoomReconnected(viewerPage.page);

    await waitForNewPublicationSid(
      viewerPage.page,
      modUserId,
      'screen_share',
      oldPub?.sid,
      'the viewer should learn the republished screenshare',
    );
    await expectScreenshareRendering(viewerPage.page, 'after the resume');
  });

  test('a server-forced reconnect keeps what the user was publishing', async ({ browser }, testInfo) => {
    test.setTimeout(ELEMENT_WAIT_EXTRA_LONG_TIME * 10);
    const fixture = await initReconnectionScenario(browser, testInfo, { webcam: true });
    current = fixture;
    const { modPage, viewerPage, viewerUserId, modUserId, viewerLogLines } = fixture;
    const roomName = await getLiveKitRoomName(viewerPage.page);
    const serverLog = liveKitServerLogAvailable(roomName);

    await expectViewerAudioFlowing(fixture);
    await expect(async () => {
      expect(await getLiveCameraIdentities(modPage.page), 'the moderator should see the viewer camera').toContain(
        viewerUserId,
      );
    }).toPass({ timeout: ELEMENT_WAIT_LONGER_TIME });

    const logMark = viewerLogLines.length;
    await forceServerReconnect(fixture, roomName, serverLog);
    await viewerPage.page.waitForTimeout(CAM_STOP_GRACE_SETTLE);

    // The three things the user had, and the tile they were watching.
    await expect
      .soft(async () => {
        await expectViewerAudioFlowing(fixture);
      })
      .toPass({ timeout: RECONNECT_WAIT_TIME });
    await expect
      .soft(async () => {
        expect(
          await getLiveCameraIdentities(modPage.page),
          'the moderator should still see the viewer camera after the forced reconnect',
        ).toContain(viewerUserId);
        expect(await viewerPage.checkElement(e.leaveVideo), 'the viewer should still be sharing their webcam').toBe(
          true,
        );
        await expectRemoteCameraRendering(modPage.page, viewerUserId, 'after the forced reconnect');
      })
      .toPass({ timeout: RECONNECT_WAIT_TIME });
    await expect
      .soft(async () => {
        await expectRemoteCameraRendering(viewerPage.page, modUserId, 'after the forced reconnect');
      })
      .toPass({ timeout: RECONNECT_WAIT_TIME });
    expect
      .soft(
        await getRemoteCameraTile(viewerPage.page, modUserId),
        'the moderator camera tile should be rendered at the viewer',
      )
      .toBeDefined();
    // The client had every fact needed to say why its session was reset; a
    // reconnect the server asked for must leave a server-shipped trace.
    const explained = viewerLogLines
      .slice(logMark)
      .some((line) => /leave request|leave_request|STATE_MISMATCH/i.test(line));
    expect.soft(explained, 'the client should log the server-requested reconnect').toBe(true);
  });

  // Quarantined: a voice rejoin the SFU sees before the mic republish is
  // recorded muted, and the client honours that mute once the reconnect
  // outlives its server-mute ignore window, unpublishing the track it just
  // republished. Retire the tag once the window is anchored to the republish.
  test(
    'two server-forced reconnects in a row keep the microphone',
    { tag: '@flaky' },
    async ({ browser }, testInfo) => {
      test.setTimeout(ELEMENT_WAIT_EXTRA_LONG_TIME * 14);
      const fixture = await initReconnectionScenario(browser, testInfo);
      current = fixture;
      const { modPage, viewerPage, viewerUserId } = fixture;
      const roomName = await getLiveKitRoomName(viewerPage.page);
      const serverLog = liveKitServerLogAvailable(roomName);

      await expectViewerAudioFlowing(fixture);

      // Answers stay withheld past the first reset: the next session's first
      // subscriber offer goes unanswered too, a client on a bad network closed
      // twice running.
      const armedAt = Date.now();
      fixture.dropLiveKitAnswers();
      await modPage.shareWebcam();
      await expect(async () => {
        expect(fixture.droppedAnswerCount(), 'the viewer should have withheld two answers').toBeGreaterThan(1);
      }).toPass({ timeout: NEGOTIATION_FAILED_WAIT + ELEMENT_WAIT_LONGER_TIME });
      fixture.passLiveKitAnswers();

      if (serverLog) {
        await expect(async () => {
          const closes = readLiveKitServerLog(armedAt, roomName).filter(
            (line) => line.includes('participant closing') && line.includes(viewerUserId),
          );
          expect(closes.length, 'the server should have closed the viewer twice').toBeGreaterThan(1);
        }).toPass({
          timeout: NEGOTIATION_FAILED_WAIT * 2 + ELEMENT_WAIT_LONGER_TIME,
          intervals: [ELEMENT_WAIT_TIME / 5],
        });
      }
      await waitForRoomReconnected(viewerPage.page);
      await viewerPage.page.waitForTimeout(ELEMENT_WAIT_LONGER_TIME);

      await expect
        .soft(async () => {
          await expectViewerAudioFlowing(fixture);
        })
        .toPass({ timeout: RECONNECT_WAIT_TIME });
      await expectMicUiMatchesPublication(fixture);
    },
  );

  test('a server-forced reconnect keeps the screenshare the user was presenting', async ({ browser }, testInfo) => {
    test.setTimeout(ELEMENT_WAIT_EXTRA_LONG_TIME * 10);
    const fixture = await initReconnectionScenario(browser, testInfo);
    current = fixture;
    const { modPage, viewerPage } = fixture;
    const roomName = await getLiveKitRoomName(viewerPage.page);
    const serverLog = liveKitServerLogAvailable(roomName);

    await makeViewerPresenter(modPage, viewerPage);
    await startScreenshare(viewerPage);
    await expectScreenshareRendering(modPage.page, 'before the forced reconnect');

    await forceServerReconnect(fixture, roomName, serverLog);
    // The share goes through stopped/started on the new sid; let that land.
    await viewerPage.page.waitForTimeout(ELEMENT_WAIT_LONGER_TIME);

    await expect
      .soft(async () => {
        expect(
          await viewerPage.checkElement(e.stopScreenSharing),
          'the presenter should still be sharing their screen',
        ).toBe(true);
      })
      .toPass({ timeout: RECONNECT_WAIT_TIME });
    await expect
      .soft(async () => {
        await expectScreenshareRendering(modPage.page, 'after the forced reconnect');
      })
      .toPass({ timeout: RECONNECT_WAIT_TIME });
  });
});
