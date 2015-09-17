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

import org.zoolu.sip.call.*;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.sip.provider.SipStack;
import org.zoolu.sip.message.*;
import org.zoolu.sdp.*;
import org.bigbluebutton.voiceconf.messaging.IMessagingService;
import org.bigbluebutton.voiceconf.red5.CallStreamFactory;
import org.bigbluebutton.voiceconf.red5.ClientConnectionManager;
import org.bigbluebutton.voiceconf.red5.media.CallStream;
import org.bigbluebutton.voiceconf.red5.media.CallStreamObserver;
import org.bigbluebutton.voiceconf.red5.media.StreamException;
import org.bigbluebutton.voiceconf.util.StackTraceUtil;
import org.red5.app.sip.codecs.Codec;
import org.red5.app.sip.codecs.CodecUtils;
import org.slf4j.Logger;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.Red5;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Vector;
import java.util.Map;

import org.bigbluebutton.voiceconf.red5.media.transcoder.VideoTranscoder;
import org.bigbluebutton.voiceconf.red5.media.transcoder.VideoTranscoderObserver;

public class CallAgent extends CallListenerAdapter implements CallStreamObserver, VideoTranscoderObserver{
    private static Logger log = Red5LoggerFactory.getLogger(CallAgent.class, "sip");
    
    private final SipPeerProfile userProfile;
    private final SipProvider sipProvider;
    private final String clientRtpIp;
    private ExtendedCall call;
    private CallStream audioCallStream; 
    private CallStream videoCallStream;    
    private String localSession = null;
    private Codec sipAudioCodec = null;
    private CallStreamFactory callStreamFactory;
    private ClientConnectionManager clientConnManager; 
    private final String clientId;
    private final ConferenceProvider portProvider;
    private DatagramSocket localAudioSocket;
    private DatagramSocket localVideoSocket;
    private String _callerName;
    private String _userId;
    private String _destination;
    private String _meetingId;
    private Boolean listeningToGlobal = false;
    private IMessagingService messagingService;
    private String _videoStreamName;
    private final String serverIp;
    private String localVideoPort;
    private String remoteVideoPort;
    private boolean isWebRTC = false;
    private boolean isGlobal = false;
    private boolean isVideoRunning = false;
    private CallAgentObserver callAgentObserver;

    private String destinationPrefix;

    private VideoTranscoder videoTranscoder = null;

    private enum CallState {
    	UA_IDLE(0), UA_INCOMING_CALL(1), UA_OUTGOING_CALL(2), UA_ONCALL(3);    	
    	private final int state;
    	CallState(int state) {this.state = state;}
    	private int getState() {return state;}
    }

    private CallState callState;

    private Map<String, String> currentVideoProbe;

    public String getDestination() {
        return _destination;
    }

    public void setDestination(String voiceConf) {
        _destination = voiceConf;
        log.debug("CallAgent destination attribute set to {} for the user {}", _destination, _callerName);

    }

    public CallAgent(String sipClientRtpIp, SipProvider sipProvider, SipPeerProfile userProfile,
		ConferenceProvider portProvider, String clientId, String userId, IMessagingService messagingService, String serverIp) {
        this.sipProvider = sipProvider;
        this.clientRtpIp = sipClientRtpIp;
        this.userProfile = userProfile;
        this.portProvider = portProvider;
        this.clientId = clientId;
        this._userId = userId;
        this.messagingService = messagingService;
        this.serverIp = serverIp;
        this.userProfile.userID = this._userId;
        this.isGlobal = isGlobalUserId();
        this.localVideoSocket = null;
        this.currentVideoProbe = null;
        this._videoStreamName = "";
    }
    
    public String getCallId() {
    	return clientId;
    }
    
    private void initSessionDescriptor() {        
        log.debug("initSessionDescriptor => userProfile.videoPort = " + userProfile.videoPort + " userProfile.audioPort =" + userProfile.audioPort);
        SessionDescriptor newSdp = SdpUtils.createInitialSdp(userProfile.username, 
        		this.clientRtpIp, userProfile.audioPort, 
        		userProfile.videoPort, userProfile.audioCodecsPrecedence, isGlobalStream() );
        localSession = newSdp.toString();        
        log.debug("localSession Descriptor = " + localSession );
    }

    public Boolean isListeningToGlobal() {
        return listeningToGlobal;
    }

