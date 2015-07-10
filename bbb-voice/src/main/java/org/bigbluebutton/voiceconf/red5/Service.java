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
package org.bigbluebutton.voiceconf.red5;

import java.text.MessageFormat;

import org.slf4j.Logger;
import org.bigbluebutton.voiceconf.sip.GlobalCallNotFoundException;
import org.bigbluebutton.voiceconf.sip.PeerNotFoundException;
import org.bigbluebutton.voiceconf.sip.SipPeerManager;
import org.bigbluebutton.voiceconf.sip.GlobalCall;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
public class Service {
    private static Logger log = Red5LoggerFactory.getLogger(Service.class, "sip");

    private SipPeerManager sipPeerManager;
    private ClientConnectionManager clientConnectionManager;
    private final String peerId = "default";
	private MessageFormat callExtensionPattern = new MessageFormat("{0}");

	public Boolean call(String peerId, String callerName, String destination, Boolean listenOnly) {
        String clientId = Red5.getConnectionLocal().getClient().getId();
	    String userId = getUserId();
	    String username = getUsername();
		if (listenOnly) {
            try{
                log.debug("{} is requesting to join into the conference {} as a listenonly.", username +"[peerId="+ peerId + "][uid=" + userId + "][clientid=" + clientId + " callerName="+callerName+"]", destination);
                sipPeerManager.connectToGlobalStream(peerId, clientId, userId, callerName, destination);
                Red5.getConnectionLocal().setAttribute("VOICE_CONF_PEER", peerId);
                return true;
            } catch (GlobalCallNotFoundException e){
                log.debug("{} can't join into the conferece {} as listenonly , because there's no global call agent for this room",userId,destination);
                log.debug("Sending destroyedGlobalCall() to this user. ClientId={} ",clientId);
                clientConnectionManager.destroyedGlobalCall(clientId);;
                return false;
            }
		} else {
			Boolean result = call(peerId, callerName, destination);
			return result;
		}
	}

	public Boolean call(String peerId, String callerName, String destination) {
    	String clientId = Red5.getConnectionLocal().getClient().getId();
    	String userid = getUserId();
    	String username = getUsername();		
    log.debug("{} is requesting to join into the conference {}.", username +"[peerId="+ peerId + "][uid=" + userid + "][clientid=" + clientId + " callerName="+callerName+"]", destination);
		
		String extension = callExtensionPattern.format(new String[] { destination });
		try {
			sipPeerManager.call(peerId, getClientId(), callerName, userid, extension,getMeetingId());
			Red5.getConnectionLocal().setAttribute("VOICE_CONF_PEER", peerId);
			return true;
		} catch (PeerNotFoundException e) {
			log.error("PeerNotFound {}", peerId);
			return false;
		}
	}

	public Boolean hangup(String peerId) {
    	String clientId = Red5.getConnectionLocal().getClient().getId();
    	String userid = getUserId();
    	String username = getUsername();		
    	log.debug("{} is requesting to hang up from the conference.", username + "[uid=" + userid + "][clientid=" + clientId + "]");
		try {
			sipPeerManager.hangup(peerId, userid, username);
			return true;
		} catch (PeerNotFoundException e) {
			log.error("PeerNotFound {}", peerId);
			return false;
		}
	}
	
	public Boolean acceptWebRTCCall(String peerId, String destination, String remoteVideoPort, String localVideoPort){
        //called by the client
        String userid = getUserId();
        String username = getUsername();
        String meetingId = getMeetingId();
        String clientId = getClientId();
        log.debug("Accepted a webRTC Call for the user ["+userid+"] : saving it's parameters: [destination = "+destination +",remoteVideoPort = "+remoteVideoPort+",localVideoPort = "+localVideoPort+"]");
        try{
            if (sipPeerManager != null) {
                sipPeerManager.webRTCCall(peerId, clientId, userid, username, destination, meetingId, remoteVideoPort,localVideoPort);
                sipPeerManager.startBbbToFreeswitchVideoStream(peerId, userid, "");
            }
            else log.debug("There's no SipPeerManager to handle this webRTC Video Call. Aborting... ");
        } catch (PeerNotFoundException e) {
            log.error("PeerNotFound {}", peerId);
            return false;
        }
        return true;
	}

