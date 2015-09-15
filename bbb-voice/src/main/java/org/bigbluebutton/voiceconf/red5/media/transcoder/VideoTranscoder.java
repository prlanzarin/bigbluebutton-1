package org.bigbluebutton.voiceconf.red5.media.transcoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bigbluebutton.voiceconf.sip.FFProbeCommand;
import org.bigbluebutton.voiceconf.sip.FFmpegCommand;
import org.bigbluebutton.voiceconf.sip.GlobalCall;
import org.bigbluebutton.voiceconf.sip.ProcessMonitor;
import org.bigbluebutton.voiceconf.sip.ProcessMonitorObserver;
import org.red5.app.sip.codecs.H264Codec;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class VideoTranscoder implements ProcessMonitorObserver {
    private static Logger log = Red5LoggerFactory.getLogger(VideoTranscoder.class, "sip");

    public static enum Type{TRANSCODE_RTP_TO_RTMP,TRANSCODE_RTMP_TO_RTP,TRANSCODE_FILE_TO_RTP};
    public static final String TEMP_SIP_VIDEO_IMG_PATH = GlobalCall.tempSipVideoImg;

    private Type type;
    private ProcessMonitor ffmpegProcessMonitor;
    private ProcessMonitor ffprobeProcessMonitor;
    private FFmpegCommand ffmpeg;
    private String videoStreamName;
    private String outputLive;
    private String meetingId;
    private String ip;
    private String localVideoPort;
    private String remoteVideoPort;
    private String sdpPath;
    private VideoTranscoderObserver observer;
    private String globalVideoWidth = GlobalCall.getGlobalVideoWidth();
    private String globalVideoHeight = GlobalCall.getGlobalVideoHeight();
    public static final String FFMPEG_NAME = "FFMPEG";
    public static final String FFPROBE_NAME = "FFPROBE";

    public VideoTranscoder(Type type,String videoStreamName,String meetingId,String ip, String localVideoPort, String remoteVideoPort){
        this.type = type;
        this.sdpPath = "";
        this.videoStreamName = videoStreamName;
        this.meetingId = meetingId;
        this.ip = ip;
        this.localVideoPort = localVideoPort;
        this.remoteVideoPort = remoteVideoPort;
        this.outputLive = "";
    }

    public VideoTranscoder(Type type,String sdpPath, String videoStreamName, String meetingId, String ip){
        this.type = type;
        this.videoStreamName = videoStreamName;
        this.sdpPath = sdpPath;
        this.meetingId = meetingId;
        this.ip = ip;
        this.localVideoPort = "";
        this.remoteVideoPort = "";
        this.outputLive = "";
    }

    public synchronized boolean start(){
        if ((ffmpegProcessMonitor != null) &&(ffmpeg != null)) {
            log.debug("There's already an FFMPEG process running for this transcoder. No need to start a new one");
            return false;
        }

        String[] command;
        String inputLive;
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
                ffmpeg.setAnalyzeDuration("1000"); // 1ms
                ffmpeg.setProbeSize("32"); // 1ms
                ffmpeg.addCustomParameter("-tune", "zerolatency"); //x264 parameter
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
                ffmpeg.setCodec("libx264");
                ffmpeg.setPreset("ultrafast");
                ffmpeg.setProfile("baseline");
                ffmpeg.setAnalyzeDuration("10000"); // 10ms
                //ffmpeg.addCustomParameter("-q:v", "1");
                ffmpeg.setMaxKeyFrameInterval("30"); //2*fps. 1 key frame on each 2s
                ffmpeg.addCustomParameter("-tune", "zerolatency"); //x264 parameter
                ffmpeg.addCustomParameter("-crf", "30");
                ffmpeg.addCustomParameter("-s", globalVideoWidth+"x"+globalVideoHeight);
                ffmpeg.addCustomParameter("-filter:v","scale=iw*min("+globalVideoWidth+"/iw\\,"+globalVideoHeight+"/ih):ih*min("+globalVideoWidth+"/iw\\,"+globalVideoHeight+"/ih), pad="+globalVideoWidth+":"+globalVideoHeight+":("+globalVideoWidth+"-iw*min("+globalVideoWidth+"/iw\\,"+globalVideoHeight+"/ih))/2:("+globalVideoHeight+"-ih*min("+globalVideoWidth+"/iw\\,"+globalVideoHeight+"/ih))/2, fps=fps=15");
                log.debug("Preparing FFmpeg process monitor");
                command = ffmpeg.getFFmpegCommand(true);
                break;

            case TRANSCODE_FILE_TO_RTP:
                inputLive = TEMP_SIP_VIDEO_IMG_PATH;
                outputLive = "rtp://" + ip + ":" + remoteVideoPort + "?localport=" + localVideoPort;

                ffmpeg = new FFmpegCommand();
                ffmpeg.setFFmpegPath("/usr/local/bin/ffmpeg");
                ffmpeg.setLoop("1");
                ffmpeg.addCustomParameter("-framerate", "5");
                ffmpeg.setInput(inputLive);
                ffmpeg.addCustomParameter("-r", "5");
                ffmpeg.addCustomParameter("-s", globalVideoWidth+"x"+globalVideoHeight);
                ffmpeg.setLevel("1.3");
                ffmpeg.setPayloadType(String.valueOf(H264Codec.codecId));
                ffmpeg.setLoglevel("quiet");
                ffmpeg.setCodec("h264");
                ffmpeg.addCustomParameter("-tune", "zerolatency"); //x264 parameter
                ffmpeg.setPreset("ultrafast");
                ffmpeg.setFormat("rtp");
                ffmpeg.setSliceMaxSize("1024");
                ffmpeg.setMaxKeyFrameInterval("10");
                ffmpeg.setOutput(outputLive);
                log.debug("Preparing temporary FFmpeg process monitor");
                command = ffmpeg.getFFmpegCommand(true);
                break;


            default: command = null;
        }

        if(command != null){
            this.ffmpegProcessMonitor = new ProcessMonitor(command,FFMPEG_NAME);
            ffmpegProcessMonitor.setProcessMonitorObserver(this);
            ffmpegProcessMonitor.start();
            return true;
        }
        return false;
    }

    public synchronized boolean stop(){
        if (ffmpegProcessMonitor != null) {
            ffmpegProcessMonitor.forceDestroy();
            ffmpegProcessMonitor = null;
            ffmpeg = null;
        }else log.debug("There's no FFMPEG process running for this transcoder. No need to destroy it");
        return true;
    }

    public synchronized boolean restart(String streamName){
        /*
         * It doesn't instantiate a new processMonitor, but uses the same reference, to restart the transcoder
         */
        if ((ffmpegProcessMonitor != null) && (ffmpeg != null)){
            switch(type){
                case TRANSCODE_RTMP_TO_RTP:
                    //user's video stream : parameters are the same
                    log.debug("Restarting the user's video stream: "+this.videoStreamName);
                    ffmpegProcessMonitor.restart();
                    return true;
                case TRANSCODE_RTP_TO_RTMP:
                    //global's video stream : stream name got a new timestamp
                    updateGlobalStreamName(streamName);
                    log.debug("Restarting the global's video stream: "+this.videoStreamName);
                    ffmpegProcessMonitor.restart();
                    return true;
                case TRANSCODE_FILE_TO_RTP:
                    //user's temporary video stream : parameters are the same
                    log.debug("Restarting the user's temporary video stream...");
                    ffmpegProcessMonitor.restart();
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

    public synchronized void probeVideoStream(){
        if (ffmpegProcessMonitor != null) {
            log.debug("Preparing to run probe command");
            FFProbeCommand ffprobe = new FFProbeCommand(outputLive);
            String command[];

            ffprobe.setFFprobepath("/usr/local/bin/ffprobe");
            ffprobe.setInput(outputLive);
            ffprobe.setAnalyzeDuration("1");
            ffprobe.setShowStreams();
            ffprobe.setLoglevel("quiet");
            ffprobe.getFFprobeCommand(true);

            command = ffprobe.getFFprobeCommand(true);
            if(command != null){
                this.ffprobeProcessMonitor = new ProcessMonitor(command,FFPROBE_NAME);
                ffprobeProcessMonitor.setProcessMonitorObserver(this);
                ffprobeProcessMonitor.start();
            }
        } else {
          log.debug("There's no FFMPEG process running for this transcoder. Stream can't be analyzed");
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
        ffmpegProcessMonitor.setCommand(newCommand); //update ffmpeg command
    }

    public void setVideoTranscoderObserver(VideoTranscoderObserver observer){
        this.observer = observer;
    }

    public VideoTranscoder.Type getType() {
        return type;
    }

    @Override
    public void handleProcessFinishedUnsuccessfully(String processMonitorName,String processOutput) {
        if ((processMonitorName == null)|| processMonitorName.isEmpty()){
            log.debug("Can't handle process process monitor finishing unsuccessfully: UNKNOWN PROCESS");
            return;
        }

        if (FFMPEG_NAME.equals(processMonitorName)){
            observer.handleTranscodingFinishedUnsuccessfully();
        }else if (FFPROBE_NAME.equals(processMonitorName)){
            log.debug("Failed to probe video stream [{}]",outputLive);
        }
    }

    @Override
    public void handleProcessFinishedWithSuccess(String processMonitorName, String processOutput) {
        if ((processMonitorName == null)|| processMonitorName.isEmpty()){
            log.debug("Can't handle process process monitor finishing with success: UNKNOWN PROCESS");
            return;
        }

        if (FFMPEG_NAME.equals(processMonitorName)){
            observer.handleTranscodingFinishedWithSuccess();
        }
        else if (FFPROBE_NAME.equals(processMonitorName)){
            String ffprobeOutput = processOutput;
            log.debug("{} finished with success with the output: {}",processMonitorName,processOutput);
            Map<String,String> ffprobeData = parseFFprobeOutput(ffprobeOutput);
            observer.handleVideoProbingFinishedWithSuccess(ffprobeData);
        }else{
            log.debug("Can't handle process monitor finishing with success: UNKNOWN PROCESS");
        }
    }

    public Map<String,String> parseFFprobeOutput(String ffprobeOutput){
        Pattern pattern = Pattern.compile("(.*)=(.*)");
        Map<String, String> ffprobeResult = new HashMap<String, String>();

        BufferedReader buf = new BufferedReader(new StringReader(ffprobeOutput));
        String line = null;
        try {
            while( (line=buf.readLine()) != null){
                Matcher matcher = pattern.matcher(line);
                if(matcher.matches()) {
                    ffprobeResult.put(matcher.group(1), matcher.group(2));
                }
            }
        } catch (IOException e){
            log.debug("Error when parsing FFprobe's output");
        }
        return ffprobeResult;
    }
}