    public void call(String callerName, String userId, String destination) {
    	_callerName = callerName;
        _userId = userId;
    	_destination = destination;
    	log.debug("{} making a call to {}", callerName, destination);  
    	try {

			localAudioSocket = getLocalAudioSocket();
			userProfile.audioPort = localAudioSocket.getLocalPort();

            localVideoSocket = getLocalVideoSocket();
            userProfile.videoPort = localVideoSocket.getLocalPort();

		} catch (Exception e) {
			log.debug("{} failed to allocate local port for call to {}. Notifying client that call failed.", callerName, destination); 
			notifyListenersOnOutgoingCallFailed();
			return;
		}    	
    	
		setupCallerDisplayName(callerName, destination);	
    	userProfile.initContactAddress(sipProvider);        
        initSessionDescriptor();
        
    	callState = CallState.UA_OUTGOING_CALL;
    	
        call = new ExtendedCall(sipProvider, userProfile.fromUrl, 
                userProfile.contactUrl, userProfile.username,
                userProfile.realm, userProfile.passwd, this);  
        
        // In case of incomplete url (e.g. only 'user' is present), 
        // try to complete it.       
        destination = sipProvider.completeNameAddress(destination).toString();
        log.debug("call {}", destination);  
        if (userProfile.noOffer) {
            call.call(destination);
        } else {
            call.call(destination, localSession);
        }
    }

    private void setupCallerDisplayName(String callerName, String destination) {
        destinationPrefix = destination;
    	String fromURL = "\"" + callerName + "\" <sip:" + destination + "@" + portProvider.getHost() + ">";
    	userProfile.username = callerName;
    	userProfile.fromUrl = fromURL;
		userProfile.contactUrl = "sip:" + destination + "@" + sipProvider.getViaAddress();
        if (sipProvider.getPort() != SipStack.default_port) {
            userProfile.contactUrl += ":" + sipProvider.getPort();
        }
        log.debug("userID: " + userProfile.userID);
    }
    
    /** Closes an ongoing, incoming, or pending call */
    public void hangup() {
    	if (callState == CallState.UA_IDLE) return;
    	log.debug("hangup");
    	if (listeningToGlobal) {
    		log.debug("Hanging up of a call connected to the global audio stream");
    		notifyListenersOfOnCallClosed();
    	} else {
            closeAudioStream();
            stopVideoTranscoder();
    		if (call != null) call.hangup();
    	}
    	callState = CallState.UA_IDLE; 
    }

    private DatagramSocket getLocalAudioSocket() throws Exception {
    	DatagramSocket socket = null;
    	boolean failedToGetSocket = true;
    	StringBuilder failedPorts = new StringBuilder("Failed ports: ");
    	
    	for (int i = portProvider.getStartAudioPort(); i <= portProvider.getStopAudioPort(); i++) {
    		int freePort = portProvider.getFreeAudioPort();
    		try {    			
        		socket = new DatagramSocket(freePort);
        		failedToGetSocket = false;
        		log.info("Successfully setup local audio port {}. {}", freePort, failedPorts);
        		break;
    		} catch (SocketException e) {
    			failedPorts.append(freePort + ", ");   			
    		}
    	}
    	
    	if (failedToGetSocket) {
			log.warn("Failed to setup local audio port {}.", failedPorts); 
    		throw new Exception("Exception while initializing CallStream");
    	}
    	
    	return socket;
    }

    public boolean isGlobalStream() {
        return (_callerName != null && _callerName.startsWith(GlobalCall.LISTENONLY_USERID_PREFIX));
    }

    private DatagramSocket getLocalVideoSocket() throws Exception {
        DatagramSocket socket = null;
        boolean failedToGetSocket = true;
        StringBuilder failedPorts = new StringBuilder("Failed ports: ");
        
        for (int i = portProvider.getStartVideoPort(); i <= portProvider.getStopVideoPort(); i++) {
            int freePort = portProvider.getFreeVideoPort();
            try {               
                socket = new DatagramSocket(freePort);
                failedToGetSocket = false;
                log.info("Successfully setup local VIDEO port {}. {}", freePort, failedPorts);
                break;
            } catch (SocketException e) {
                failedPorts.append(freePort + ", ");            
            }
        }
        
        if (failedToGetSocket) {
            log.warn("Failed to setup local VIDEO port {}.", failedPorts); 
            throw new Exception("Exception while initializing CallStream");
        }
        
        return socket;
    }
    
