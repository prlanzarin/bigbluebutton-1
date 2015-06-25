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

    private static Set<String> globalCalls = new HashSet<String>();
    private static Map<String,String> roomToAudioStreamMap = new ConcurrentHashMap<String, String>();
    private static Map<String,Codec> roomToAudioCodecMap = new ConcurrentHashMap<String, Codec>();
    //private static Map<String,KeepGlobalAudioAlive> globalAudioKeepAliverMap = new ConcurrentHashMap<String, KeepGlobalAudioAlive>();
    private static Map<String,CallStream> roomToVideoStreamMap = new ConcurrentHashMap<String, CallStream>();
    private static Map<String,Boolean> roomToVideoPresent = new ConcurrentHashMap<String,Boolean>();
    private static Map<String, VoiceConfToListenOnlyUsersMap> voiceConfToListenOnlyUsersMap = new ConcurrentHashMap<String, VoiceConfToListenOnlyUsersMap>();
    private static Path sdpVideoPath;
    public static final String GLOBAL_AUDIO_STREAM_NAME_PREFIX = "GLOBAL_AUDIO_";
    public static final String GLOBAL_VIDEO_STREAM_NAME_PREFIX = "sip_";
    public static final String LISTENONLY_USERID_PREFIX = "GLOBAL_CALL_"; //when changed, must also change ESLEventListener.java in bigbluebutton-apps
    private static final String sdpVideoFullPath = "/tmp/"+GLOBAL_VIDEO_STREAM_NAME_PREFIX; //when changed , must also change VideoApplication.java in bbb-video
    private static OpenOption[] fileOptions = new OpenOption[] {StandardOpenOption.CREATE,StandardOpenOption.WRITE};

    
    public static synchronized boolean reservePlaceToCreateGlobal(String roomName) {
        if (globalCalls.contains(roomName)) {
            log.debug("There's already a global call for room {}, no need to create a new one", roomName);
            return false;
        } else {
            log.debug("Reserving the place to create a global call for room {}", roomName);
            globalCalls.add(roomName);
            voiceConfToListenOnlyUsersMap.put(roomName, new VoiceConfToListenOnlyUsersMap(roomName));
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
        if (voiceConfToListenOnlyUsersMap.containsKey(voiceConf) && voiceConfToListenOnlyUsersMap.get(voiceConf).numUsers() <= 0) {
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

    public static synchronized void addUser(String clientId, String callerIdName,String userId, String voiceConf, boolean listeningToAudio) throws GlobalCallNotFoundException {
    	if (voiceConfToListenOnlyUsersMap.containsKey(voiceConf)) {
    		VoiceConfToListenOnlyUsersMap map = voiceConfToListenOnlyUsersMap.get(voiceConf);
            map.addUser(clientId, callerIdName, userId,listeningToAudio);
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

    public static synchronized void updateUserListeningStatus(String clientId, boolean newStatus, String voiceConf) {
       if (voiceConfToListenOnlyUsersMap.containsKey(voiceConf)) {
           voiceConfToListenOnlyUsersMap.get(voiceConf).setUserListeningStatus(clientId, newStatus);
           log.debug("ListenOnlyUser with clientId {} has listeningToAudio = {}", clientId, voiceConfToListenOnlyUsersMap.get(voiceConf).getListenOnlyUser(clientId).listeningToAudio);
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
        log.debug("Current videoPresent: "+ videoPresent);
        return videoPresent;
    }

}
