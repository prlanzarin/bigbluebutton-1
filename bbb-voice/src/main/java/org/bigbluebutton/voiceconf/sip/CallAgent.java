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

import java.util.HashMap;
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
    private Codec sipVideoCodec = null;    
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

    public String getDestination() {
        return _destination;
    }

    public CallAgent(String sipClientRtpIp, SipProvider sipProvider, SipPeerProfile userProfile,
		ConferenceProvider portProvider, String clientId, String userId, IMessagingService messagingService) {
        this.sipProvider = sipProvider;
        this.clientRtpIp = sipClientRtpIp;
        this.userProfile = userProfile;
        this.portProvider = portProvider;
        this.clientId = clientId;
        this._userId = userId;
        this.messagingService = messagingService;
        this.serverIp = Red5.getConnectionLocal().getHost();
        this.userProfile.userID = this._userId;
        this.isGlobal = isGlobalUserId();
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
            closeVideoStream();
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
                        String streamName = audioCallStream.getBbbToFreeswitchStreamName();

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

    private boolean createVideoStream() {

        //Starting FreeswitchToBbb Video Stream - this method is called only by global stream
        //this method can be called createGlobalVideoStream
        try {
            SessionDescriptor localSdp = new SessionDescriptor(call.getLocalSessionDescriptor());
            String sdpVideo = SessionDescriptorUtil.getLocalVideoSDP(localSdp);
            GlobalCall.createSDPVideoFile(getDestination(), sdpVideo);
            String sdpPath = GlobalCall.getSdpVideoPath(getDestination());
            setVideoStreamName(GlobalCall.GLOBAL_VIDEO_STREAM_NAME_PREFIX + getDestination() +"_"+System.currentTimeMillis());

            // Free local port before starting ffmpeg
            localVideoSocket.close();

            videoTranscoder = new VideoTranscoder(VideoTranscoder.Type.TRANSCODE_RTP_TO_RTMP,
                    sdpPath,getVideoStreamName(),getMeetingId(),getServerIp());
            videoTranscoder.setVideoTranscoderObserver(this);
            videoTranscoder.start();
            //videoCallStream.startBbbToFreeswitchStream(broadcastStream, scope);
        } catch (Exception e) {
            log.debug("Failed to start FFMPEG for global video (fs->bbb) stream");
            e.printStackTrace();
        }

        return false;
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
    		String streamName = audioCallStream.getBbbToFreeswitchStreamName();
    	} else {
    		log.info("Can't stop talk stream as stream may have already stopped.");
    	}
    }

    public void startBbbToFreeswitchVideoStream(){
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

    public void startBbbToFreeswitchFlashVideoStream() {
        if (_videoStreamName.equals("")){
            log.debug("There's no videoStream for this FlashCall. Waiting for the user to enable your webcam");
            return;
        }
        //start flash video stream
        try {
            SessionDescriptor remoteSdp = new SessionDescriptor(call.getRemoteSessionDescriptor());
        	SessionDescriptor localSdp = new SessionDescriptor(call.getLocalSessionDescriptor());
            
            remoteVideoPort = Integer.toString(SessionDescriptorUtil.getRemoteMediaPort(remoteSdp, SessionDescriptorUtil.SDP_MEDIA_VIDEO));
            localVideoPort = Integer.toString(SessionDescriptorUtil.getRemoteMediaPort(localSdp, SessionDescriptorUtil.SDP_MEDIA_VIDEO));

        	// Free local port before starting ffmpeg
        	localVideoSocket.close();
            videoTranscoder = new VideoTranscoder(VideoTranscoder.Type.TRANSCODE_RTMP_TO_RTP,getVideoStreamName(),getMeetingId(),getServerIp(),getLocalVideoPort(),getRemoteVideoPort());
            videoTranscoder.setVideoTranscoderObserver(this);
            videoTranscoder.start();

            // videoCallStream.startBbbToFreeswitchStream(broadcastStream, scope);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public void stopBbbToFreeswitchFlashVideoStream() {
        closeVideoStream();
    }
    
    public void startBbbToFreeswitchWebRTCVideoStream(){
        if (_videoStreamName.equals("")){
            log.debug("There's no videoStream for this webRTCCall. Waiting for the user to enable your webcam");
            return;
        }
        //start webRTCVideoStream
        log.debug("{} is requesting to send video through webRTC. " + "[uid=" + getUserId() + "]");
        videoTranscoder = new VideoTranscoder(VideoTranscoder.Type.TRANSCODE_RTMP_TO_RTP,getVideoStreamName(),getMeetingId(),getServerIp(),getLocalVideoPort(),getRemoteVideoPort());
        videoTranscoder.setVideoTranscoderObserver(this);
        videoTranscoder.start();
    }

    public void stopBbbToFreeswitchWebRTCVideoStream(){
        if (videoTranscoder != null) {
            videoTranscoder.stop();
            videoTranscoder=null;
        }
    }

    public void startFreeswitchToBbbVideoStream(){
       createVideoStream();
       messagingService.globalVideoStreamCreated(getMeetingId(),getVideoStreamName());
       //onCallStreamStarted();
    }
    
    public void stopFreeswitchToBbbGlobalVideoStream(){
    	closeVideoStream();
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

    private void closeVideoStream(){
      /*
       * closes videoTranscoder
       */

        if(videoTranscoder != null) {
            log.debug("Shutting down the video transcoder...");
            videoTranscoder.stop();
            videoTranscoder = null;

            if(isGlobalStream()){
                log.debug("Shutting down the (****GLOBAL VIDEO TRANSCODER ****) ...");
                GlobalCall.removeSDPVideoFile(getDestination());
                log.debug("Informing client the global stream is null");
                messagingService.globalVideoStreamCreated(getMeetingId(),"");
            }
        }else{
            log.debug("Can't shutdown VIDEO transcoder: already NULL");
        }
    }

    public void connectToGlobalStream(String clientId, String userId, String callerIdName, String voiceConf) {
        listeningToGlobal = true;
        _destination = voiceConf;

        String globalAudioStreamName = GlobalCall.getGlobalAudioStream(voiceConf);
        while (globalAudioStreamName == null) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
            }
            globalAudioStreamName = GlobalCall.getGlobalAudioStream(voiceConf);
        }

        GlobalCall.addUser(clientId, callerIdName, _destination);
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
        sipVideoCodec = SdpUtils.getNegotiatedVideoCodec(newSdp);
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
            log.debug("***GLOBAL CALL*** notifyListenersOfOnCallClosed: closing all streams because GLOBAL AUDIO received a bye");
            for(Iterator<String> i = GlobalCall.getListeners(_destination).iterator(); i.hasNext();) {
                String userId = i.next();
                log.debug("notifyListenersOfOnCallClosed for {}", userId);
                clientConnManager.leaveConference(userId);
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
        
        closeAudioStream();
        closeVideoStream();
        notifyListenersOfOnCallClosed();
        callState = CallState.UA_IDLE;
        notifyCallAgentObserverOnCallAgentClosed(getUserId());
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
        
        closeAudioStream();
        closeVideoStream();

        notifyListenersOfOnCallClosed();
        callState = CallState.UA_IDLE;
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
    public void handleTranscodingRestarted() {
        if (videoTranscoder != null){
            if(isGlobalStream()){
                setVideoStreamName(GlobalCall.GLOBAL_VIDEO_STREAM_NAME_PREFIX + getDestination() + "_"+System.currentTimeMillis());
                videoTranscoder.restart(getVideoStreamName());
                log.debug("Informing client about the new Global Video Stream name: "+ getVideoStreamName());
                messagingService.globalVideoStreamCreated(getMeetingId(),getVideoStreamName());
            }else videoTranscoder.restart("");
        }
    }

    @Override
    public void handleTranscodingFinishedWithSuccess() {
        //called by ProcessMonitor when successfully finished
        if(isGlobalStream()){
             log.debug("Transcoder for user [uid={}] finished with success.",getUserId());
        }else log.debug("(******* GLOBAL TRANSCODER ******) [uid={}] finished with success .",getUserId());
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

	private void notifyCallAgentObserverOnCallAgentClosed(String userId){
		if(callAgentObserver != null){
			log.debug("Notifying CallAgentObserver that CallAgent with userid={} has been closed",userId);
			callAgentObserver.handleCallAgentClosed(userId);
		}else{
			log.debug("Can't notify CallAgentObserver that CallAgent with userid={} has been closed. CallAgentObserver = null",userId);
		}
	}
}