    private void createStreams() {
        boolean audioStreamCreatedSuccesfully = false;

        SessionDescriptor localSdp = new SessionDescriptor(call.getLocalSessionDescriptor());        
        SessionDescriptor remoteSdp = new SessionDescriptor(call.getRemoteSessionDescriptor());
        String remoteMediaAddress = SessionDescriptorUtil.getRemoteMediaAddress(remoteSdp);

        if (audioCallStream == null) {            
            int remoteAudioPort = SessionDescriptorUtil.getRemoteMediaPort(remoteSdp, SessionDescriptorUtil.SDP_MEDIA_AUDIO);
            int localAudioPort = SessionDescriptorUtil.getLocalMediaPort(localSdp, SessionDescriptorUtil.SDP_MEDIA_AUDIO);
            audioStreamCreatedSuccesfully = createAudioStream(remoteMediaAddress,localAudioPort,remoteAudioPort); 
                    	
        }else log.debug("AUDIO application is already running.");
        
        if (videoCallStream == null) {        
            remoteVideoPort = Integer.toString(SessionDescriptorUtil.getRemoteMediaPort(remoteSdp, SessionDescriptorUtil.SDP_MEDIA_VIDEO));
            localVideoPort = Integer.toString(SessionDescriptorUtil.getLocalMediaPort(localSdp, SessionDescriptorUtil.SDP_MEDIA_VIDEO));
            log.debug("Video stream port-setup done");
        }else log.debug("VIDEO application is already running.");

        if(audioStreamCreatedSuccesfully && !isGlobalStream()) {

            String userSenderAudioStream = audioCallStream.getBbbToFreeswitchStreamName();
            String userReceiverAudioStream = audioCallStream.getFreeswitchToBbbStreamName();

            notifyListenersOnCallConnected(userSenderAudioStream, userReceiverAudioStream);
        }
    }

    private boolean createAudioStream(String remoteMediaAddress, int localAudioPort, int remoteAudioPort) {

       SipConnectInfo connInfo = new SipConnectInfo(localAudioSocket, remoteMediaAddress, remoteAudioPort);
       try {

            localAudioSocket.connect(InetAddress.getByName(remoteMediaAddress), remoteAudioPort);

            if (userProfile.audio && localAudioPort != 0 && remoteAudioPort != 0) {
                if ((audioCallStream == null) && (sipAudioCodec != null)) {                  
                    try {
                        log.debug("Creating AUDIO stream: [localAudioPort=" + localAudioPort + ",remoteAudioPort=" + remoteAudioPort + "]");
                        audioCallStream = callStreamFactory.createCallStream(sipAudioCodec, connInfo, isGlobalStream());
                        audioCallStream.addCallStreamObserver(this);
                        audioCallStream.start();

                        if (isGlobalStream())
                        {
                        	GlobalCall.addGlobalAudioStream(_destination, audioCallStream.getFreeswitchToBbbStreamName(), sipAudioCodec, connInfo);
                        }

                        return true;

                    } catch (Exception e) {
                        log.error("Failed to create AUDIO Call Stream.");
                        System.out.println(StackTraceUtil.getStackTrace(e));
                    }                
                }
            }

        } catch (UnknownHostException e1) {
            log.error("Failed to connect for AUDIO Stream.");
            log.error(StackTraceUtil.getStackTrace(e1));
        }

        return false;

    }

    private synchronized void beginGlobalVideoTranscoding() {
        if (videoTranscoder == null){
            try {
                prepareGlobalVideoTranscoder();
                setVideoStreamName(GlobalCall.GLOBAL_VIDEO_STREAM_NAME_PREFIX + getDestination() +"_"+System.currentTimeMillis());
                // Free local port before starting ffmpeg
                localVideoSocket.close();
                startGlobalVideoTranscoder();
                //videoCallStream.startBbbToFreeswitchStream(broadcastStream, scope);
                messagingService.globalVideoStreamCreated(getMeetingId(),getVideoStreamName());
            } catch (Exception e) {
                log.debug("Failed to start FFMPEG for global video (fs->bbb) stream");
                e.printStackTrace();
            }
        }else
            log.debug("No need to start User's Video Transcoder [uid={}], it is already running.",getUserId());
    }

