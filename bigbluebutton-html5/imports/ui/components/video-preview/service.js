import Storage from '/imports/ui/services/storage/session';
import getFromUserSettings from '/imports/ui/services/users-settings';

const DEFAULT_DEVICE_ID = 'DEFAULT_CAMERA_ID';

// VIDEO_STREAM_STORAGE: Map<deviceId, MediaStream>. Registers WEBCAM streams.
// Easier to keep track of them. Easier to centralize their referencing.
// Easier to shuffle them around.
const VIDEO_STREAM_STORAGE = new Map();

const storeStream = (deviceId, stream) => {
  // Check if there's a dangling stream. If there's one and it's active, cool,
  // return false as it's already stored. Otherwised, clean the derelict stream
  // and store the new one
  if (VIDEO_STREAM_STORAGE.has(deviceId)) {
    const existingStream = getStream(deviceId);
    if (existingStream.active) return false;
    deleteStream(deviceId);
  }

  VIDEO_STREAM_STORAGE.set(deviceId, stream);

  // Stream insurance: clean it up if it ends (see the events being listened to below)
  const cleanup = () => {
    deleteStream(deviceId)
  }

  // Dirty, but effective way of checking whether the browser supports the 'inactive'
  // event. If the oninactive interface is null, it can be overridden === supported.
  // If undefined, it's not; so we fallback to the track 'ended' event.
  if (stream.oninactive === null) {
    stream.addEventListener('inactive', cleanup, { once: true });
  } else {
    const track = getVideoTracks(stream)[0];
    if (track) {
      track.addEventListener('ended', cleanup, { once: true });
    }
  }

  return true;
}

const getStream = (deviceId) => {
  return VIDEO_STREAM_STORAGE.get(deviceId);
}

const deleteStream = (deviceId) => {
  const stream = getStream(deviceId);
  if (stream == null) return false;
  stopStreamTracks(stream);
  return VIDEO_STREAM_STORAGE.delete(deviceId);
}

const stopStreamTracks = (stream) => {
  if (stream && typeof stream.getTracks === 'function') {
    stream.getTracks().forEach(track => {
      if (typeof track.stop === 'function') {
        track.stop();
      }
    });
  }
}

const promiseTimeout = (ms, promise) => {
  const timeout = new Promise((resolve, reject) => {
    const id = setTimeout(() => {
      clearTimeout(id);

      const error = {
        name: 'TimeoutError',
        message: 'Promise did not return',
      };

      reject(error);
    }, ms);
  });

  return Promise.race([
    promise,
    timeout,
  ]);
};

const getSkipVideoPreview = () => {
  const KURENTO_CONFIG = Meteor.settings.public.kurento;

  const skipVideoPreviewOnFirstJoin = getFromUserSettings(
    'bbb_skip_video_preview_on_first_join',
    KURENTO_CONFIG.skipVideoPreviewOnFirstJoin,
  );
  const skipVideoPreview = getFromUserSettings(
    'bbb_skip_video_preview',
    KURENTO_CONFIG.skipVideoPreview,
  );

  return (
    (Storage.getItem('isFirstJoin') !== false && skipVideoPreviewOnFirstJoin)
    || skipVideoPreview
  );
};

const getVideoTracks = (stream) => {
  let videoTracks = [];

  if (stream) {
    if (typeof stream.getVideoTracks === 'function') {
      videoTracks = stream.getVideoTracks();
    } else if (typeof stream.getTracks === 'function') {
      videoTracks = stream.getTracks().filter(track => track.kind === 'video');
    }
  }

  return videoTracks;
}

const applyProfileConstraints = (stream, profile) => {
  const videoTracks = getVideoTracks(stream);

  // Something borked in the track fetching
  if (videoTracks.length === 0) return Promise.reject(new Error('NoVideoTracksError'));

  return Promise.all(
    videoTracks.map(track => {
      return track.applyConstraints(profile.constraints).catch(error => {
        logger.warn({
          logCode: 'video_preview_profile_apply_failure',
          extraInfo: { errorName: error.name, errorCode: error.code, profileId: profile.id },
        }, 'Error applying preview camera profile');
      });
    })
  );
}

const getVideoDeviceId = (stream) => {
  const videoTracks = getVideoTracks(stream);

  if (videoTracks.length > 0 && videoTracks[0].getSettings) {
    const trackSettings = videoTracks[0].getSettings();
    return trackSettings.deviceId;
  }

  return DEFAULT_DEVICE_ID;
}

const digestVideoDevices = (devices, priorityDevice) => {
  const webcams = [];
  let areLabelled = true;

  devices.forEach((device) => {
    if (device.kind === 'videoinput') {
      // Avoid duplicated devices
      if (!webcams.some(d => d.deviceId === device.deviceId)) {
        // We found a priority device. Push it to the beginning of the array so we
        // can use it as the "initial device"
        if (priorityDevice && priorityDevice === device.deviceId) {
          webcams.unshift(device);
        } else {
          webcams.push(device);
        }

        if (!device.label) { areLabelled = false };
      }
    }
  });

  // Returns the list of devices and whether they are labelled or not
  return { webcams, areLabelled };
}

export default {
  promiseTimeout,
  changeWebcam: (deviceId) => {
    Session.set('WebcamDeviceId', deviceId);
  },
  webcamDeviceId: () => Session.get('WebcamDeviceId'),
  changeProfile: (profileId) => {
    Session.set('WebcamProfileId', profileId);
  },
  getSkipVideoPreview,
  stopStreamTracks,
  storeStream,
  getStream,
  deleteStream,
  applyProfileConstraints,
  getVideoDeviceId,
  digestVideoDevices,
};
