import React, { Component } from 'react';
import PropTypes from 'prop-types';
import logger from '/imports/startup/client/logger';
import Styled from './styles';
import {
  getAudioConstraints,
} from '/imports/api/audio/client/bridge/service';

const propTypes = {
  low: PropTypes.number,
  optimum: PropTypes.number,
  high: PropTypes.number,
  deviceId: PropTypes.string,
  intl: PropTypes.shape({
    formatMessage: PropTypes.func.isRequired,
  }).isRequired,
  stream: PropTypes.object,
};

const defaultProps = {
  low: 0,
  optimum: 0.05,
  high: 0.3,
  deviceId: undefined,
  stream: null,
};

const AudioContext = window.AudioContext || window.webkitAudioContext;

class AudioStreamVolume extends Component {
  static handleError(error) {
    logger.error({
      logCode: 'audiostreamvolume_handleError',
      extraInfo: { error },
    }, 'Encountered error while creating audio context');
  }

  constructor(props) {
    super(props);

    this.createAudioContext = this.createAudioContext.bind(this);
    this.closeAudioContext = this.closeAudioContext.bind(this);
    this.handleConnectStreamToProcessor = this.handleConnectStreamToProcessor.bind(this);
    this.handleAudioProcess = this.handleAudioProcess.bind(this);
    this.handleError = AudioStreamVolume.handleError.bind(this);

    this.state = {
      slow: 0,
    };
  }

  componentDidMount() {
    this.createAudioContext();
  }

  componentDidUpdate(prevProps) {
    const { deviceId: nextDeviceId, stream: nextStream } = this.props;
    const shouldRecreateAudioContext = (prevProps.deviceId !== nextDeviceId)
      || (prevProps.stream?.id !== nextStream?.id);

    if (shouldRecreateAudioContext) {
      this.closeAudioContext().then(() => {
        this.setState({
          slow: 0,
        });
        this.createAudioContext();
      });
    }
  }

  componentWillUnmount() {
    this.closeAudioContext();
  }

  handleConnectStreamToProcessor(stream) {
    this.source = this.audioContext.createMediaStreamSource(stream);
    this.source.connect(this.scriptProcessor);
    this.scriptProcessor.connect(this.audioContext.destination);
  }

  handleAudioProcess(event) {
    const input = event.inputBuffer.getChannelData(0);
    const sum = input.reduce((a, b) => a + (b * b), 0);
    const instant = Math.sqrt(sum / input.length);

    this.setState((prevState) => ({
      slow: (0.75 * prevState.slow) + (0.25 * instant),
    }));
  }

  createAudioContext() {
    const { stream } = this.props;

    if (stream) {
      this.audioContext = new AudioContext();
      this.scriptProcessor = this.audioContext.createScriptProcessor(2048, 1, 1);
      this.scriptProcessor.onaudioprocess = this.handleAudioProcess;
      this.source = null;
      this.handleConnectStreamToProcessor(stream);
    }
  }

  closeAudioContext() {
    if (!this.audioContext) return Promise.resolve();

    // Early override of the meter state callback to avoid re-renders while
    // unmounting
    if (this.scriptProcessor) {
      this.scriptProcessor.onaudioprocess = null;
    }

    return this.audioContext.close().then(() => {
      this.audioContext = null;
      this.source = null;
    });
  }

  render() {
    const {
      low, optimum, high, ...props
    } = this.props;

    const { slow } = this.state;

    return (
      <Styled.VolumeMeter
        {...props}
        min={0}
        max={high * 1.25}
        low={low}
        optimum={optimum}
        high={high}
        value={slow}
      />
    );
  }
}

AudioStreamVolume.propTypes = propTypes;
AudioStreamVolume.defaultProps = defaultProps;

export default AudioStreamVolume;
