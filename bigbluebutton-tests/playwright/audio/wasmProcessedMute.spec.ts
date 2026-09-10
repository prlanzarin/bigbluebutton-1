import { expect, type Page as PlaywrightPage } from '@playwright/test';

import { elements as e } from '../core/elements';
import { isLiveKit } from '../core/livekit';
import { ClientSettingsOverrides } from '../core/page';
import { test } from '../core/setup/fixtures';
import { AudioProcessingMode, audioProcessingModeOverrides } from '../options/audioProcessingMode';
import { getLocalMicState } from './liveKitProbe';
import { connectMicrophone } from './util';

const WEB_AUDIO_TRACK_LABEL = 'MediaStreamAudioDestinationNode';

// Advanced filtering, plus an unpublish-after-mute window long enough that the
// mute/unmute round trips below cannot leave it - the regression is invisible
// once the track is unpublished.
const clientSettings = (): ClientSettingsOverrides => {
  const base = audioProcessingModeOverrides(true, 'advanced');
  const media = (base.public as ClientSettingsOverrides).media as ClientSettingsOverrides;

  return {
    ...base,
    public: {
      ...(base.public as ClientSettingsOverrides),
      media: {
        ...media,
        livekit: { audio: { unpublishOnMute: true, unpublishAfterMuteMs: 30000 } },
      },
    },
  };
};

// Advanced filtering publishes a track taken from a MediaStreamAudioDestinationNode.
// Chromium invents a synthetic "WebAudio-<uuid>" deviceId for such tracks while
// Firefox reports an empty getSettings(), which is what makes this regression
// Firefox-only. Reproduce Firefox's behaviour so the CI browser takes the same path.
const reportNoDeviceIdForWebAudioTracks = (page: PlaywrightPage) =>
  page.addInitScript((label: string) => {
    const original = MediaStreamTrack.prototype.getSettings;
    MediaStreamTrack.prototype.getSettings = function getSettings(this: MediaStreamTrack) {
      if (this.label === label) return {};
      return original.call(this);
    };
  }, WEB_AUDIO_TRACK_LABEL);

// The scenario is vacuous unless the injection above is in force.
const webAudioTracksReportNoDeviceId = (page: PlaywrightPage): Promise<boolean> =>
  page.evaluate(() => {
    const context = new AudioContext();
    const [track] = context.createMediaStreamDestination().stream.getAudioTracks();
    const { deviceId } = track.getSettings();
    track.stop();
    context.close().catch(() => {});

    return deviceId === undefined;
  });

test.describe('Audio mute with advanced filtering', { tag: ['@ci', '@media'] }, () => {
  // A WASM-processed microphone is a user-provided track with no capture device
  // behind it. Unmuting inside the unpublish-after-mute window must still resolve
  // it to the current input device, or the publication stays muted - and stays
  // that way, since the unmute also cancels the pending unpublish.
  test('restores the microphone when unmuted inside the unpublish window', async ({
    browser,
    context,
    page,
  }, testInfo) => {
    test.skip(!isLiveKit, 'the unpublish-after-mute window is specific to the LiveKit audio bridge');

    const audioProcessingMode = new AudioProcessingMode(browser, context);
    await page.addInitScript(() => {
      (window as unknown as { BBB_EXPOSE_LIVEKIT_ROOM?: boolean }).BBB_EXPOSE_LIVEKIT_ROOM = true;
    });
    await reportNoDeviceIdForWebAudioTracks(page);
    await audioProcessingMode.trackWasmProcessorRequests(page);
    await audioProcessingMode.initModPage(page, {
      testInfo,
      shouldCloseAudioModal: false,
      clientSettingsOverrides: clientSettings(),
    });

    expect(
      await webAudioTracksReportNoDeviceId(page),
      'web audio tracks should report no deviceId for this scenario to be meaningful',
    ).toBeTruthy();

    await connectMicrophone(audioProcessingMode.modPage);
    expect(
      audioProcessingMode.wasmProcessorWasLoaded(),
      'the WASM processor should have loaded for advanced filtering',
    ).toBeTruthy();

    await audioProcessingMode.modPage.waitAndClick(e.unmuteMicButton);
    await audioProcessingMode.modPage.hasElement(e.muteMicButton, 'should be unmuted after clicking unmute');
    await expect
      .poll(async () => (await getLocalMicState(page)).allMuted, {
        message: 'should publish an unmuted microphone track',
      })
      .toBeFalsy();

    await audioProcessingMode.muteButtonCooldown();
    await audioProcessingMode.modPage.waitAndClick(e.muteMicButton);
    await audioProcessingMode.modPage.hasElement(e.unmuteMicButton, 'should be muted after clicking mute');
    await expect
      .poll(async () => (await getLocalMicState(page)).allMuted, { message: 'should mute the published track' })
      .toBeTruthy();

    await audioProcessingMode.muteButtonCooldown();
    await audioProcessingMode.modPage.waitAndClick(e.unmuteMicButton);
    await audioProcessingMode.modPage.hasElement(e.muteMicButton, 'should be unmuted after clicking unmute again');

    const micState = await getLocalMicState(page);
    expect(micState.micPublications, 'the track should still be published, i.e. inside the unpublish window').toBe(1);
    await expect
      .poll(async () => (await getLocalMicState(page)).allMuted, {
        message: 'should unmute the published track again instead of leaving it muted',
      })
      .toBeFalsy();
  });
});
