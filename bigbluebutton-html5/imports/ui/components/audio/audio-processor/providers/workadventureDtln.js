import { isGenericWasmProcessingSupported } from '../wasmCapability';

const SAMPLE_RATE = 16000;

const isSupported = () => isGenericWasmProcessingSupported();

const loadFiles = async () => {
  // The published worklet bundle embeds the LiteRT wasm bytes and both DTLN
  // model files, so there's nothing to prefetch separately here -
  // createProcessorStream() loads everything through
  // createNoiseSuppressionAudioWorklet() below, on first use.
};

const createProcessorStream = async (stream) => {
  // Dynamic import, not a top-level one: this keeps the package's actual
  // runtime code (and webpack's chunk for it) out of the bundle for
  // deployments that never select this provider - only isSupported()/
  // loadFiles() need to be cheap to statically import (see service.js).
  const { createNoiseSuppressionAudioWorklet } = await import('@workadventure/noise-suppression/audio-worklet');

  // DTLN operates at a fixed 16kHz/mono, unlike BBBA which runs at whatever
  // rate the source stream provides. MediaStreamAudioSourceNode resamples
  // the incoming track to the context's rate automatically.
  const context = new AudioContext({ sampleRate: SAMPLE_RATE });

  await context.resume();

  const source = context.createMediaStreamSource(stream);
  const destination = context.createMediaStreamDestination();
  // threads/numThreads default to false/unset - enabling them needs
  // COOP/COEP headers this client doesn't set today, so this stays on the
  // single-threaded default rather than requesting cross-origin isolation.
  const worklet = await createNoiseSuppressionAudioWorklet(context);
  await worklet.ready;

  source.connect(worklet.node);
  worklet.node.connect(destination);

  return {
    stream: destination.stream,
    context,
    // The package exposes no runtime enable/disable toggle - dispose() is
    // its only lifecycle control - so there's nothing to wire up here.
    setEnabled: () => {},
    destroy: () => worklet.dispose(),
  };
};

// The package's docs ask consumers to disable the browser's own
// noiseSuppression so it doesn't double-process the signal alongside DTLN.
// This is forced, not a default an admin's constraints could override:
// settings.yml ships audioWasmProcessing.constraints non-empty
// (noiseSuppression: true, tuned for BBBA), so a merely-overridable value
// would silently lose to that shipped default for any deployment that
// hasn't customized it - and running the browser's own noiseSuppression
// alongside DTLN actively degrades the signal DTLN receives.
const forcedMicrophoneConstraints = {
  noiseSuppression: false,
};

export default {
  isSupported,
  loadFiles,
  createProcessorStream,
  forcedMicrophoneConstraints,
};
