package org.bigbluebutton.voiceconf.red5.media.transcoder;

import org.bigbluebutton.voiceconf.sip.FFmpegCommand;
import org.bigbluebutton.voiceconf.sip.ProcessMonitor;
import org.red5.app.sip.codecs.H264Codec;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class VideoTranscoder {
    private static Logger log = Red5LoggerFactory.getLogger(VideoTranscoder.class, "sip");

    public static enum Type{TRANSCODE_RTP_TO_RTMP,TRANSCODE_RTMP_TO_RTP};
    private Type type;
    private ProcessMonitor processMonitor;
    private FFmpegCommand ffmpeg;
    private String videoStreamName;
    private String meetingId;
    private String ip;
    private String localVideoPort;
    private String remoteVideoPort;
    private String sdpPath;
    private VideoTranscoderObserver observer;

    public VideoTranscoder(Type type,String videoStreamName,String meetingId,String ip, String localVideoPort, String remoteVideoPort){
        this.type = type;
        this.sdpPath = "";
        this.videoStreamName = videoStreamName;
        this.meetingId = meetingId;
        this.ip = ip;
        this.localVideoPort = localVideoPort;
        this.remoteVideoPort = remoteVideoPort;
    }

    public VideoTranscoder(Type type,String sdpPath, String videoStreamName, String meetingId, String ip){
        this.type = type;
        this.videoStreamName = videoStreamName;
        this.sdpPath = sdpPath;
        this.meetingId = meetingId;
        this.ip = ip;
        this.localVideoPort = "";
        this.remoteVideoPort = "";
    }

    public synchronized boolean start(){
        if ((processMonitor != null) &&(ffmpeg != null)) {
            log.debug("There's already an FFMPEG process running for this transcoder. No need to start a new one");
            return false;
        }

        String[] command;
        String inputLive;
        String outputLive;
        log.debug("Starting Video Transcoder...");

        switch(type){
            case TRANSCODE_RTMP_TO_RTP:
                log.debug("Video Parameters: remotePort = "+remoteVideoPort+ ", localPort = "+localVideoPort+" rtmp-stream = rtmp://" + ip + "/video/" + meetingId + "/"
                        + videoStreamName);

                inputLive = "rtmp://" + ip + "/video/" + meetingId + "/"
                        + videoStreamName + " live=1";
                outputLive = "rtp://" + ip + ":" + remoteVideoPort + "?localport=" + localVideoPort;

                ffmpeg = new FFmpegCommand();
                ffmpeg.setFFmpegPath("/usr/local/bin/ffmpeg");
                ffmpeg.setInput(inputLive);
                ffmpeg.setCodec("h264");
                ffmpeg.setPreset("ultrafast");
                ffmpeg.setProfile("baseline");
                ffmpeg.setLevel("1.3");
                ffmpeg.setFormat("rtp");
                ffmpeg.setPayloadType(String.valueOf(H264Codec.codecId));
                ffmpeg.setLoglevel("quiet");
                ffmpeg.setSliceMaxSize("1024");
                ffmpeg.setMaxKeyFrameInterval("10");
                ffmpeg.setOutput(outputLive);
                ffmpeg.setAnalyzeDuration("10000"); // 10ms
                log.debug("Preparing FFmpeg process monitor");
                command = ffmpeg.getFFmpegCommand(true);
                break;

            case TRANSCODE_RTP_TO_RTMP:
                inputLive = sdpPath;
                outputLive = "rtmp://" + ip + "/video/" + meetingId + "/"
                        + videoStreamName+" live=1";

                ffmpeg = new FFmpegCommand();
                ffmpeg.setFFmpegPath("/usr/local/bin/ffmpeg");
                ffmpeg.setInput(inputLive);
                ffmpeg.setFormat("flv");
                ffmpeg.setLoglevel("quiet");
                ffmpeg.setOutput(outputLive);
                ffmpeg.addCustomParameter("-q:v", "1");
                log.debug("Preparing FFmpeg process monitor");
                command = ffmpeg.getFFmpegCommand(true);
                break;

            default: command = null;
        }

        if(command != null){
            this.processMonitor = new ProcessMonitor(command);
            processMonitor.setVideoTranscoderObserver(observer);
            processMonitor.start();
            return true;
        }
        return false;
    }

    public synchronized boolean stop(){
        if (processMonitor != null) {
            if(type == Type.TRANSCODE_RTP_TO_RTMP)
                processMonitor.forceDestroy();
            else processMonitor.destroy();
            processMonitor = null;
            ffmpeg = null;
        }else log.debug("There's no FFMPEG process running for this transcoder. No need to destroy it");
        return true;
    }

    public synchronized boolean restart(String streamName){
        if ((processMonitor != null) && (ffmpeg != null)){
            switch(type){
                case TRANSCODE_RTMP_TO_RTP:
                    //user's video stream : parameters are the same
                    log.debug("Restarting the user's video stream: "+this.videoStreamName);
                    processMonitor.restart();
                    return true;
                case TRANSCODE_RTP_TO_RTMP:
                    //global's video stream : stream name got a new timestamp
                    updateGlobalStreamName(streamName);
                    log.debug("Restarting the global's video stream: "+this.videoStreamName);
                    processMonitor.restart();
                    return true;
                default:
                    log.debug("Video Transcoder error: Unknown TRANSCODING TYPE");
                    break;
            }
            return false;
        }else {
            log.debug(" Can't restart VideoTranscoder. There's no ProcessMonitor running");
            return false;
        }
    }

    private void updateGlobalStreamName(String streamName){
        this.videoStreamName = streamName;
        String outputLive;
        String[] newCommand;
        outputLive = "rtmp://" + ip + "/video/" + meetingId + "/"
                + this.videoStreamName+" live=1";
        ffmpeg.setOutput(outputLive); //update ffmpeg's output
        newCommand = ffmpeg.getFFmpegCommand(true);
        processMonitor.setCommand(newCommand); //update ffmpeg command
    }

    public void setVideoTranscoderObserver(VideoTranscoderObserver observer){
        this.observer = observer;
    }
}
