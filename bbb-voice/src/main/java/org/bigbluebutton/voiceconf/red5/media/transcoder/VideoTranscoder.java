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

    public static enum Type{TRANSCODE_RTP_TO_RTMP,TRANSCODE_RTMP_TO_RTP,TRANSCODE_FILE_TO_RTP, TRANSCODE_FILE_TO_RTMP};
    public static final String VIDEO_CONF_LOGO_PATH = GlobalCall.videoConfLogo;
    public static final String FFMPEG_PATH = GlobalCall.ffmpegPath;


    private Type type;
    private ProcessMonitor ffmpegProcessMonitor;
    private ProcessMonitor ffprobeProcessMonitor;
    private FFmpegCommand ffmpeg;
    private String userId;
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

    public VideoTranscoder(Type type,String userId,String videoStreamName,String meetingId,String ip, String localVideoPort, String remoteVideoPort){
        this.type = type;
        this.sdpPath = "";
        this.userId = userId;
        this.videoStreamName = videoStreamName;
        this.meetingId = meetingId;
        this.ip = ip;
        this.localVideoPort = localVideoPort;
        this.remoteVideoPort = remoteVideoPort;
        this.outputLive = "";
    }

    public VideoTranscoder(Type type,String sdpPath, String userId, String videoStreamName, String meetingId, String ip){
        this.type = type;
        this.userId = userId;
        this.videoStreamName = videoStreamName;
        this.sdpPath = sdpPath;
        this.meetingId = meetingId;
        this.ip = ip;
        this.localVideoPort = "";
        this.remoteVideoPort = "";
        this.outputLive = "";
    }

    /**
     * Creates a new VideoTranscoder, which doesn't need local neither remote video ports.
     * This is useful for FILE_TO_RTMP type of transcoder.
     * @param type
     * @param videoStreamName
     * @param meetingId
     * @param ip
     */
    public VideoTranscoder(Type type,String userId,String videoStreamName,String meetingId,String ip){
        this.type = type;
        this.sdpPath = "";
        this.userId = userId;
        this.videoStreamName = videoStreamName;
        this.meetingId = meetingId;
        this.ip = ip;
        this.outputLive = "";
    }

    public synchronized boolean start(){
        if ((ffmpegProcessMonitor != null) &&(ffmpeg != null)) {
            log.debug("There's already an FFMPEG process running for this transcoder. No need to start a new one");
            return false;
        }

        String[] command;
        String inputLive;

        if(!canFFmpegRun()) {
            log.debug("***TRANSCODER WILL NOT START: ffmpeg cannot run");
            return false;
        }

        log.debug("Starting Video Transcoder...");

        switch(type){
            case TRANSCODE_RTMP_TO_RTP:

                if(!areRtmpToRtpParametersValid()) {
                    log.debug("***TRANSCODER WILL NOT START: Rtmp to Rtp Parameters are invalid");
                    return false;
                }

                log.debug("Video Parameters: remotePort = "+remoteVideoPort+ ", localPort = "+localVideoPort+" rtmp-stream = rtmp://" + ip + "/video/" + meetingId + "/"
                        + videoStreamName);

                inputLive = "rtmp://" + ip + "/video/" + meetingId + "/"
                        + videoStreamName + " live=1";
                outputLive = "rtp://" + ip + ":" + remoteVideoPort + "?localport=" + localVideoPort;

                ffmpeg = new FFmpegCommand();
                ffmpeg.setFFmpegPath(FFMPEG_PATH);
                ffmpeg.setInput(inputLive);
                ffmpeg.addRtmpInputConnectionParameter(meetingId);
                ffmpeg.addRtmpInputConnectionParameter("transcoder-"+userId);
                ffmpeg.setCodec("libopenh264");
                ffmpeg.setMaxRate(512);
                ffmpeg.setSliceMode("dyn");
                ffmpeg.setMaxNalSize("1024");
                ffmpeg.setRtpFlags("h264_mode0"); //RTP's packetization mode 0
                ffmpeg.setProfile("baseline");
                ffmpeg.setFormat("rtp");
                ffmpeg.setPayloadType(String.valueOf(H264Codec.codecId));
                ffmpeg.setLoglevel("quiet");
                ffmpeg.setOutput(outputLive);
                ffmpeg.setAnalyzeDuration("1000"); // 1ms
                ffmpeg.setProbeSize("32"); // 1ms
                log.debug("Preparing FFmpeg process monitor");
                command = ffmpeg.getFFmpegCommand(true);
                break;

            case TRANSCODE_RTP_TO_RTMP:

                if(!areRtpToRtmpParametersValid()) {
                    log.debug("***TRANSCODER WILL NOT START: Rtp to Rtmp Parameters are invalid");
                    return false;
                }

                inputLive = sdpPath;
                outputLive = "rtmp://" + ip + "/video/" + meetingId + "/"
                        + videoStreamName+" live=1";

                ffmpeg = new FFmpegCommand();
                ffmpeg.setFFmpegPath(FFMPEG_PATH);
                ffmpeg.setInput(inputLive);
                ffmpeg.setFormat("flv");
                ffmpeg.setLoglevel("quiet");
                ffmpeg.setOutput(outputLive);
                ffmpeg.addRtmpOutputConnectionParameter(meetingId);
                ffmpeg.addRtmpOutputConnectionParameter("transcoder-"+userId);
                ffmpeg.setMaxRate(512);
                ffmpeg.setCodec("libopenh264");
                ffmpeg.setProfile("baseline");
                ffmpeg.setAnalyzeDuration("1000"); // 10ms
                ffmpeg.addCustomParameter("-s", globalVideoWidth+"x"+globalVideoHeight);
                ffmpeg.addCustomParameter("-filter:v","scale=iw*min("+globalVideoWidth+"/iw\\,"+globalVideoHeight+"/ih):ih*min("+globalVideoWidth+"/iw\\,"+globalVideoHeight+"/ih), pad="+globalVideoWidth+":"+globalVideoHeight+":("+globalVideoWidth+"-iw*min("+globalVideoWidth+"/iw\\,"+globalVideoHeight+"/ih))/2:("+globalVideoHeight+"-ih*min("+globalVideoWidth+"/iw\\,"+globalVideoHeight+"/ih))/2, fps=fps=15");
                log.debug("Preparing FFmpeg process monitor");
                command = ffmpeg.getFFmpegCommand(true);
                break;

            case TRANSCODE_FILE_TO_RTP:

                if(!areFileToRtpParametersValid())  {
                    log.debug("***TRANSCODER WILL NOT START: File to Rtp Parameters are invalid");
                    return false;
                }

                inputLive = VIDEO_CONF_LOGO_PATH;
                outputLive = "rtp://" + ip + ":" + remoteVideoPort + "?localport=" + localVideoPort;

                ffmpeg = new FFmpegCommand();
                ffmpeg.setFFmpegPath(FFMPEG_PATH);
                ffmpeg.setIgnoreLoop(0);
                //ffmpeg.addCustomParameter("-framerate", "15");
                ffmpeg.setInput(inputLive);
                ffmpeg.setInputLive(true);
                ffmpeg.addCustomParameter("-s", globalVideoWidth+"x"+globalVideoHeight);
                ffmpeg.setPayloadType(String.valueOf(H264Codec.codecId));
                ffmpeg.setLoglevel("quiet");
                ffmpeg.setCodec("libopenh264");
                ffmpeg.setSliceMode("dyn");
                ffmpeg.setMaxNalSize("1024");
                ffmpeg.setRtpFlags("h264_mode0"); //RTP's packetization mode 0
                ffmpeg.setProfile("baseline");
                ffmpeg.setFormat("rtp");
                ffmpeg.setOutput(outputLive);
                log.debug("Preparing video-conf-logo's FFmpeg process monitor");
                command = ffmpeg.getFFmpegCommand(true);
                break;

            case TRANSCODE_FILE_TO_RTMP:

                if(!areFileToRtmpParametersValid()) {
                    log.debug("***TRANSCODER WILL NOT START: File to Rtmp Parameters are invalid");
                    return false;
                }

                inputLive = VIDEO_CONF_LOGO_PATH;
                outputLive = "rtmp://" + ip + "/video/" + meetingId + "/"
                        + videoStreamName+" live=1";

                ffmpeg = new FFmpegCommand();
                ffmpeg.setFFmpegPath(FFMPEG_PATH);
                ffmpeg.setInput(inputLive);
                ffmpeg.setInputLive(true);
                ffmpeg.setFrameSize("640x480");
                ffmpeg.setIgnoreLoop(0);
                ffmpeg.setFormat("flv");
                ffmpeg.setLoglevel("quiet");
                ffmpeg.addRtmpOutputConnectionParameter(meetingId);
                ffmpeg.addRtmpOutputConnectionParameter("transcoder-"+userId);
                ffmpeg.setOutput(outputLive);
                ffmpeg.setCodec("libopenh264");
                ffmpeg.setProfile("baseline");
                log.debug("Preparing FFmpeg process monitor");
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
                    //user's videoconf-logo video stream : parameters are the same
                    log.debug("Restarting the user's videoconf-logo video stream...");
                    ffmpegProcessMonitor.restart();
                    return true;
                case TRANSCODE_FILE_TO_RTMP:
                    //videoconf-logo video stream : parameters are the same
                    log.debug("Restarting the videconf's logo videoconf-logo video stream...");
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


    public boolean canFFmpegRun() {
        log.debug("Checking if FFmpeg can run...");

        if(ip == null || ip.isEmpty()) {
           log.debug("ip is null or empty");
           return false;
        }

        return isFFmpegPathValid();
    }

    public boolean isFFmpegPathValid() {
        if (!GlobalCall.ffmpegExists(FFMPEG_PATH)) {
            log.debug("***FFMPEG DOESN'T EXIST: check the FFMPEG path in bigbluebutton-sip.properties");
            return false;
        }

        return true;
    }


    public boolean areRtmpToRtpParametersValid() {
        log.debug("Checking Rtmp to Rtp Transcoder Parameters...");

        if(meetingId == null || meetingId.isEmpty()) {
           log.debug("meetingId is null or empty");
           return false;
        }

        if(videoStreamName == null || videoStreamName.isEmpty()) {
           log.debug("videoStreamName is null or empty");
           return false;
        }

        return areVideoPortsValid();
    }

    public boolean areRtpToRtmpParametersValid() {
        log.debug("Checking Rtp to Rtmp Transcoder Parameters...");

        if(meetingId == null || meetingId.isEmpty()) {
           log.debug("meetingId is null or empty");
           return false;
        }

        if(videoStreamName == null || videoStreamName.isEmpty()) {
           log.debug("videoStreamName is null or empty");
           return false;
        }

        return isSdpPathValid();
    }

    public boolean areFileToRtpParametersValid() {
        log.debug("Checking File to Rtp Transcoder Parameters...");
        return areVideoPortsValid() && isVideoConfLogoValid();
    }

    public boolean areFileToRtmpParametersValid() {
        log.debug("Checking File to Rtmp Transcoder Parameters...");

        if(meetingId == null || meetingId.isEmpty()) {
           log.debug("meetingId is null or empty");
           return false;
        }

        if(videoStreamName == null || videoStreamName.isEmpty()) {
           log.debug("videoStreamName is null or empty");
           return false;
        }

        return isVideoConfLogoValid();
    }

    public boolean areVideoPortsValid() {
        if(localVideoPort == null || localVideoPort.isEmpty()) {
           log.debug("localVideoPort is null or empty");
           return false;
        }

        if(remoteVideoPort == null || remoteVideoPort.isEmpty()) {
           log.debug("remoteVideoPort is null or empty");
           return false;
        }

        if(localVideoPort.equals("0")) {
           log.debug("localVideoPort is 0");
           return false;
        }

        if(remoteVideoPort.equals("0")) {
           log.debug("remoteVideoPort is 0");
           return false;
        }

        return true;

    }

    public boolean isVideoConfLogoValid() {
        if(!GlobalCall.videoConfLogoExists(VIDEO_CONF_LOGO_PATH)) {
            log.debug("***IMAGE FOR VIDEOCONF-LOGO VIDEO DOESN'T EXIST: check the image path in bigbluebutton-sip.properties");
            return false;
        }

        return true;
    }

    public boolean isSdpPathValid() {
        if(!GlobalCall.sdpVideoExists(sdpPath)) {
            log.debug("***SDP FOR GLOBAL FFMPEG ({}) doesn't exist", sdpPath);
            return false;
        }

        return true;
    }
}
