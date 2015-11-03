/**
* BigBlueButton open source conferencing system - http://www.bigbluebutton.org/
* 
* Copyright (c) 2012 BigBlueButton Inc. and by respective authors (see below).
*
* This program is free software; you can redistribute it and/or modify it under the
* terms of the GNU Lesser General Public License as published by the Free Software
* Foundation; either version 3.0 of the License, or (at your option) any later
* version.
* 
* BigBlueButton is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
* PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License along
* with BigBlueButton; if not, see <http://www.gnu.org/licenses/>.
*
*/
package org.bigbluebutton.voiceconf.sip;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.zoolu.sip.provider.*;
import org.zoolu.net.SocketAddress;
import org.slf4j.Logger;
import org.bigbluebutton.voiceconf.messaging.IMessagingService;
import org.bigbluebutton.voiceconf.red5.CallStreamFactory;
import org.bigbluebutton.voiceconf.red5.ClientConnectionManager;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;

/**
 * Class that is a peer to the sip server. This class will maintain
 * all calls to it's peer server.
 * @author Richard Alam
 *
 */
public class SipPeer implements SipRegisterAgentListener, CallAgentObserver {
    private static Logger log = Red5LoggerFactory.getLogger(SipPeer.class, "sip");

    private ClientConnectionManager clientConnManager;
    private CallStreamFactory callStreamFactory;
    
    private CallManager callManager = new CallManager();
    private IMessagingService messagingService;
    private SipProvider sipProvider;
    private String clientRtpIp;
    private SipRegisterAgent registerAgent;
    private final String id;
    private final ConferenceProvider confProvider;
    
    private boolean registered = false;
    private SipPeerProfile registeredProfile;

    public SipPeer(String id, String sipClientRtpIp, String host, int sipPort, 
			int startAudioPort, int stopAudioPort, int startVideoPort, int stopVideoPort, IMessagingService messagingService) {
        this.id = id;
        this.clientRtpIp = sipClientRtpIp;
        this.messagingService = messagingService;
        confProvider = new ConferenceProvider(host, sipPort, startAudioPort, stopAudioPort, startVideoPort, stopVideoPort);
        initSipProvider(host, sipPort);
    }
    
    private void initSipProvider(String host, int sipPort) {
        sipProvider = new SipProvider(host, sipPort);    
        sipProvider.setOutboundProxy(new SocketAddress(host)); 
        sipProvider.addSipProviderListener(new OptionMethodListener());    	
    }
    
    public void register(String username, String password) {
    	log.debug( "SIPUser register" );
        createRegisterUserProfile(username, password);
        if (sipProvider != null) {
        	registerAgent = new SipRegisterAgent(sipProvider, registeredProfile.fromUrl, 
        			registeredProfile.contactUrl, registeredProfile.username, 
        			registeredProfile.realm, registeredProfile.passwd);
        	registerAgent.addListener(this);
        	registerAgent.register(registeredProfile.expires, registeredProfile.expires/2, registeredProfile.keepaliveTime);
        }                              
    }
    
    private void createRegisterUserProfile(String username, String password) {    	    	
    	registeredProfile = new SipPeerProfile();
    	registeredProfile.audioPort = confProvider.getStartAudioPort();
            	
        String fromURL = "\"" + username + "\" <sip:" + username + "@" + confProvider.getHost() + ">";
        registeredProfile.username = username;
        registeredProfile.passwd = password;
        registeredProfile.realm = confProvider.getHost();
        registeredProfile.fromUrl = fromURL;
        registeredProfile.contactUrl = "sip:" + username + "@" + sipProvider.getViaAddress();
        if (sipProvider.getPort() != SipStack.default_port) {
        	registeredProfile.contactUrl += ":" + sipProvider.getPort();
        }		
        registeredProfile.keepaliveTime=8000;
        registeredProfile.acceptTime=0;
        registeredProfile.hangupTime=20;   
        
        log.debug( "SIPUser register : {}", fromURL );
        log.debug( "SIPUser register : {}", registeredProfile.contactUrl );
    }

    public void call(String clientId, String callerName, String userId,String destination,String meetingId, String serverIp) {
    	if (!registered) {
    		/* 
    		 * If we failed to register with FreeSWITCH, reject all calls right away.
    		 * This way the user will know that there is a problem as quickly as possible.
    		 * If we pass the call, it take more that 30seconds for the call to timeout
    		 * (in case FS is offline) and the user will be kept wondering why the call
    		 * isn't going through.
    		 */
    		log.warn("We are not registered to FreeSWITCH. However, we will allow {} to call {}.", callerName, destination);
//    		return;
    	}
        CallAgent ca = createCallAgent(clientId, userId,serverIp);
        ca.setMeetingId(meetingId);//set meetingId to use with fs->bbb video stream when call is accepted
        ca.call(callerName,userId, destination);
    	callManager.add(ca);
    }

