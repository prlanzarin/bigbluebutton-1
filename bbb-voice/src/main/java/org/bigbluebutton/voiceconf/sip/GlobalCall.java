package org.bigbluebutton.voiceconf.sip;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.bigbluebutton.voiceconf.messaging.IMessagingService;
import org.bigbluebutton.voiceconf.red5.media.CallStream;
import org.bigbluebutton.voiceconf.red5.media.transcoder.VideoTranscoder;
import org.red5.app.sip.codecs.Codec;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

import ch.qos.logback.core.property.FileExistsPropertyDefiner;

public class GlobalCall {
    private static final Logger log = Red5LoggerFactory.getLogger( GlobalCall.class, "sip" );
    private static final String LOW_QUALITY = "160x120";
    private static final String MEDIUM_QUALITY = "320x240";
    private static final String HIGH_QUALITY = "640x480";

    private static Set<String> globalCalls = new HashSet<String>();
    private static Map<String,String> roomToAudioStreamMap = new ConcurrentHashMap<String, String>();
    private static Map<String,Codec> roomToAudioCodecMap = new ConcurrentHashMap<String, Codec>();
    //private static Map<String,KeepGlobalAudioAlive> globalAudioKeepAliverMap = new ConcurrentHashMap<String, KeepGlobalAudioAlive>();
    private static Map<String,CallStream> roomToVideoStreamMap = new ConcurrentHashMap<String, CallStream>();
    private static Map<String,Boolean> roomToVideoPresent = new ConcurrentHashMap<String,Boolean>();
    private static Map<String,Boolean> roomToSipPhonePresent = new ConcurrentHashMap<String,Boolean>();
    private static Map<String, VoiceConfToListenOnlyUsersMap> voiceConfToListenOnlyUsersMap = new ConcurrentHashMap<String, VoiceConfToListenOnlyUsersMap>();
    private static Map<String, VoiceConfToGlobalVideoUsersMap> voiceConfToGlobalVideoUsersMap = new ConcurrentHashMap<String, VoiceConfToGlobalVideoUsersMap>();
    private static Map<String, FloorHolder> voiceConfToFloorHolder = new ConcurrentHashMap<String, FloorHolder>();
    private static Map<String, VideoTranscoder> voiceConfToVideoLogoTranscoder = new ConcurrentHashMap<String,VideoTranscoder>();
    private static Path sdpVideoPath;
    public static final String GLOBAL_AUDIO_STREAM_NAME_PREFIX = "GLOBAL_AUDIO_";
    public static final String GLOBAL_VIDEO_STREAM_NAME_PREFIX = "sip_";
    public static final String VIDEOCONFLOGO_STREAM_NAME_PREFIX = "video_conf_";

    public static final String LISTENONLY_USERID_PREFIX = "GLOBAL_CALL_"; //when changed, must also change ESLEventListener.java in bigbluebutton-apps
    private static final String sdpVideoFullPath = "/tmp/"+GLOBAL_VIDEO_STREAM_NAME_PREFIX; //when changed , must also change VideoApplication.java in bbb-video
    private static OpenOption[] fileOptions = new OpenOption[] {StandardOpenOption.CREATE,StandardOpenOption.WRITE};

    private static boolean sipVideoEnabled = false;

    public static String sipVideoWidth;
    public static String sipVideoHeight;
    public static String videoConfLogo = "";
    public static String ffmpegPath = "";
    private static String sipServerHost;
    private static String sipClientRtpIp; //this is the ip address where bbb stands
    private static boolean enableUserVideoSubtitle;

	private static IMessagingService messagingService;

    public static synchronized boolean reservePlaceToCreateGlobal(String roomName) {
        if (globalCalls.contains(roomName)) {
            log.debug("There's already a global call for room {}, no need to create a new one", roomName);
            return false;
        } else {
            log.debug("Reserving the place to create a global call for room {}", roomName);
            globalCalls.add(roomName);
            voiceConfToListenOnlyUsersMap.put(roomName, new VoiceConfToListenOnlyUsersMap(roomName));
            voiceConfToGlobalVideoUsersMap.put(roomName, new VoiceConfToGlobalVideoUsersMap(roomName));
            return true;
        }
    }