    private void prepareGlobalVideoTranscoder(){
        SessionDescriptor localSdp = new SessionDescriptor(call.getLocalSessionDescriptor());
        String sdpVideo = SessionDescriptorUtil.getLocalVideoSDP(localSdp);
        GlobalCall.createSDPVideoFile(getDestination(), sdpVideo);
    }

    private synchronized void startGlobalVideoTranscoder(){
        if (videoTranscoder == null){
            videoTranscoder = new VideoTranscoder(VideoTranscoder.Type.TRANSCODE_RTP_TO_RTMP,
                    GlobalCall.getSdpVideoPath(getDestination()),getVideoStreamName(),getMeetingId(),getServerIp());
            videoTranscoder.setVideoTranscoderObserver(this);
            setVideoRunning(true);
            videoTranscoder.start();
        }else
            log.debug("No need to start Global Video Transcoder [uid={}], it is already running.",getUserId());
    }

   public void startBbbToFreeswitchAudioStream(IBroadcastStream broadcastStream, IScope scope) {
    	try {
			audioCallStream.startBbbToFreeswitchStream(broadcastStream, scope);

		} catch (StreamException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
       
    public void stopBbbToFreeswitchAudioStream(IBroadcastStream broadcastStream, IScope scope) {
    	if (audioCallStream != null) {
    		audioCallStream.stopBbbToFreeswitchStream(broadcastStream, scope);   	
    	} else {
    		log.info("Can't stop talk stream as stream may have already stopped.");
    	}
    }

    public void startBbbToFreeswitchVideoStream(){
        if (!GlobalCall.isUserVideoAbleToRun(getDestination())){
            log.debug("User's video transcoder won't start because there's no need to (check previous log message)");
            return;
        }

        if (isWebRTC())
             startBbbToFreeswitchWebRTCVideoStream();
        else
            startBbbToFreeswitchFlashVideoStream();
    }

    public void stopBbbToFreeswitchVideoStream(){
        if (isWebRTC())
            stopBbbToFreeswitchWebRTCVideoStream();
       else
           stopBbbToFreeswitchFlashVideoStream();
    }

    private void startBbbToFreeswitchFlashVideoStream() {
        VideoTranscoder.Type type;
        if (_videoStreamName.equals("")){
            log.debug("startBbbToFreeswitchFlashVideoStream: There's no videoStream for this FlashCall. Starting TEMPORARY video");
            type = VideoTranscoder.Type.TRANSCODE_FILE_TO_RTP;
        }
        else {
            log.debug("startBbbToFreeswitchFlashVideoStream: Starting {}", _videoStreamName);
            type = VideoTranscoder.Type.TRANSCODE_RTMP_TO_RTP;
        }

        //start flash video transcoder
        try {
            SessionDescriptor remoteSdp = new SessionDescriptor(call.getRemoteSessionDescriptor());
        	SessionDescriptor localSdp = new SessionDescriptor(call.getLocalSessionDescriptor());
            
            remoteVideoPort = Integer.toString(SessionDescriptorUtil.getRemoteMediaPort(remoteSdp, SessionDescriptorUtil.SDP_MEDIA_VIDEO));
            localVideoPort = Integer.toString(SessionDescriptorUtil.getRemoteMediaPort(localSdp, SessionDescriptorUtil.SDP_MEDIA_VIDEO));

        	// Free local port before starting ffmpeg
        	localVideoSocket.close();
            startUserVideoTranscoder(type);

        } catch (Exception e) {
            log.debug("Exception when starting video transcoder [UserID={} , Listeonly={}]",getUserId(),isListeningToGlobal());
            e.printStackTrace();
        }
    }
    
    private void stopBbbToFreeswitchFlashVideoStream() {
        stopVideoTranscoder();
    }
    
    private void startBbbToFreeswitchWebRTCVideoStream(){
        VideoTranscoder.Type type;
        if (_videoStreamName.equals("")){
            log.debug("startBbbToFreeswitchWebRTCVideoStream: There's no videoStream for this WebRTCCall. Starting TEMPORARY video");
            type = VideoTranscoder.Type.TRANSCODE_FILE_TO_RTP;
        }
        else {
            log.debug("startBbbToFreeswitchWebRTCVideoStream: Starting {}", _videoStreamName);
            type = VideoTranscoder.Type.TRANSCODE_RTMP_TO_RTP;
        }

        //start webRTCVideoStream
        log.debug("{} is requesting to send video through webRTC. " + "[uid=" + getUserId() + "]");
        startUserVideoTranscoder(type);
    }

    public synchronized void startUserVideoTranscoder(VideoTranscoder.Type type){
        if (videoTranscoder == null){
            videoTranscoder = new VideoTranscoder(type,getVideoStreamName(),getMeetingId(),getServerIp(),getLocalVideoPort(),getRemoteVideoPort());
            videoTranscoder.setVideoTranscoderObserver(this);
            setVideoRunning(true);
            videoTranscoder.start();
        }else
            log.debug("No need to start User's Video Transcoder [uid={}], it is already running.",getUserId());
    }


    private void stopBbbToFreeswitchWebRTCVideoStream(){
        stopVideoTranscoder();
    }

    public void startFreeswitchToBbbVideoStream(){
       beginGlobalVideoTranscoding();
       //onCallStreamStarted();
    }
    
    public void startFreeswitchToBbbVideoProbe(){
      if(videoTranscoder != null){
          videoTranscoder.probeVideoStream();
      }
    }

    public void stopFreeswitchToBbbGlobalVideoStream(){
        stopVideoTranscoder();
    }

    private void closeAudioStream() {
        if (audioCallStream != null) {
            log.debug("Shutting down the AUDIO stream...");
        	audioCallStream.stopFreeswitchToBbbStream();
        	audioCallStream = null;
        } else {
        	log.debug("Can't shutdown AUDIO stream: already NULL");
        }
    }

    private synchronized void stopVideoTranscoder(){
      /*
       * closes videoTranscoder
       */

        if(videoTranscoder != null) {
            log.debug("Shutting down the video transcoder...");

            setVideoRunning(false);
            videoTranscoder.stop();
            videoTranscoder = null;

            if(isGlobalStream()){
                log.debug("Shutting down the (****GLOBAL VIDEO TRANSCODER ****) ...");
                GlobalCall.removeSDPVideoFile(getDestination());
                GlobalCall.setVideoPresent(getDestination(), false);
                log.debug("Informing client the global stream is null");
                messagingService.globalVideoStreamCreated(getMeetingId(),"");
            }
        }else{
            log.debug("No need to stop Video Transcoder [uid={}], it already stopped.",getUserId());
        }
    }

    public void connectToGlobalStream(String clientId, String userId, String callerIdName, String voiceConf) throws GlobalCallNotFoundException {
        _destination = voiceConf;
        GlobalCall.addUser(clientId, callerIdName,userId, _destination);
        listeningToGlobal = true;
        String globalAudioStreamName = GlobalCall.getGlobalAudioStream(voiceConf);
        while (globalAudioStreamName == null) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
            }
            globalAudioStreamName = GlobalCall.getGlobalAudioStream(voiceConf);
        }

        sipAudioCodec = GlobalCall.getRoomAudioCodec(voiceConf);
        callState = CallState.UA_ONCALL;
        notifyListenersOnCallConnected("", globalAudioStreamName);
        log.info("User is has connected to global audio, user=[" + callerIdName + "] voiceConf = [" + voiceConf + "]");
        messagingService.userConnectedToGlobalAudio(voiceConf, callerIdName);
        userProfile.userID = userId;
    }