	public void connectToGlobalStream(String clientId, String userId, String callerIdName, String destination,String serverIp) throws GlobalCallNotFoundException {
        CallAgent ca = createCallAgent(clientId,userId,serverIp);
        ca.connectToGlobalStream(clientId, userId, callerIdName, destination);
     	callManager.add(ca);
	}

    private CallAgent createCallAgent(String clientId, String userId, String serverIp) {
    	SipPeerProfile callerProfile = SipPeerProfile.copy(registeredProfile);
        CallAgent ca = new CallAgent(this.clientRtpIp, sipProvider, callerProfile, confProvider, clientId, userId, messagingService,serverIp);
        ca.setClientConnectionManager(clientConnManager);
    	ca.setCallStreamFactory(callStreamFactory);
        ca.setCallAgentObserver(this);

    	return ca;
    }

	public void close() {
		log.debug("SIPUser close1");
        try {
			unregister();
		} catch(Exception e) {
			log.error("close: Exception:>\n" + e);
		}

       log.debug("Stopping SipProvider");
       sipProvider.halt();
	}

    public void hangup(String userId, String callerIdName) {
        log.debug( "SIPUser hangup" );

        CallAgent ca = callManager.remove(userId);

        if (ca != null) {
            String destination = ca.getDestination();
            if (ca.isListeningToGlobal()) {
                log.info("User has disconnected from global audio, callerIdName [{}], user [{}] voiceConf {}", callerIdName, userId, destination);
                messagingService.userDisconnectedFromGlobalAudio(destination, callerIdName);

                log.info("Removing {} (clientId = {}) from the listen only users list", callerIdName, ca.getCallId());
                GlobalCall.removeUser(ca.getCallId(), destination);
            }
            if(ca.isGlobal()){
                 log.info("Hanging up (***** GLOBAL CALL *****) , callerIdName [{}], user [{}] for the room {} ", callerIdName, userId, destination);
                 GlobalCall.removeRoom(destination);
            }
            ca.hangup();
        }
    }

    public void unregister() {
    	log.debug( "SIPUser unregister" );

    	Collection<CallAgent> calls = callManager.getAll();
    	for (Iterator<CallAgent> iter = calls.iterator(); iter.hasNext();) {
    		CallAgent ca = (CallAgent) iter.next();
    		ca.hangup();
    	}

        if (registerAgent != null) {
            registerAgent.unregister();
            registerAgent = null;
        }
    }

    public void startBbbToFreeswitchAudioStream(String clientId, String userId, IBroadcastStream broadcastStream, IScope scope) {
        CallAgent ca = callManager.get(userId);
        String videoStream = callManager.getVideoStream(userId);
        log.debug("Starting Audio Stream for the user ["+userId+"]");
        if (ca != null) {
            ca.startBbbToFreeswitchAudioStream(broadcastStream, scope);
            if (videoStream != null){
                log.debug(" There's a VideoStream for this audio call, starting it ");
                ca.setVideoStreamName(videoStream);
                ca.startBbbToFreeswitchVideoStream();
            }else log.debug("There's no videostream for this flash audio call yet.");
        }
    }
    
    public void stopBbbToFreeswitchAudioStream(String userId, IBroadcastStream broadcastStream, IScope scope) {
        CallAgent ca = callManager.get(userId);

        if (ca != null) {
           ca.stopBbbToFreeswitchAudioStream(broadcastStream, scope);
       
        } else {
        	log.info("Can't stop talk stream as stream may have already been stopped.");
        }
    }
    