    public static synchronized void addGlobalAudioStream(String voiceConf, String globalAudioStreamName, Codec sipCodec, SipConnectInfo connInfo) {
        log.debug("Adding a global audio stream to room {}", voiceConf);
        roomToAudioStreamMap.put(voiceConf, globalAudioStreamName);
        roomToAudioCodecMap.put(voiceConf, sipCodec);
        log.debug("No KeepAlive for now...");
        //KeepGlobalAudioAlive globalAudioKeepAlive = new KeepGlobalAudioAlive(connInfo.getSocket(), connInfo, sipCodec.getCodecId());
        //globalAudioKeepAliverMap.put(voiceConf, globalAudioKeepAlive);
        //globalAudioKeepAlive.start();
    }

    public static synchronized String getGlobalAudioStream(String voiceConf) {
        return roomToAudioStreamMap.get(voiceConf);
    }

    public static synchronized void addGlobalVideoStream(String voiceConf, CallStream globalStream, SipConnectInfo connInfo) {
        log.debug("Adding a global video stream to room {} stream {}", voiceConf, globalStream.getBbbToFreeswitchStreamName());
        roomToVideoStreamMap.put(voiceConf, globalStream);
    }

    public static synchronized String getGlobalVideoStream(String voiceConf) {
        if(roomToVideoStreamMap.containsKey(voiceConf)) {
            return roomToVideoStreamMap.get(voiceConf).getFreeswitchToBbbStreamName();
        }
        else
            return null;
    }

    public static synchronized boolean removeRoomIfUnused(String voiceConf) {
        if (voiceConfToGlobalVideoUsersMap.containsKey(voiceConf) && voiceConfToGlobalVideoUsersMap.get(voiceConf).numUsers() <= 0) {
            removeRoom(voiceConf);
            return true;
        } else {
            return false;
        }
    }
 
    public static synchronized void removeRoom(String voiceConf) {
        log.debug("Removing global audio and video stream of room {}", voiceConf);
        voiceConfToListenOnlyUsersMap.remove(voiceConf);
        roomToAudioStreamMap.remove(voiceConf);
        roomToAudioCodecMap.remove(voiceConf);
        //KeepGlobalAudioAlive globalAudioKeepAlive = globalAudioKeepAliverMap.get(voiceConf);
        //globalAudioKeepAlive.halt();
        //globalAudioKeepAliverMap.remove(voiceConf);
        roomToVideoStreamMap.remove(voiceConf);
        globalCalls.remove(voiceConf);
        roomToVideoPresent.remove(voiceConf);
        removeVideoConfLogoStream(voiceConf, "","");
        voiceConfToFloorHolder.remove(voiceConf);
        voiceConfToVideoLogoTranscoder.remove(voiceConf);
    }

    public static synchronized void addUser(String clientId, String callerIdName,String userId, String voiceConf) throws GlobalCallNotFoundException {
    	if (voiceConfToListenOnlyUsersMap.containsKey(voiceConf)) {
    		VoiceConfToListenOnlyUsersMap map = voiceConfToListenOnlyUsersMap.get(voiceConf);
            map.addUser(clientId, callerIdName, userId);
    		int numUsers = map.numUsers();
    		log.debug("Adding new user to voiceConf [{}], current number of users on global stream is {}", voiceConf, numUsers);
        }else{
            log.debug("There's no global call agent for the room [{}]. User [{}] can't connect.", voiceConf, callerIdName);
            throw new GlobalCallNotFoundException("No Global Call Agent for the room "+voiceConf);
    	}
      
    }
    
    public static synchronized ListenOnlyUser removeUser(String clientId, String voiceConf) {
    	if (voiceConfToListenOnlyUsersMap.containsKey(voiceConf)) {
    		return voiceConfToListenOnlyUsersMap.get(voiceConf).removeUser(clientId);
    	}
    	return null;
    }

