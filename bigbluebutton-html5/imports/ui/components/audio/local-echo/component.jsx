import React from 'react';
import PropTypes from 'prop-types';
import { defineMessages, injectIntl } from 'react-intl';
import Styled from '../audio-test/styles';
import Settings from '/imports/ui/services/settings';

const propTypes = {
  intl: PropTypes.shape({
    formatMessage: PropTypes.func.isRequired,
  }).isRequired,
  outputDeviceId: PropTypes.string,
  stream: PropTypes.object,
};

const defaultProps = {
  outputDeviceId: null,
  stream: null,
};

const intlMessages = defineMessages({
  hearYourselfLabel: {
    id: 'app.audio.hearYourselfLabel',
    description: 'Hear yourself button label',
  },
  stopHearingYourselfLabel: {
    id: 'app.audio.stopHearingYourselfLabel',
    description: 'Stop hearing yourself button label',
  },

});

class LocalEcho extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      hearing: true,
    };

    this.toggleLocalEcho = this.toggleLocalEcho.bind(this);
  }

  componentDidMount() {
    this.handleHearingStateChange();
  }

  componentWillUnmount() {
    this.deattachEchoStream();
  }

  componentDidUpdate(prevProps, prevState) {
    const { hearing: nextHearingState } = this.state;
    const { stream: nextStreamProp } = this.props;
    const shouldUpdate = (prevState.hearing !== nextHearingState)
      || prevProps.stream?.id !== nextStreamProp?.id;

    if (shouldUpdate) {
      this.handleHearingStateChange();
    }
  }

  deattachEchoStream() {
    const audio = document.querySelector('#remote-media');
    audio.pause();
    audio.srcObject = null;
  }

  playEchoStream() {
    const { stream } = this.props;

    if (stream) {
      const audio = document.querySelector('#remote-media');
      this.deattachEchoStream();
      audio.srcObject = stream;
      audio.play();

    }
  }

  handleHearingStateChange() {
    const { hearing } = this.state;

    if (hearing) {
      this.playEchoStream();
    } else {
      this.deattachEchoStream();
    }
  }

  toggleLocalEcho (outputDeviceId) {
    const { hearing } = this.state;

    // TODO handle outputDeviceId changes
    this.setState({ hearing: !hearing });
  }

  render() {
    const {
      outputDeviceId,
      intl,
    } = this.props;

    const { hearing } = this.state;
    const { animations } = Settings.application;

    const icon = hearing ? "mute" : "unmute";
    const label = hearing ? intlMessages.stopHearingYourselfLabel : intlMessages.hearYourselfLabel;

    return (
      <Styled.TestAudioButton
        label={intl.formatMessage(label)}
        icon={icon}
        size="sm"
        color="primary"
        onClick={() => this.toggleLocalEcho(outputDeviceId)}
        animations={animations}
      />
    );
  }
}

LocalEcho.propTypes = propTypes;
LocalEcho.defaultProps = defaultProps;

export default injectIntl(LocalEcho);