    public void setBbbToFreeswitchVideoStream(String userId, String videoStreamName) {
        if (videoStreamName.equals("")) {
            log.debug("setBbbToFreeswitchVideoStream without video stream name, trying to retrieve it from a previously saved state");
            videoStreamName = callManager.getVideoStream(userId);
            if (videoStreamName != null && !videoStreamName.equals("")) {
                log.debug("Retrieved successfully video stream name for {}, we're ready to go", userId);
            } else {
                log.debug("There's no saved video stream name for {}, no stream to begin", userId);
                return;
            }
        } else {
            log.debug("Saving video stream name for {}", userId);
            callManager.addVideoStream(userId, videoStreamName);
        }
        
        CallAgent ca = callManager.getByUserId(userId);
        if (ca != null) {
            if (ca.isGlobalStream()) {
                log.debug("This is a global CallAgent, there's no video stream to send from bbb to freeswitch");
                return;
            }
            if(ca.isListeningToGlobal()) {
                log.debug("This is a CallAgent from a listen only user (connected to global): there's no need to start the video stream.");
                return;
            }

            log.debug("There's a CallAgent and a video Stream running for this userId={}. This user is able to send video (when he becomes the floor holder).", userId);
            ca.setVideoStreamName(videoStreamName);

            //if this user is the current video floor, update his video (because he's sending the temporary video)
            if(ca.isVideoRunning()) {
                ca.stopBbbToFreeswitchVideoStream();
                ca.startBbbToFreeswitchVideoStream();
            }
            else{
                ca.stopBbbToFreeswitchVideoStream();
                changeCurrentVideoFloorToWebUser(ca);
            }
        } else {
            //ca null means that this method was called when publishing a video stream
            log.debug("Could not START BbbToFreeswitchVideoStream: there is no CallAgent with"
                       + " userId " + userId + ". Saving the current stream to be used when the CallAgent is created by this user");            
        }
    }

    public void stopBbbToFreeswitchVideoStream(String userId) {
        CallAgent ca = callManager.getByUserId(userId);
        if (ca != null) {
            ca.setVideoStreamName("");
            //if this user is the current video floor, start his video (Now we have to send the temporary video)
            if(ca.isVideoRunning()){
                ca.stopBbbToFreeswitchVideoStream();
                ca.startBbbToFreeswitchVideoStream();
            }else{
                ca.stopBbbToFreeswitchVideoStream();
            }
        }else
            log.debug("stopBbbToFreeswitchVideoStream: There's no call running for userId = {}: removing video stream only", userId);

        callManager.removeVideoStream(userId);
    }

    public void startFreeswitchToBbbGlobalVideoStream(String userId, Boolean videoPresent) {
        if (!videoPresent) return;
        synchronized (callManager){
        CallAgent ca = callManager.getByUserId(userId);
        if (ca != null){
            if(ca.isGlobalStream()){ //this MUST be a globalStream, because the global is the only one that sends video
                log.debug("Starting GlobalCall's freeswitch->bbb video stream");
                if(ca.startFreeswitchToBbbVideoStream())
                     GlobalCall.setVideoPresent(ca.getDestination(), true);
                else
                     log.debug("Could NOT set video present: startFreeswitchToBbbVideoStream() returned false");
            }
        }else log.debug("startFreeswitchToBbbGlobalVideoStream(): There's no global call agent for the user: "+userId);
    }
    }

    public void startFreeswitchToBbbGlobalVideoProbe(String userId, Boolean videoPresent) {
        if (!videoPresent) return;
        synchronized (callManager){
        CallAgent ca = callManager.getByUserId(userId);
        if (ca != null){
            if(ca.isGlobalStream()){ //this MUST be a globalStream, because the global is the only one that sends video
                log.debug("Probe GlobalCall's freeswitch->bbb video stream");
                ca.startFreeswitchToBbbVideoProbe();
            }
        }else log.debug("startFreeswitchToBbbGlobalVideoStream(): There's no global call agent for the user: "+userId);
        }
    }

    public void stopFreeswitchToBbbGlobalVideoStream(String userId) {
        synchronized (callManager){
        CallAgent ca = callManager.getByUserId(userId);
        if (ca != null) {
            if(ca.isGlobalStream()) {
                ca.stopFreeswitchToBbbGlobalVideoStream();
                messagingService.globalVideoStreamCreated(ca.getMeetingId(),"");
            }
        }
        else
            log.debug("Could not STOP FreeswitchToBbbGlobalVideoStream: there is no Global CallAgent with"
                       + "userId " + userId);
        }
    }

    public void webRTCCall(String clientId, String userId, String username, String destination, String meetingId, String remoteVideoPort, String localVideoPort, String serverIp) throws PeerNotFoundException {
        String callerName = username;
        CallAgent ca = createCallAgent(clientId,userId,serverIp);
        //ports and meetingId now saved in the CallAgent
        ca.setCallerName(callerName);
        ca.setUserNameFromCallerName(callerName);
        ca.setLocalVideoPort(localVideoPort);
        ca.setRemoteVideoPort(remoteVideoPort);
        ca.setMeetingId(meetingId);
        ca.setWebRTC(true);
        ca.setDestination(destination);
        callManager.add(ca); //webRTC's CallAgent
        log.debug("WebRTC's CallAgent created");
    }