    public static synchronized void addUserToGlobalVideo(String clientId, String voiceConf){
        if (voiceConfToGlobalVideoUsersMap.containsKey(voiceConf)) {
            voiceConfToGlobalVideoUsersMap.get(voiceConf).addUser(clientId);
        }
    }

    public static synchronized void removeUserFromGlobalVideo(String clientId, String voiceConf){
        if (voiceConfToGlobalVideoUsersMap.containsKey(voiceConf)) {
            voiceConfToGlobalVideoUsersMap.get(voiceConf).removeUser(clientId);
            log.debug("Current Users in the Global Video: {} ",voiceConfToGlobalVideoUsersMap.get(voiceConf).numUsers());
        }
    }

    public static synchronized List<ListenOnlyUser> getListenOnlyUsers(String voiceConf){
        List<ListenOnlyUser> listeners;
        if(voiceConfToListenOnlyUsersMap.containsKey(voiceConf)) {
            VoiceConfToListenOnlyUsersMap map = voiceConfToListenOnlyUsersMap.get(voiceConf);
            return map.getListenOnlyUsers();
        }
        else {
            listeners = new ArrayList<ListenOnlyUser>(); //return an empty list
        }
        return listeners;
    }

    public static Codec getRoomAudioCodec(String roomName) {
        return roomToAudioCodecMap.get(roomName);
    }

    public static Codec getRoomVideoCodec(String roomName) {
        if(roomToVideoStreamMap.containsKey(roomName))
            return roomToVideoStreamMap.get(roomName).getSipCodec();
        else
            return null;
    }

    public static void createSDPVideoFile(String voiceconf, String sdp){
        sdpVideoPath = FileSystems.getDefault().getPath(sdpVideoFullPath + voiceconf+".sdp");

        Charset charset = Charset.forName("US-ASCII");
        try {
            BufferedWriter writer = Files.newBufferedWriter(sdpVideoPath,charset,fileOptions);
            writer.write(sdp, 0, sdp.length());
            writer.close();
            log.debug("SDP video file created at: "+sdpVideoPath.toString());
        } catch (IOException x) {
            log.debug("Failed to create SDP video file: "+sdpVideoPath.toString());
        }
    }

    public static void removeSDPVideoFile(String voiceconf){
        sdpVideoPath = FileSystems.getDefault().getPath(sdpVideoFullPath +voiceconf+".sdp");
        try {
            Files.deleteIfExists(sdpVideoPath);
        } catch (IOException e) {
            log.debug("Failed to remove SDP video file: "+sdpVideoPath.toString());
        }
    }

    public static String getSdpVideoPath(String voiceconf){
        return sdpVideoFullPath+voiceconf+".sdp";
    }

    public static boolean sdpVideoExists(String sdpFilePath) {
        return fileExists(sdpFilePath);
    }

    public static synchronized void setVideoPresent(String voiceconf, Boolean flag){
        /*
         * set current transcoder status
         */
        log.debug("setVideoPresent: "+flag);
        roomToVideoPresent.put(voiceconf, flag);
    }

    /**
     * Informs if the current room already has a global video transcoder
     * running. True when there's a VideoTranscoder running for this room.
     * False otherwise.
     * @param voiceconf
     */
    public static synchronized boolean isVideoPresent(String voiceconf){
        Boolean videoPresent;
        videoPresent = roomToVideoPresent.get(voiceconf);
        if (videoPresent == null) videoPresent = false;
        log.debug("videoPresent [voiceconf={}] ? {} ",voiceconf, videoPresent?"true (Transcoder already running. No need to start a new one)":"false");
        return videoPresent;
    }

    public static synchronized boolean isGlobalVideoAbleToRun(String voiceconf,String newFloorHolder){
        return !isVideoPresent(voiceconf) && isSipPhonePresent(voiceconf) && isSipVideoEnabled() && !isWebUser(newFloorHolder);
    }