    public void updateVideoStatus(String voiceBridge, String floorHolder, Boolean videoPresent) {		
        log.debug("updateVideoStatus [voiceBridge={}, floorHolder={}, isVideoPresent={}]", voiceBridge, floorHolder, videoPresent);
        String globalUserId = GlobalCall.LISTENONLY_USERID_PREFIX + voiceBridge;

        if (GlobalCall.isGlobalVideoAbleToRun(voiceBridge)){
            if (videoPresent){
                sipPeerManager.startFreeswitchToBbbGlobalVideoStream(peerId, globalUserId);

                if(GlobalCall.floorHolderChanged(voiceBridge, floorHolder)) {
                  sipPeerManager.startFreeswitchToBbbGlobalVideoProbe(peerId, globalUserId);
                  GlobalCall.setFlooHolder(voiceBridge, floorHolder);
                }
            }
        }else log.debug("Global video transcoder won't start because there's no need to (check previous log message)");
    }

    public void userSharedWebcam(String userId, String streamName){
        log.debug("userSharedWebcam [userId={}, streamName={}]",userId, streamName);

        if(isVideoStream(streamName) && (!userId.equals(""))){
            try {
                sipPeerManager.startBbbToFreeswitchVideoStream(peerId, userId, streamName);
            } catch (PeerNotFoundException e) {
                log.error("PeerNotFound {}", peerId);
            }
        }
        else{
            log.debug("Error when getting user's info");
        }
    }

    public void userUnsharedWebcam(String userId){
        log.debug("userUnsharedWebcam [userId={}]",userId);
        if (!userId.equals("")) {
            try {
                sipPeerManager.stopBbbToFreeswitchVideoStream(peerId, userId);
            } catch (PeerNotFoundException e) {
              log.error("PeerNotFound {}", peerId);
            }
        }else {
            log.debug("Error when getting user's info");
        }
    }

	private String getClientId() {
		IConnection conn = Red5.getConnectionLocal();
		return conn.getClient().getId();
	}
	
	public void setCallExtensionPattern(String callExtensionPattern) {
		this.callExtensionPattern = new MessageFormat(callExtensionPattern);
	}
	
	public void setSipPeerManager(SipPeerManager sum) {
		sipPeerManager = sum;
	}
	
	private String getUserId() {
		String userid = (String) Red5.getConnectionLocal().getAttribute("USERID");
		if ((userid == null) || ("".equals(userid))) userid = "unknown-userid";
		return userid;
	}
	
	private String getUsername() {
		String username = (String) Red5.getConnectionLocal().getAttribute("USERNAME");
		if ((username == null) || ("".equals(username))) username = "UNKNOWN-CALLER";
		return username;
	}

	private String getMeetingId(){
		String meetingid = (String) Red5.getConnectionLocal().getAttribute("MEETING_ID");
		if ((meetingid == null) || ("".equals(meetingid))) meetingid = "UNKNOWN-MEETING_ID";
		return meetingid;
	}

    private boolean isVideoStream(String streamName){
        return streamName.matches("\\w+-\\w+_\\d+-\\d+"); //format: <video profile>-<userid>_<number of joins>-<timestamp>
    }

    public void setClientConnectionManager(ClientConnectionManager ccm) {
        clientConnectionManager = ccm;
    }

    public void updateSipPhoneStatus(String voiceBridge, Boolean sipPhonePresent) {
        log.debug("updateSipPhoneStatus [voiceBridge={}, isSipPhonePresent={}]", voiceBridge, sipPhonePresent);
        GlobalCall.setSipPhonePresent(voiceBridge, sipPhonePresent);

        if(GlobalCall.isUserVideoAbleToRun(voiceBridge)) {
            log.debug("sip-video is able to run, starting video transcoders");
            sipPeerManager.startSavedVideoStreams(peerId, voiceBridge);
            //we won't start the global video stream here, cause it will be initiated in the next sip video update event

        } else {
            log.debug("No more sip phones in the conference. Stopping video transcoders");
            sipPeerManager.stopSavedVideoStreams(peerId, voiceBridge);
            log.debug("Now stopping the global transconder");
            String globalUserId = GlobalCall.LISTENONLY_USERID_PREFIX + voiceBridge;
            sipPeerManager.stopFreeswitchToBbbGlobalVideoStream(peerId, globalUserId);
        }
    }
}