	@Override
	public void onRegistrationFailure(String result) {
		log.error("Failed to register with Sip Server.");
		registered = false;
	}

	@Override
	public void onRegistrationSuccess(String result) {
		log.info("Successfully registered with Sip Server.");
		registered = true;
	}

	@Override
	public void onUnregistedSuccess() {
		log.info("Successfully unregistered with Sip Server");
		registered = false;
	}
	
	public void setCallStreamFactory(CallStreamFactory csf) {
		callStreamFactory = csf;
	}
	
	public void setClientConnectionManager(ClientConnectionManager ccm) {
		clientConnManager = ccm;
	}

	@Override
	public void handleCallAgentClosed(String clientId, String callerName, String userId, String destination, String meetingId, String serverIp) {
        /*
         * This observer is called every time we receive a BYE from sip server.
         * This means that if we receive a bye, but there's still a CallAgent, we
         * are dealing with an unexpected end of call (like ended by the other end point).
         * What we do is in this case is removing the current CallAgent from the
         * CallManager, leaving it to be recreated when a new call() is made by the user
         * (or by the GlobalCall)
         *
         */

        log.debug("handleCallAgentClosed(): CallAgent for the user [uid={}] has been closed.",userId);
        callManager.remove(userId);
        restartGlobalCall(clientId, callerName, userId, destination, meetingId, serverIp);
    }

    @Override
    public void handleCallAccepted(String userId, String destination, String meetingId){
        /**
         * After creating Global Call Agent, we must handle the current
         * video status in the conference, because there might be a sip-phone
         * in there.
         */

        if (GlobalCall.isGlobalCallAgent(userId)){
            log.debug("Global CallAgent running. Handling video.");
            requestUpdateVideoStatus(meetingId,destination);
        }
    }

    private void requestUpdateVideoStatus(String meetingId, String destination){
        if (messagingService != null){
            log.debug("Requesting updateVideoStatus for room={}, meetingId={}",destination, meetingId);
            messagingService.requestUpdateVideoStatus(meetingId, destination);
        }else {
            log.debug("Failed to request updateVideoStatus for room={}, meetingId={}",destination,meetingId);
        }

    }

    public void restartGlobalCall(String clientId, String callerName, String userId, String destination, String meetingId, String serverIp){
    synchronized(callManager){
        if (callManager.get(userId)== null){ //avoids RC if another user joins the room and call createGlobalCall() in Application.java before this gets done
            log.debug("Restarting Global Call [clientId={}] [callerName={}] [userId={}] [destination={}] [meetingId={}]",clientId, callerName, userId, destination, meetingId);
            createGlobalCall(clientId, callerName, userId, destination, meetingId, serverIp);
        }else log.debug("Cannot restart Global Call. There's already a global call agent for this room");
    }
    }

    public void createGlobalCall(String clientId, String callerName, String userId,String destination,String meetingId, String serverIp){
        if (GlobalCall.reservePlaceToCreateGlobal(destination))
            log.debug("Global Call Recreated.");
        log.debug("GlobalCall's info exists. Remaking globalCall's call");
        this.call(clientId, callerName, userId, destination, meetingId, serverIp);
    }

    public void startCurrentFloorVideo(String voiceBridge, String userId, Boolean videoPresent, String meetingId) {
        log.debug("Starting the video stream for uid={}, voiceBridge={}", userId,voiceBridge);
        CallAgent ca;

        if (userId.isEmpty()){
            //when empty, we try to retrieve current floor, to start his video
            ca = callManager.getByUserId(GlobalCall.getFloorHolderUserId(voiceBridge));
        }else ca = callManager.getByUserId(userId);

        if (ca != null && !ca.isGlobalStream() && !ca.isListeningToGlobal()) {
            if(ca.getDestination().equals(voiceBridge)) {
                log.debug("startBbbToVideoStream: starting video stream for {} (videoStreamName = {})",ca.getUserId(), ca.getVideoStreamName());
                changeCurrentVideoFloorToWebUser(ca);
            }
            else log.debug("Could not start sip video for {} cause this user has different voiceBridge ({})", ca.getUserId(), ca.getDestination());
        }
        else {
            log.debug("Could not start sip video for {}, CA is null, global or listen only", userId);
            log.debug("Changing current video floor to a non-web user, conference={}, uid={}",voiceBridge,userId);
            changeCurrentVideoFloorToPhoneUser(voiceBridge,userId,videoPresent,meetingId);
        }

    }

