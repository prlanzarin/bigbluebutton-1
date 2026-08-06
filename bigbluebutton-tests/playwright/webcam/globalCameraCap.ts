import { elements as e } from '../core/elements';
import { apiCall, createMeeting, getRandomInt } from '../core/helpers';
import { Page } from '../core/page';
import { parameters } from '../core/parameters';
import { constants as c } from '../parameters/constants';
import { MultiUsers } from '../user/multiusers';

const CAP_TRANSITION_TIMEOUT = 30_000;
// Loosening is deliberately delayed by globalCameraCap.releaseDelay (30s by default)
// so a load dip cannot make cameras flap, so anything waiting on a lift needs to
// outlast it.
const CAP_RELEASE_TIMEOUT = 60_000;

// Meetings are named apart from the suite's usual `random-*` so the cleanup below
// can end this spec's leftovers without touching a meeting another spec is running.
const MEETING_PREFIX = 'globalcamcap';

const suiteMeetingId = () => `${MEETING_PREFIX}-${getRandomInt(1000000, 10000000)}`;

interface SuiteMeetingsResponse {
  response?: { meetings?: Array<{ meeting?: Array<{ meetingID?: string[] }> }> };
}

interface OpenPageOptions {
  isModerator?: boolean;
  fullName: string;
  createParameter?: string;
}

// The cap is server-wide, so a scenario needing a second, independent meeting cannot
// go through MultiUsers' page slots - they all join modPage's meeting. Passing a
// createParameter instead of a meetingId is what mints a fresh one (core/page.ts).
export class GlobalCameraCap extends MultiUsers {
  private createdMeetingIds: string[] = [];

  static modPageOptions(createParameter: string) {
    return { createParameter, customMeetingId: suiteMeetingId() };
  }

  private async openPage({ isModerator = false, fullName, createParameter }: OpenPageOptions): Promise<Page> {
    const page = new Page(this.browser, await this.context.newPage(), this.modPage.testInfo);
    await page.init(isModerator, {
      fullName,
      meetingId: createParameter ? undefined : this.modPage.meetingId,
      customMeetingId: createParameter ? suiteMeetingId() : undefined,
      createParameter,
    });
    if (createParameter) this.createdMeetingIds.push(page.meetingId);
    return page;
  }

  /**
   * Waits for the server to allow a share before clicking. Tests run back to back
   * and a budget the previous one lowered only recovers after releaseDelay, so
   * sharing immediately would race that timer rather than test anything.
   */
  private static async shareWhenAllowed(page: Page) {
    await page.hasElementEnabled(e.joinVideo, 'should allow sharing before the cap engages', CAP_RELEASE_TIMEOUT);
    await page.shareWebcam({ shouldConfirmSharing: true });
  }

  /**
   * Closing the browser contexts is not enough here: a meeting outlives its last
   * user by `meetingExpireWhenLastUserLeftInMinutes`, and while it lives its
   * requested cap still narrows the server budget for the next test.
   */
  async endMeetings() {
    const meetingIds = [this.modPage.meetingId, ...this.createdMeetingIds].filter(Boolean);
    await Promise.all(meetingIds.map((meetingID) => apiCall('end', { meetingID, password: parameters.moderatorPW! })));
  }

  /** Ends leftovers from an earlier run of this spec, and nothing else. */
  static async clearSuiteMeetings() {
    const response = await apiCall<SuiteMeetingsResponse>('getMeetings');
    const meetings = response.data?.response?.meetings?.[0]?.meeting ?? [];
    const staleIds = meetings
      .map((meeting) => meeting.meetingID?.[0])
      .filter((meetingID): meetingID is string => !!meetingID && meetingID.startsWith(`${MEETING_PREFIX}-`));

    await Promise.all(staleIds.map((meetingID) => apiCall('end', { meetingID, password: parameters.moderatorPW! })));
  }

  /** The camera button is disabled, and blames the server rather than the meeting. */
  private static async expectBlockedByServer(page: Page) {
    await page.hasElementDisabled(
      e.joinVideo,
      'should disable the camera button once the server-wide cap is reached',
      CAP_TRANSITION_TIMEOUT,
    );
    await page.hasText(
      e.joinVideo,
      /server is at capacity/i,
      'should attribute the block to the server, not to the meeting limit',
    );
  }

