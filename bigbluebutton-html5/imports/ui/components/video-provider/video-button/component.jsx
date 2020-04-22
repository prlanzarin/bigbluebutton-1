import React, { memo } from 'react';
import PropTypes from 'prop-types';
import cx from 'classnames';
import Button from '/imports/ui/components/button/component';
import VideoService from '../service';
import { defineMessages, injectIntl, intlShape } from 'react-intl';
import { styles } from './styles';
import { validIOSVersion } from '/imports/ui/components/app/service';

const CAMERA_SOFT_CAP = Meteor.settings.public.kurento.meetingCamerasSoftCap.cameras;

const intlMessages = defineMessages({
  joinVideo: {
    id: 'app.video.joinVideo',
    description: 'Join video button label',
  },
  leaveVideo: {
    id: 'app.video.leaveVideo',
    description: 'Leave video button label',
  },
  videoButtonDesc: {
    id: 'app.video.videoButtonDesc',
    description: 'video button description',
  },
  videoLocked: {
    id: 'app.video.videoLocked',
    description: 'video disabled label',
  },
  iOSWarning: {
    id: 'app.iOSWarning.label',
    description: 'message indicating to upgrade ios version',
  },
  cameraSoftcapReached: {
    id: 'app.video.cameraSoftcapReached',
    description: 'video soft cap reached label',
  },
});

const propTypes = {
  intl: intlShape.isRequired,
  hasVideoStream: PropTypes.bool.isRequired,
  isDisabled: PropTypes.bool.isRequired,
  isSoftcapLocked: PropTypes.bool.isRequired,
  mountVideoPreview: PropTypes.func.isRequired,
};

const JoinVideoButton = ({
  intl,
  hasVideoStream,
  isDisabled,
  isSoftcapLocked,
  mountVideoPreview,
}) => {
  // FIXME maybe straighten some of this stuff and move them to a service.
  const exitVideo = () => hasVideoStream && !VideoService.isMultipleCamerasEnabled();

  const handleOnClick = () => {
    if (!validIOSVersion()) {
      return VideoService.notify(intl.formatMessage(intlMessages.iOSWarning));
    }

    if (exitVideo()) {
      VideoService.exitVideo();
    } else {
      mountVideoPreview();
    }
  };

  const getButtonLabel = () => {
    if (exitVideo()) {
      return intl.formatMessage(intlMessages.leaveVideo);
    }

    // If the button is disabled it's either due to lock settings or the
    // camera soft cap. Fetch the appropriate label.
    if (isDisabled) {
      if (isSoftcapLocked) {
        const softCapLabel = intl.formatMessage(intlMessages.cameraSoftcapReached,
          { 0: CAMERA_SOFT_CAP });

        return `${intl.formatMessage(intlMessages.videoLocked)}. ${softCapLabel}`;
      }
      return intl.formatMessage(intlMessages.videoLocked);
    }

    return intl.formatMessage(intlMessages.joinVideo);
  };

  const buttonLabel = getButtonLabel();

  return (
    <Button
      data-test="joinVideo"
      label={buttonLabel}
      className={cx(styles.button, hasVideoStream || styles.btn)}
      onClick={handleOnClick}
      hideLabel
      aria-label={intl.formatMessage(intlMessages.videoButtonDesc)}
      color={hasVideoStream ? 'primary' : 'default'}
      icon={hasVideoStream ? 'video' : 'video_off'}
      ghost={!hasVideoStream}
      size="lg"
      circle
      disabled={isDisabled}
    />
  );
};

JoinVideoButton.propTypes = propTypes;

export default injectIntl(memo(JoinVideoButton));