    /**
     * This method stops the current floor video and videoconf-logo transcoder
     * (if there is any for this user). Unlikely changeCurrentVideoFloor() method,
     * no global-video is restored after this execution
     * @param voiceBridge
     * @param meetingId
     */
    public void stopCurrentFloorVideo(String voiceBridge,String meetingId) {
        stopCurrentFloorVideo(voiceBridge);
        updateCurrentFloorVideo(voiceBridge,"",false);
        GlobalCall.removeVideoConfLogoStream(voiceBridge, meetingId,"");
    }

    /**
     * Change current video floor to an webconference user
     * (who has an associated call-agent).
     *  This method stops the current floor and logo streams (if there is any),
     *  updates it and start the new floor holder video.
     * @param ca
     */
    public void changeCurrentVideoFloorToWebUser(CallAgent ca){
        if (ca == null) return;
        synchronized (callManager){
            if (GlobalCall.floorHolderChanged(ca.getDestination(),ca.getUserId(),true) || !ca.isVideoRunning()){
                log.debug("TEST HOLD");
                if (!GlobalCall.isFloorHolder(ca.getDestination(),ca.getUserId()) && !ca.isVideoRunning()){
                    log.debug("Stopping floor video, because this is not the current user");
                    stopCurrentFloorVideo(ca.getDestination());
                }
                updateCurrentFloorVideo(ca.getDestination(),ca.getUserId(),true); //webuser always has video
                if(startNewVideoFloor(ca))
                    GlobalCall.addVideoConfLogoStream(ca.getDestination(),ca.getMeetingId());
                log.debug("TEST RELEASE");
            }else{
                log.debug("Video floor holder still is the same. Nothing to do about his video...");
            }
        }
    }

    /**
     * Change current video floor to an video-conference user
     * (or non-webconference user, who doesn't have any associated
     * call agent).
     * This method stops the current floor and logo streams (if there is any),
     * updates it and start the video-conf logo's video.
     * @param voiceBridge
     * @param userId
     * @param meetingId
     */
    private void changeCurrentVideoFloorToPhoneUser(String voiceBridge, String userId , Boolean videoPresent, String meetingId){
        synchronized (callManager){
            if (GlobalCall.floorHolderChanged(voiceBridge,userId,videoPresent)){
                log.debug("TEST HOLD");
                CallAgent ca = callManager.getGlobalCallAgent(voiceBridge);
                if (ca == null){
                    log.debug("Global Call Agent hasn't been created yet for room={}. Global Video transcoder will start when it is created [currentFloor = {\"\"} (not updated)]",voiceBridge,GlobalCall.getFloorHolderUserId(voiceBridge));
                    return;
                }
                stopCurrentFloorVideo(voiceBridge);
                updateCurrentFloorVideo(voiceBridge,userId,videoPresent);
                if (videoPresent)
                    GlobalCall.removeVideoConfLogoStream(voiceBridge, meetingId, (ca!=null) ? ca.getVideoStreamName():"");
                else GlobalCall.addVideoConfLogoStream(ca.getDestination(),ca.getMeetingId());
                log.debug("TEST RELEASE");
            }else{
                log.debug("Video floor holder still is the same. Nothing to do about his video...");
            }
        }
    }

    private void updateCurrentFloorVideo(String voiceBridge, String userId, Boolean videoPresent){
        GlobalCall.setFloorHolder(voiceBridge,userId, videoPresent);
    }

    private void stopCurrentFloorVideo(String voiceBridge){
        log.debug("Stopping current video floor for the conference {}", voiceBridge);
        String userId = GlobalCall.getFloorHolderUserId(voiceBridge);
        CallAgent ca;
        if ((userId != null) && (!userId.equals(""))){
            log.debug("{} is the current video floor holder, stopping his video.", userId);
            ca = callManager.get(userId);
            if((ca != null) && (ca.isVideoRunning())){
                ca.stopBbbToFreeswitchVideoStream();
            }else log.debug("Can't stop current floor video: There's no callAgent associated to this user uid={} ", userId);
        }else log.debug("There's no video floor holder for this conference, at this moment. Conference={}",voiceBridge);
    }

    /**
     * Start the video of the current floor.
     * True when it starts, false otherwise.
     * If the video is running already, returns  true.
     * @param ca
     * @return
     */
    private boolean startNewVideoFloor(CallAgent ca){
        if (ca == null) return false;
        log.debug("{} is the new video floor holder of the room {}. Starting it's transcoder",ca.getUserId(),ca.getDestination());
        ca.startBbbToFreeswitchVideoStream();
        return ca.isVideoRunning();
    }


}