    // ********************** Call callback functions **********************
    private void createAudioCodec(SessionDescriptor newSdp) {
    	sipAudioCodec = SdpUtils.getNegotiatedAudioCodec(newSdp);
    }

    private void createVideoCodec(SessionDescriptor newSdp) {
    }
        
    private void setupSdpAndCodec(String sdp) {
    	SessionDescriptor remoteSdp = new SessionDescriptor(sdp);
        SessionDescriptor localSdp = new SessionDescriptor(localSession);
        
        log.debug("localSdp = " + localSdp.toString() + ".");
        log.debug("remoteSdp = " + remoteSdp.toString() + ".");
        
        // First we need to make payloads negotiation so the related attributes can be then matched.
        SessionDescriptor newSdp = SdpUtils.makeMediaPayloadsNegotiation(localSdp, remoteSdp);        
        createAudioCodec(newSdp);
        createVideoCodec(newSdp);
        
        // Now we complete the SDP negotiation informing the selected 
        // codec, so it can be internally updated during the process.
        SdpUtils.completeSdpNegotiation(newSdp, localSdp, remoteSdp);
        localSession = newSdp.toString();
        
        log.debug("newSdp = " + localSession + "." );
        
        // Finally, we use the "newSdp" and "remoteSdp" to initialize the lasting codec informations.
        CodecUtils.initSipAudioCodec(sipAudioCodec, userProfile.audioDefaultPacketization, 
                userProfile.audioDefaultPacketization, newSdp, remoteSdp);

        //Init the video codec? we don't know yet...
        //CodecUtils.initSipVideoCodec(sipVideoCodec, userProfile.audioDefaultPacketization, 
                //userProfile.audioDefaultPacketization, newSdp, remoteSdp);

    }