    public static synchronized boolean isUserVideoAbleToRun(String voiceconf){
        return isSipPhonePresent(voiceconf) && isSipVideoEnabled();
    }

    public static synchronized void setSipPhonePresent(String voiceconf, Boolean flag){
        /*
         * set current sipPhone status
         */
        log.debug("setSipPhonePresent: "+flag);
        roomToSipPhonePresent.put(voiceconf, flag);
    }

    public static synchronized void setFloorHolder(String voiceconf, String userId, Boolean videoPresent){
        FloorHolder floorHolder;
        floorHolder = voiceConfToFloorHolder.get(voiceconf);
        if (floorHolder != null){
            log.debug("Contains key [{}]. ",voiceconf);
            floorHolder.setUserId(userId);
            floorHolder.setUserHasVideo(videoPresent);
        } else{
            log.debug("Doesn't contain key [{}]. ",voiceconf);
            floorHolder = new FloorHolder(userId,videoPresent);
            voiceConfToFloorHolder.put(voiceconf, floorHolder);
        }
        log.debug("setFlooHolder: {} [oldFloorHolder = {}]",userId,floorHolder.getUserId());
    }

    public static String getFloorHolderUserId(String voiceconf){
        FloorHolder floorHolder = voiceConfToFloorHolder.get(voiceconf);
        if (floorHolder != null)
            return floorHolder.getUserId();
        else return "";
    }

    public static boolean floorHolderHasVideo(String voiceconf){
        FloorHolder floorHolder = voiceConfToFloorHolder.get(voiceconf);
        if (floorHolder != null)
            return floorHolder.hasVideo();
        else return false;
    }

    public static boolean isFloorHolder(String voiceconf, String userId){
        return getFloorHolderUserId(voiceconf).equals(userId);
    }

    public static boolean isFloorHolderAnWebUser(String voiceconf){
        return isWebUser(getFloorHolderUserId(voiceconf));
    }
    /**
     * True every time floor holder changes, or his video status changes,
     * false otherwise.
     * The status of the floor holder is a boolean which determines if
     * he got video.
     * @param voiceconf
     * @param floorHolder
     * @param videoPresent
     * @return
     */
    public static boolean floorHolderChanged(String voiceconf, String floorHolder, Boolean videoPresent) {
        Boolean floorHolderChanged;
        Boolean floorHolderVideoStatusChanged;
        String oldFloorHolder;
        oldFloorHolder = getFloorHolderUserId(voiceconf);
        floorHolderVideoStatusChanged = floorHolderHasVideo(voiceconf) ^ videoPresent;
        if (oldFloorHolder == null || oldFloorHolder.isEmpty())
          floorHolderChanged = (floorHolder != null) && (!floorHolder.isEmpty());
        else
          floorHolderChanged = isWebUser(floorHolder) ^ isWebUser(oldFloorHolder);
        log.debug("FloorHolderChanged [voiceconf={}] ? {} [floorHolder={}, oldFloorHolder={}]",voiceconf, floorHolderChanged,floorHolder,oldFloorHolder);
        return floorHolderChanged || floorHolderVideoStatusChanged;
    }

    public static synchronized boolean shouldProbeGlobalVideo(String voiceconf,String floorHolder,Boolean videoPresent){
        return floorHolderChanged(voiceconf,floorHolder,videoPresent) && isVideoPresent(voiceconf) && globalCallHasVideo(videoPresent);
    }

    public static boolean globalCallHasVideo(Boolean videoPresent){
        return videoPresent;
    }

    private static synchronized boolean isSipPhonePresent(String voiceconf) {
        Boolean sipPhonePresent;
        sipPhonePresent = roomToSipPhonePresent.get(voiceconf);
        if (sipPhonePresent == null) sipPhonePresent = false;
        log.debug("SipPhonePresent [voiceconf={}] ? {}",voiceconf, sipPhonePresent);
        return sipPhonePresent;
    }

