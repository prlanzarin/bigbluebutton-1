package org.bigbluebutton.voiceconf.sip;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;

import org.bigbluebutton.voiceconf.messaging.IMessagingService;
import org.bigbluebutton.voiceconf.red5.media.CallStream;
import org.red5.app.sip.codecs.Codec;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class GlobalCall {
    private static final Logger log = Red5LoggerFactory.getLogger( GlobalCall.class, "sip" );

    // Configure hashmap properly (ralam sept 1, 2015)
    // https://ria101.wordpress.com/2011/12/12/concurrenthashmap-avoid-a-common-misuse/
    //
    private static Set<String> globalCalls = new HashSet<String>();
    private static Map<String,String> roomToAudioStreamMap = new ConcurrentHashMap<String, String>();
    private static Map<String,Codec> roomToAudioCodecMap = new ConcurrentHashMap<String, Codec>();
    //private static Map<String,KeepGlobalAudioAlive> globalAudioKeepAliverMap = new ConcurrentHashMap<String, KeepGlobalAudioAlive>();

    private static Map<String, VoiceConfToListenOnlyUsersMap> voiceConfToListenOnlyUsersMap = new ConcurrentHashMap<String, VoiceConfToListenOnlyUsersMap>(8, 0.9f, 1);

    private static Map<String, VoiceConfToGlobalVideoUsersMap> voiceConfToGlobalVideoUsersMap = new ConcurrentHashMap<String, VoiceConfToGlobalVideoUsersMap>();

    public static final String GLOBAL_AUDIO_STREAM_NAME_PREFIX = "GLOBAL_AUDIO_";

    public static final String LISTENONLY_USERID_PREFIX = "GLOBAL_CALL_"; //when changed, must also change ESLEventListener.java in bigbluebutton-apps

    private static boolean sipVideoEnabled = false;

    private static String sipServerHost;
    private static String sipClientRtpIp; //this is the ip address where bbb stands

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
        globalCalls.remove(voiceConf);
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

    public static boolean isSipVideoEnabled() {
        log.debug("SipVideoEnabled? {}",sipVideoEnabled?"Enabled":"Disabled");
        return sipVideoEnabled;
    }

    public static void setSipVideoEnabled(boolean flag){
        log.debug("Setting sip-video status: {} ",flag?"Enabled":"Disabled");
        sipVideoEnabled = flag;
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

    public static String getSipServerHost(){
        return sipServerHost;
    }

    public static String getSipClientRtpIp(){
        return sipClientRtpIp;
    }

    public static void setMessagingService(IMessagingService service){
        messagingService = service;
    }

    public static boolean isGlobalCallAgent(String userId){
        return userId.startsWith(LISTENONLY_USERID_PREFIX);
    }

}
