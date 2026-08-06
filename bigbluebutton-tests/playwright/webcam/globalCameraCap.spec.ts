import { test } from '../core/setup/fixtures';
import { constants as c } from '../parameters/constants';
import { GlobalCameraCap } from './globalCameraCap';

// The cap lives in bbb-apps-akka.conf, which no create parameter can switch on, so
// every case here needs globalCameraCap.enabled and .allowCreateOverride set on the
// server under test - hence @setting-required. The create parameter then narrows the
// budget to 2 for the scenario.
//
// Serial, and to be run with --workers=1: the budget under test is the whole
// server's, so two of these running at once would contend for the same two camera
// slots and each would see the other's publishers.
// https://docs.bigbluebutton.org/4.0/testing/release-testing/#server-wide-camera-cap-automated
test.describe.serial('Global camera cap', { tag: ['@ci', '@media'] }, () => {
  test.beforeEach(async () => {
    await GlobalCameraCap.clearSuiteMeetings();
  });

  test(
    'Refuses a share past the server-wide allowance',
    { tag: '@setting-required:globalCameraCap' },
    async ({ browser, context, page }, testInfo) => {
      const cap = new GlobalCameraCap(browser, context);
      await cap.initModPage(page, { ...GlobalCameraCap.modPageOptions(c.globalCameraCapTwo), testInfo });
      try {
        await cap.blocksShareBeyondAllowance();
      } finally {
        await cap.endMeetings();
      }
    },
  );

  test(
    'Trims the meeting over its fair share and leaves the lighter one alone',
    { tag: '@setting-required:globalCameraCap' },
    async ({ browser, context, page }, testInfo) => {
      const cap = new GlobalCameraCap(browser, context);
      await cap.initModPage(page, { ...GlobalCameraCap.modPageOptions(c.globalCameraCapThree), testInfo });
      try {
        await cap.trimsTheHeavierMeetingFirst();
      } finally {
        await cap.endMeetings();
      }
    },
  );

  test(
    'Restores camera sharing once capacity returns',
    { tag: '@setting-required:globalCameraCap' },
    async ({ browser, context, page }, testInfo) => {
      const cap = new GlobalCameraCap(browser, context);
      await cap.initModPage(page, { ...GlobalCameraCap.modPageOptions(c.globalCameraCapTwo), testInfo });
      try {
        await cap.restoresSharingWhenCapacityReturns();
      } finally {
        await cap.endMeetings();
      }
    },
  );
});