    public static boolean isSipVideoEnabled() {
        log.debug("SipVideoEnabled? {}",sipVideoEnabled?"Enabled":"Disabled");
        return sipVideoEnabled;
    }

    public static void setSipVideoEnabled(boolean flag){
        log.debug("Setting sip-video status: {} ",flag?"Enabled":"Disabled");
        sipVideoEnabled = flag;
    }

    private void validateResolution(String resolution) {
        log.debug("Validating sip video resolution: {}", resolution);
        switch(resolution) {
            case LOW_QUALITY:
            case MEDIUM_QUALITY:
            case HIGH_QUALITY: parseResolution(resolution);
                               break;
            //using the default resolution
            default: log.debug("****The resolution set in bigbluebutton-sip.properties is invalid. Using the default resolution.");
                     parseResolution(MEDIUM_QUALITY);
        }
    }

    private void parseResolution(String resolution) {
        String[] dimensions = resolution.split("x");
        sipVideoWidth = dimensions[0];
        sipVideoHeight = dimensions[1];
        log.debug("Sip Video Resolution is {}x{}", sipVideoWidth, sipVideoHeight);
    }

    public static String getGlobalVideoWidth() {
        log.debug("Getting sip video width: {} (Resolution is {}x{})", sipVideoWidth, sipVideoWidth, sipVideoHeight);
        return sipVideoWidth;
    }

    public static String getGlobalVideoHeight() {
        log.debug("Getting sip video heigth: {} (Resolution is {}x{})", sipVideoHeight, sipVideoWidth, sipVideoHeight);
        return sipVideoHeight;
    }

    public void setSipVideoResolution(String resolution) {
        validateResolution(resolution);
    }

    public void setSipVideoHeight(String height) {
        sipVideoHeight = height;
    }

    public void setVideoConfLogo(String filePath) {
        log.debug("Trying to set the temporary sip video image file to: {}", filePath);

        if(videoConfLogoExists(filePath)) {
           videoConfLogo = filePath;
           log.debug("Temporary sip video image file set to: {}", videoConfLogo);
        }
        else
           log.debug("Could NOT set {} as the temporary sip video image", filePath);
    }

    public static boolean videoConfLogoExists(String filePath) {
        return fileExists(filePath);
    }

    public void setFfmpegPath(String ffPath) {
        log.debug("Trying to set the ffmpeg path to: {}", ffPath);

        if(ffmpegExists(ffPath)) {
            ffmpegPath = ffPath;
            log.debug("ffmpeg path set to: {}", ffmpegPath);
         }
         else
            log.debug("****Could NOT set {} as the ffmpeg path", ffPath);
     }

     public static boolean ffmpegExists(String ffPath) {
         return fileExists(ffPath);
     }

    /**
     * Creates a video transcoder which sends a moving-logo
     * stream to the bbb-video app. This video will be played
     * by the clients of the webconference, everytime any webconference
     * user becomes the video floor holder. This avoids duplicated video
     * window.
     * @param voiceconf
     * @param meetingId
     * @return
     */
    public static boolean addVideoConfLogoStream(String voiceconf,String meetingId) {
        synchronized (voiceConfToVideoLogoTranscoder){
            if (voiceConfToVideoLogoTranscoder.containsKey(voiceconf)) {
                log.debug("There's already a videoconf-logo transcoder for room {}, no need to create a new one", voiceconf);
                return false;
            } else {
                log.debug("Reserving the place to create a video-logo transcoder for room {}", voiceconf);
                String videoConfLogoStreamName = VIDEOCONFLOGO_STREAM_NAME_PREFIX+voiceconf+"_"+System.currentTimeMillis();
                VideoTranscoder videoTranscoder = new VideoTranscoder(VideoTranscoder.Type.TRANSCODE_FILE_TO_RTMP,VIDEOCONFLOGO_STREAM_NAME_PREFIX+voiceconf,videoConfLogoStreamName,meetingId,voiceconf,getSipClientRtpIp());
                boolean startedSuccesfully = videoTranscoder.start();
                if (startedSuccesfully && (!meetingId.isEmpty()) && (messagingService != null)) {
                    messagingService.globalVideoStreamCreated(meetingId, videoConfLogoStreamName);
                    voiceConfToVideoLogoTranscoder.put(voiceconf,videoTranscoder);
                    return true;
                }
                log.debug("Could not start video conf logo stream");
                return false;
            }
        }
    }