    /** Callback function called when arriving a 2xx (call accepted) 
     *  The user has managed to join the conference.
     */ 
    public void onCallAccepted(Call call, String sdp, Message resp) {        
    	log.debug("Received 200/OK. So user has successfully joined the conference.");        
    	if (!isCurrentCall(call)) return;
        callState = CallState.UA_ONCALL;

        setupSdpAndCodec(sdp);

        if (userProfile.noOffer) {
            // Answer with the local sdp.
            call.ackWithAnswer(localSession);
        }

        createStreams();
    }

    /** Callback function called when arriving an ACK method (call confirmed) */
    public void onCallConfirmed(Call call, String sdp, Message ack) {
    	log.debug("Received ACK. Hmmm...is this for when the server initiates the call????");
        
    	if (!isCurrentCall(call)) return;        
        callState = CallState.UA_ONCALL;
        createStreams();
    }

    /** Callback function called when arriving a 4xx (call failure) */
    public void onCallRefused(Call call, String reason, Message resp) {        
    	log.debug("Call has been refused.");        
    	if (!isCurrentCall(call)) return;
        callState = CallState.UA_IDLE;
        notifyListenersOnOutgoingCallFailed();
    }

    /** Callback function called when arriving a 3xx (call redirection) */
    public void onCallRedirection(Call call, String reason, Vector contact_list, Message resp) {        
    	log.debug("onCallRedirection");
        
    	if (!isCurrentCall(call)) return;
        call.call(((String) contact_list.elementAt(0)));
    }


    /**
     * Callback function that may be overloaded (extended). Called when arriving a CANCEL request
     */
    public void onCallCanceling(Call call, Message cancel) {
    	log.error("Server shouldn't cancel call...or does it???");
        
    	if (!isCurrentCall(call)) return; 
        
        log.debug("Server has CANCEL-led the call.");
        callState = CallState.UA_IDLE;
        notifyListenersOfOnIncomingCallCancelled();
    }

    private void notifyListenersOnCallConnected(String userSenderAudioStream, String userReceiverAudioStream) {
        log.debug("notifyListenersOnCallConnected for {}", clientId);
        String audioCodec = "";

        if(sipAudioCodec != null)
            audioCodec = sipAudioCodec.getCodecName();

        clientConnManager.joinConferenceSuccess(clientId, userSenderAudioStream, userReceiverAudioStream, audioCodec);
    }    


    private void notifyListenersOnOutgoingCallFailed() {
    	log.debug("notifyListenersOnOutgoingCallFailed for {}", clientId);
    	clientConnManager.joinConferenceFailed(clientId);
    	cleanup();
    }

    
    private void notifyListenersOfOnIncomingCallCancelled() {
    	log.debug("notifyListenersOfOnIncomingCallCancelled for {}", clientId);
    }
    
    private void notifyListenersOfOnCallClosed() {
        if (callState == CallState.UA_IDLE) return;

        if(isGlobalStream()) {
            log.debug("***GLOBAL CALL*** notifyListenersOfOnCallClosed: closing all streams because GLOBAL CALL received a bye");
            log.debug("Current users connected on the Global Call: "+ GlobalCall.getListenOnlyUsers(_destination).size());
            for(Iterator<ListenOnlyUser> i = GlobalCall.getListenOnlyUsers(_destination).iterator(); i.hasNext();) {
                ListenOnlyUser lou = i.next();
                String clientId = lou.clientId;
                String callerIdName = lou.callerIdName;
                log.debug("notifyListenersOfOnCallClosed for callerIdName = {} (clientId = {})", callerIdName, clientId);

                //notify bigbluebutton-client
                clientConnManager.leaveConference(clientId);

                //notify bbb-apps
                messagingService.userDisconnectedFromGlobalAudio(_destination,callerIdName);

                //now, we remove the lou from the list
                GlobalCall.removeUser(clientId, _destination);
            }
        }
        else {
            log.debug("notifyListenersOfOnCallClosed for {}", clientId);
            clientConnManager.leaveConference(clientId);
        }
        cleanup();
    }