  async blocksShareBeyondAllowance() {
    // Budget is 2: the moderator and one attendee fill it, and the next user is
    // refused outright rather than allowed and then trimmed.
    await GlobalCameraCap.shareWhenAllowed(this.modPage);

    const publisher = await this.openPage({ fullName: 'Publisher' });
    await GlobalCameraCap.shareWhenAllowed(publisher);

    const blocked = await this.openPage({ fullName: 'Blocked' });
    await GlobalCameraCap.expectBlockedByServer(blocked);
  }

  async trimsTheHeavierMeetingFirst() {
    // Both publishers in the heavy meeting are plain viewers, so neither is
    // protected by rank and the trim order is decided purely by which camera
    // started last. The moderator deliberately does not share: as presenter it
    // would outrank both and mask the rule under test.
    const older = await this.openPage({ fullName: 'OlderPublisher' });
    await GlobalCameraCap.shareWhenAllowed(older);

    const newer = await this.openPage({ fullName: 'NewerPublisher' });
    await GlobalCameraCap.shareWhenAllowed(newer);

    const lightModerator = await this.openPage({
      isModerator: true,
      fullName: 'LightModerator',
      createParameter: c.globalCameraCapThree,
    });
    await GlobalCameraCap.shareWhenAllowed(lightModerator);

    await newer.closeAllToastNotifications();
    await this.modPage.closeAllToastNotifications();

    // Admission control alone never trims: while the budget holds, a meeting at its
    // share simply cannot add another camera. Trimming is what happens when the
    // budget DROPS under cameras that are already live, so the scenario has to move
    // it - here with a meeting asking for a stricter server-wide cap, which is
    // exactly what the lower-only create parameter is for. It is created through the
    // API rather than a browser page: a page init would burn ten seconds, by which
    // time the toasts asserted below have auto-closed.
    const tightenerMeetingId = suiteMeetingId();
    this.createdMeetingIds.push(await createMeeting(c.globalCameraCapTwo, tightenerMeetingId));

    // The toasts are the most perishable signals, so they are asserted first.
    await newer.hasText(
      e.smallToastMsg,
      e.globalCameraCapEnforcedToast,
      'should tell the user their camera was stopped by the server',
      CAP_TRANSITION_TIMEOUT,
    );
    await this.modPage.hasText(
      e.smallToastMsg,
      e.globalCameraCapEnforcedModeratorToast,
      'should tell the moderators of the trimmed meeting that the server stopped cameras',
      CAP_TRANSITION_TIMEOUT,
    );

    await newer.hasElement(
      e.joinVideo,
      'should stop the most recently started camera in the meeting over its share',
      CAP_TRANSITION_TIMEOUT,
    );
    await newer.wasRemoved(e.webcamMirroredVideoContainer, 'should tear down the stopped camera');

    await older.hasElement(e.leaveVideo, 'should keep the camera that was sharing first');
    await lightModerator.hasElement(e.leaveVideo, 'should leave the meeting within its share untouched');

    // Ending the meeting that tightened the budget lifts it again. Only users who
    // actually lost a camera are told, so this is the one path that exercises the
    // lift notification.
    await newer.closeAllToastNotifications();
    await apiCall('end', { meetingID: tightenerMeetingId, password: parameters.moderatorPW! });

    await newer.hasText(
      e.smallToastMsg,
      e.globalCameraCapLiftedToast,
      'should tell a trimmed user when camera sharing becomes available again',
      CAP_RELEASE_TIMEOUT,
    );
    await newer.hasElementEnabled(
      e.joinVideo,
      'should re-enable camera sharing for the trimmed user once the budget recovers',
      CAP_RELEASE_TIMEOUT,
    );
  }

  async restoresSharingWhenCapacityReturns() {
    await GlobalCameraCap.shareWhenAllowed(this.modPage);

    const publisher = await this.openPage({ fullName: 'Publisher' });
    await GlobalCameraCap.shareWhenAllowed(publisher);

    const blocked = await this.openPage({ fullName: 'Blocked' });
    await GlobalCameraCap.expectBlockedByServer(blocked);

    // Freeing a slot has to reach the blocked user, otherwise the cap is a one-way
    // door for anyone who asked while the server happened to be full.
    await publisher.waitAndClick(e.leaveVideo);

    await blocked.hasElementEnabled(
      e.joinVideo,
      'should re-enable camera sharing once the server has capacity again',
      CAP_TRANSITION_TIMEOUT,
    );
  }
}