    /**
     * Removes the current videoconf-logo stream (needed to avoid duplicated video
     * in 'Speaker' window). This method also restores the current global video
     * stream of the room, which will be displayed when we close the videoconf-logo stream.
     * @param voiceconf
     * @param meetingId
     * @param globalVideoStream
     * @return
     */
    public static boolean removeVideoConfLogoStream(String voiceconf, String meetingId, String globalVideoStream) {
        synchronized (voiceConfToVideoLogoTranscoder){
            if (voiceConfToVideoLogoTranscoder.containsKey(voiceconf)) {
                log.debug("Removing videoconf-logo transcoder for room {} ", voiceconf);
                VideoTranscoder videoTranscoder = voiceConfToVideoLogoTranscoder.remove(voiceconf);
                videoTranscoder.stop();
                if (!meetingId.isEmpty())
                    messagingService.globalVideoStreamCreated(meetingId, globalVideoStream);
                else
                    log.debug("There's no need to restore global video stream, room is being closed");
                return true;
            } else {
                log.debug("There is no videoconf-logo transcoder for room {} ", voiceconf);
                return false;
            }
        }
    }

    /**
     * Removes the current videoconf-logo stream. No stream is restored after
     * it's execution. This method should be called when we want to
     * stop the current videoconf-logo transcoder and stream.
     * @param voiceconf
     * @return
     */
    public static boolean removeVideoConfLogoStream(String voiceconf){
        return removeVideoConfLogoStream(voiceconf, "","");
    }

    /**
     * Removes the current videoconf-logo stream associated to the meeting of this
     * Call Agent belongs to. No stream is restored after
     * it's execution, but a blank global video stream is sent, to
     * force client to close Speaker window asap. This method should be called when we want to
     * stop the current videoconf-logo transcoder and stream.
     * @param ca
     * @return
     */
    public static boolean removeVideoConfLogoStream(CallAgent ca){
        if(ca==null) {
            log.debug("CallAgent is null. Can't remove videoconf logo stream");
            return false;
        }
        return removeVideoConfLogoStream(ca.getDestination(), ca.getMeetingId(),"");
    }


    private static boolean isWebUser(String userId){
        return userId.matches("\\w+_\\d+");
    }

    public static void setSipServerHost(String newIp){
        sipServerHost = newIp;
    }

    public static void setSipClientRtpIp(String newIp){
        sipClientRtpIp = newIp;
    }

    public void setEnableUserVideoSubtitle(boolean flag){
        enableUserVideoSubtitle = flag;
    }

    public static boolean isUserVideoSubtitleEnabled(){
        return enableUserVideoSubtitle;
    }

    public static String getSipServerHost(){
        return sipServerHost;
    }

    public static String getSipClientRtpIp(){
        return sipClientRtpIp;
    }

    public static void setMessagingService(IMessagingService service){
        messagingService = service;
    }

    private static boolean fileExists(String filePath) {
        if(filePath == null || filePath.isEmpty())
           return false;

        return new File(filePath).isFile();
    }

    public static boolean isGlobalCallAgent(String userId){
        return userId.startsWith(LISTENONLY_USERID_PREFIX);
    }

    public static boolean isVideoConfLogoStream(String videoStreamName){
        return ((videoStreamName != null) && (videoStreamName.startsWith(VIDEOCONFLOGO_STREAM_NAME_PREFIX)));
    }

    public static void restartVideoConfLogoStream(String voiceConf, String meetingId){
        removeVideoConfLogoStream(voiceConf);
        addVideoConfLogoStream(voiceConf, meetingId);
    }
}