    private void cleanupAudio(){
        if (localAudioSocket == null) return;

        log.debug("Closing local audio port {}", localAudioSocket.getLocalPort());
        if (!listeningToGlobal) {
            localAudioSocket.close();
        }
    }

    private void cleanupVideo(){
        if (localVideoSocket == null) return;

        log.debug("Closing local video port {}", localAudioSocket.getLocalPort());
        localVideoSocket.close();
    }
    private void cleanup() {
        cleanupAudio();
        cleanupVideo();
    }

    /** Callback function called when arriving a BYE request */
    public void onCallClosing(Call call, Message bye) {

    	if (isGlobalStream())
    		log.info("Received a BYE (***GLOBAL CALL***) from the other end telling us to hangup. User ID: "+ getUserId());
    	else
    		log.info("Received a BYE from the other end telling us to hangup. User ID: "+ getUserId());
	
        if (!isCurrentCall(call)) 
        	return;

        notifyListenersOfOnCallClosed();
        callState = CallState.UA_IDLE;
        closeAudioStream();
        stopVideoTranscoder();
        notifyCallAgentObserverOnCallAgentClosed();

        // Reset local sdp for next call.
        initSessionDescriptor();
    }


    /**
     * Callback function called when arriving a response after a BYE request
     * (call closed)
     */
    public void onCallClosed(Call call, Message resp) {
    	log.debug("onCallClosed");
        
    	if (!isCurrentCall(call)) return;         
        log.debug("CLOSE/OK.");

        notifyListenersOfOnCallClosed();
        callState = CallState.UA_IDLE;
        closeAudioStream();
        stopVideoTranscoder();

    }


    /** Callback function called when the invite expires */
    public void onCallTimeout(Call call) {        
    	log.debug("onCallTimeout");
        
    	if (!isCurrentCall(call)) return; 
        
        log.debug("NOT FOUND/TIMEOUT.");
        callState = CallState.UA_IDLE;

        notifyListenersOnOutgoingCallFailed();
    }

    public void onCallStreamStopped() {
    	log.info("Call stream has been stopped");
    	notifyListenersOfOnCallClosed();
    }

    private boolean isCurrentCall(Call call) {
    	return this.call == call;
    }
    
    public void setCallStreamFactory(CallStreamFactory csf) {
    	this.callStreamFactory = csf;
    }
    
	public void setClientConnectionManager(ClientConnectionManager ccm) {
		clientConnManager = ccm;
	}

    public void setCallAgentObserver(CallAgentObserver cao){
        this.callAgentObserver = cao;
    }

    public String getUserId() {
        return userProfile.userID;
    }

    public String getCallerName() {
        return userProfile.username;
    }

    @Override
    public void onFirRequest() {
        log.debug("Sending FIR request to FreeSwitch..."); 
        
        Message msg = MessageFactory.createRequest(
                sipProvider
                , SipMethods.INFO
                , sipProvider.completeNameAddress(destinationPrefix)
                , sipProvider.completeNameAddress(userProfile.fromUrl)
                , ""); // no way to pass content-type, will set empty message for now

        msg.setBody("application/media_control+xml"
                ,       "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        " <media_control>\n" +
                        "  <vc_primitive>\n" +
                        "   <to_encoder>\n" +
                        "    <picture_fast_update>\n" +
                        "    </picture_fast_update>\n" +
                        "   </to_encoder>\n" +
                        "  </vc_primitive>\n" +
                        " </media_control>\n");

        sipProvider.sendMessage(msg);
    }

    public void setMeetingId(String meetingId){
        this._meetingId = meetingId;
    }

    public String getMeetingId(){
        return this._meetingId;
    }

    public void setVideoStreamName(String streamName){
        this._videoStreamName = streamName;
    }

    public String getVideoStreamName(){
        return this._videoStreamName;
    }

