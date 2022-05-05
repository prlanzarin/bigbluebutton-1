import BaseAudioBridge from './base';
import logger from '/imports/startup/client/logger';
import {
} from '/imports/utils/fetchStunTurnServers';
import {
  isUnifiedPlan,
  toUnifiedPlan,
  toPlanB,
  stripMDnsCandidates,
} from '/imports/utils/sdpUtils';
import Storage from '/imports/ui/services/storage/session';
import {
  DEFAULT_INPUT_DEVICE_ID,
  DEFAULT_OUTPUT_DEVICE_ID,
} from '/imports/api/audio/client/bridge/service';
import SIPSession from '/imports/api/audio/client/bridge/sip';

const MEDIA = Meteor.settings.public.media;
const IPV4_FALLBACK_DOMAIN = Meteor.settings.public.app.ipv4FallbackDomain;
const BRIDGE_NAME = 'sip';

const INPUT_DEVICE_ID_KEY = 'audioInputDeviceId';
const OUTPUT_DEVICE_ID_KEY = 'audioOutputDeviceId';

export default class SIPBridge extends BaseAudioBridge {
  constructor(userData) {
    super(userData);

    const {
      userId,
      username,
      sessionToken,
    } = userData;

    this.user = {
      userId,
      sessionToken,
      name: username,
    };

    this.media = {
      inputDevice: {},
    };

    this.protocol = window.document.location.protocol;
    if (MEDIA['sip_ws_host'] != null && MEDIA['sip_ws_host'] != '') {
      this.hostname = MEDIA.sip_ws_host;
    } else {
      this.hostname = window.document.location.hostname;
    }

    this.bridgeName = BRIDGE_NAME;

    // SDP conversion utilitary methods to be used inside SIP.js
    window.isUnifiedPlan = isUnifiedPlan;
    window.toUnifiedPlan = toUnifiedPlan;
    window.toPlanB = toPlanB;
    window.stripMDnsCandidates = stripMDnsCandidates;

    // No easy way to expose the client logger to sip.js code so we need to attach it globally
    window.clientLogger = logger;
  }

  get inputDeviceId() {
    const sessionInputDeviceId = Storage.getItem(INPUT_DEVICE_ID_KEY);

    if (sessionInputDeviceId) {
      return sessionInputDeviceId;
    }

    if (this.media.inputDeviceId) {
      return this.media.inputDeviceId;
    }

    if (this.activeSession) {
      return this.activeSession.inputDeviceId;
    }

    return DEFAULT_INPUT_DEVICE_ID;
  }

  set inputDeviceId(deviceId) {
    Storage.setItem(INPUT_DEVICE_ID_KEY, deviceId);
    this.media.inputDeviceId = deviceId;

    if (this.activeSession) {
      this.activeSession.inputDeviceId = deviceId;
    }
  }

  get outputDeviceId() {
    const sessionOutputDeviceId = Storage.getItem(OUTPUT_DEVICE_ID_KEY);
    if (sessionOutputDeviceId) {
      return sessionOutputDeviceId;
    }

    if (this.media.outputDeviceId) {
      return this.media.outputDeviceId;
    }

    if (this.activeSession) {
      return this.activeSession.outputDeviceId;
    }

    return DEFAULT_OUTPUT_DEVICE_ID;
  }

  set outputDeviceId(deviceId) {
    Storage.setItem(OUTPUT_DEVICE_ID_KEY, deviceId);
    this.media.outputDeviceId = deviceId;

    if (this.activeSession) {
      this.activeSession.outputDeviceId = deviceId;
    }
  }

  get inputStream() {
    return this.activeSession ? this.activeSession.inputStream : null;
  }

  /**
   * Wrapper for SIPSession's ignoreCallState flag
   * @param {boolean} value
   */
  set ignoreCallState(value) {
    if (this.activeSession) {
      this.activeSession.ignoreCallState = value;
    }
  }

  get ignoreCallState() {
    return this.activeSession ? this.activeSession.ignoreCallState : false;
  }

  joinAudio({
    isListenOnly,
    extension,
    validIceCandidates,
    inputStream,
  }, managerCallback) {
    const hasFallbackDomain = typeof IPV4_FALLBACK_DOMAIN === 'string' && IPV4_FALLBACK_DOMAIN !== '';

    return new Promise((resolve, reject) => {
      let { hostname } = this;

      this.activeSession = new SIPSession(this.user, this.userData, this.protocol,
        hostname, this.baseCallStates, this.baseErrorCodes, false);

      const callback = (message) => {
        if (message.status === this.baseCallStates.failed) {
          let shouldTryReconnect = false;

          // Try and get the call to clean up and end on an error
          this.activeSession.exitAudio().catch(() => { });

          if (this.activeSession.webrtcConnected) {
            // webrtc was able to connect so just try again
            message.silenceNotifications = true;
            callback({ status: this.baseCallStates.reconnecting, bridge: this.bridgeName, });
            shouldTryReconnect = true;
          } else if (hasFallbackDomain === true && hostname !== IPV4_FALLBACK_DOMAIN) {
            message.silenceNotifications = true;
            logger.info({ logCode: 'sip_js_attempt_ipv4_fallback', extraInfo: { callerIdName: this.user.callerIdName } }, 'Attempting to fallback to IPv4 domain for audio');
            hostname = IPV4_FALLBACK_DOMAIN;
            shouldTryReconnect = true;
          }

          if (shouldTryReconnect) {
            const fallbackExtension = this.activeSession.inEchoTest ? extension : undefined;
            this.activeSession = new SIPSession(this.user, this.userData, this.protocol,
              hostname, this.baseCallStates, this.baseErrorCodes, true);
            const { inputDeviceId, outputDeviceId } = this;
            this.activeSession.joinAudio({
              isListenOnly,
              extension: fallbackExtension,
              inputDeviceId,
              outputDeviceId,
              validIceCandidates,
              inputStream,
            }, callback)
              .then((value) => {
                this.changeOutputDevice(outputDeviceId, true);
                resolve(value);
              }).catch((reason) => {
                reject(reason);
              });
          }
        }

        return managerCallback(message);
      };

      const { inputDeviceId, outputDeviceId } = this;
      this.activeSession.joinAudio({
        isListenOnly,
        extension,
        inputDeviceId,
        outputDeviceId,
        validIceCandidates,
        inputStream,
      }, callback)
        .then((value) => {
          this.changeOutputDevice(outputDeviceId, true);
          resolve(value);
        }).catch((reason) => {
          reject(reason);
        });
    });
  }

  transferCall(onTransferSuccess) {
    this.activeSession.inEchoTest = false;
    logger.debug({
      logCode: 'sip_js_rtp_payload_send_dtmf',
      extraInfo: {
        callerIdName: this.activeSession.user.callerIdName,
      },
    }, 'Sending DTMF INFO to transfer user');

    return this.trackTransferState(onTransferSuccess);
  }

  sendDtmf(tones) {
    this.activeSession.sendDtmf(tones);
  }

  getPeerConnection() {
    if (!this.activeSession) return null;

    const { currentSession } = this.activeSession;
    if (currentSession && currentSession.sessionDescriptionHandler) {
      return currentSession.sessionDescriptionHandler.peerConnection;
    }
    return null;
  }

  exitAudio() {
    return this.activeSession.exitAudio();
  }

  liveChangeInputDevice(deviceId) {
    this.inputDeviceId = deviceId;
    return this.activeSession.liveChangeInputDevice(deviceId);
  }

  async updateAudioConstraints(constraints) {
    return this.activeSession.updateAudioConstraints(constraints);
  }
}

module.exports = SIPBridge;
