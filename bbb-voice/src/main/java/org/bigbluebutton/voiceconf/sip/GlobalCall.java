package org.bigbluebutton.voiceconf.sip;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.bigbluebutton.voiceconf.red5.media.CallStream;
import org.red5.app.sip.codecs.Codec;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

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
    private static Map<String, String> voiceConfToFloorHolder = new ConcurrentHashMap<String, String>();
    private static Path sdpVideoPath;
    public static final String GLOBAL_AUDIO_STREAM_NAME_PREFIX = "GLOBAL_AUDIO_";
    public static final String GLOBAL_VIDEO_STREAM_NAME_PREFIX = "sip_";
    public static final String LISTENONLY_USERID_PREFIX = "GLOBAL_CALL_"; //when changed, must also change ESLEventListener.java in bigbluebutton-apps
    private static final String sdpVideoFullPath = "/tmp/"+GLOBAL_VIDEO_STREAM_NAME_PREFIX; //when changed , must also change VideoApplication.java in bbb-video
    private static OpenOption[] fileOptions = new OpenOption[] {StandardOpenOption.CREATE,StandardOpenOption.WRITE};

    private static boolean sipVideoEnabled = false;

    public static String sipVideoWidth;
    public static String sipVideoHeight;

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

    public static synchronized void setVideoPresent(String voiceconf, Boolean flag){
        /*
         * set current transcoder status
         */
        log.debug("setVideoPresent: "+flag);
        roomToVideoPresent.put(voiceconf, flag);
    }

    public static synchronized boolean isVideoPresent(String voiceconf){
        Boolean videoPresent;
        videoPresent = roomToVideoPresent.get(voiceconf);
        if (videoPresent == null) videoPresent = false;
        log.debug("videoPresent [voiceconf={}] ? {} ",voiceconf, videoPresent?"true (Transcoder already running. No need to start a new one)":"false");
        return videoPresent;
    }

    public static synchronized boolean isGlobalVideoAbleToRun(String voiceconf){
        return !isVideoPresent(voiceconf) && isSipPhonePresent(voiceconf) && isSipVideoEnabled();
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

    public static synchronized void setFlooHolder(String voiceconf, String floorHolder){
        log.debug("setFlooHolder: {} [oldFloorHolder = {}]",floorHolder,voiceConfToFloorHolder.get(voiceconf));
        voiceConfToFloorHolder.put(voiceconf, floorHolder);
    }

    public static boolean floorHolderChanged(String voiceconf, String floorHolder) {
        Boolean floorHolderChanged;
        String oldFloorHolder;
        oldFloorHolder = voiceConfToFloorHolder.get(voiceconf);
        if (oldFloorHolder == null)
          floorHolderChanged = true;
        else
          floorHolderChanged = !oldFloorHolder.equals(floorHolder);
        log.debug("FloorHolderChanged [voiceconf={}] ? {} [floorHolder={}, oldFloorHolder={}]",voiceconf, floorHolderChanged,floorHolder,oldFloorHolder);
        return floorHolderChanged;
    }

    public static synchronized boolean shouldProbeGlobalVideo(String voiceconf,String floorHolder){
        return floorHolderChanged(voiceconf,floorHolder) && isVideoPresent(voiceconf);
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
            default: parseResolution(MEDIUM_QUALITY);
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
        this.sipVideoHeight = height;
    }
}