    @Override
    public void handleTranscodingFinishedUnsuccessfully() {
        if(isGlobalStream()){
            restartGlobalTranscoder();
        }else restartUserTranscoder();
    }

    public synchronized void restartGlobalTranscoder(){
        if (videoTranscoder != null){
            //update global's video stream name every time the transcoder is restarted
            setVideoStreamName(GlobalCall.GLOBAL_VIDEO_STREAM_NAME_PREFIX + getDestination() + "_"+System.currentTimeMillis());
            if(videoTranscoder.restart(getVideoStreamName())){
                log.debug("Informing client about the new Global Video Stream name: "+ getVideoStreamName());
                messagingService.globalVideoStreamCreated(getMeetingId(),getVideoStreamName());
            }else
                log.debug("No need to restart Global Video Transcoder, it already restarted");
        }else
            log.debug("Global Video Transcoder won't restart because global call already finished [uid={}]",getUserId());
    }

    public synchronized void restartUserTranscoder(){
        if (videoTranscoder != null){
            videoTranscoder.restart("");
        }else
            log.debug("User's Video Transcoder won't restart because global call already finished [uid={}]",getUserId());
    }
    @Override
    public void handleTranscodingFinishedWithSuccess() {
        //called by ProcessMonitor when successfully finished

        if(isGlobalStream()){
            stopVideoTranscoder();
            log.debug("(******* GLOBAL TRANSCODER ******) [uid={}] finished with success .",getUserId());
        }else log.debug("Transcoder for user [uid={}] finished with success.",getUserId());
    }

    @Override
    public void handleVideoProbingFinishedWithSuccess(Map<String,String> ffprobeResult) {
        //Send to apps
        if(ffprobeResult != null) {
           currentVideoProbe = ffprobeResult;
           String newWidth = currentVideoProbe.get("width");
           String newHeight = currentVideoProbe.get("height");

           if(newWidth != null && !newWidth.isEmpty() && newHeight != null && !newHeight.isEmpty()) {
              log.debug("Sending updateSipVideoStatus [{}x{}]", currentVideoProbe.get("width"), currentVideoProbe.get("height"));
              messagingService.updateSipVideoStatus(getMeetingId(),currentVideoProbe.get("width"), currentVideoProbe.get("height"));
           }
           else
              log.debug("Could not send updateSipVideoStatus: failed to get the new resolution");
        }
        else {
          log.debug("Could not send updateSipVideoStatus: Probe result is null.");
        }
    }

	public void setLocalVideoPort(String localVideoPort){
		this.localVideoPort = localVideoPort;
	}

	public String getLocalVideoPort(){
		return this.localVideoPort;
	}

	public void setRemoteVideoPort(String remoteVideoPort){
		this.remoteVideoPort = remoteVideoPort;
	}

	public String getRemoteVideoPort(){
		return this.remoteVideoPort;
	}

	public String getServerIp(){
		return this.serverIp;
	}

	public void setWebRTC(boolean flag){
		this.isWebRTC = flag;
	}

	public boolean isWebRTC(){
		return this.isWebRTC;
	}

	private boolean isGlobalUserId(){
		return getUserId().startsWith(GlobalCall.LISTENONLY_USERID_PREFIX);
	}

	public boolean isGlobal(){
		return this.isGlobal;
	}

    public Map<String,String> getCurrentVideoAspect(){
        return this.currentVideoProbe;
    }

	private void notifyCallAgentObserverOnCallAgentClosed(){
		if(callAgentObserver != null){
			log.debug("Notifying CallAgentObserver that CallAgent with userid={} has been closed",getUserId());
			callAgentObserver.handleCallAgentClosed(getCallId(), getCallerName(),getUserId(),getDestination(),getMeetingId(), getServerIp());
		}else{
			log.debug("Can't notify CallAgentObserver that CallAgent with userid={} has been closed. CallAgentObserver = null",getUserId());
		}
	}

    /**
     * Set the video status of the CallAgent.
     * This is useful, for example, when the user is the floor
     * but his video isn't running yet because there's no sip-user
     * in the conference
     * @param flag default: false
     */
    private void setVideoRunning(boolean flag){
        this.isVideoRunning = flag;
    }

    /**
     * Informs the status of user's transcoder
     * @return true if the user's transcoder is running, false otherwise
     */
    public boolean isVideoRunning(){
        return this.isVideoRunning;
    }
}
