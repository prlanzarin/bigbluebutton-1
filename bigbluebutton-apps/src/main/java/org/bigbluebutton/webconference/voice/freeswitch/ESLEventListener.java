package org.bigbluebutton.webconference.voice.freeswitch;

import java.util.Map;
import java.util.HashMap;
import org.bigbluebutton.webconference.voice.events.ConferenceEventListener;
import org.bigbluebutton.webconference.voice.events.ParticipantJoinedEvent;
import org.bigbluebutton.webconference.voice.events.ParticipantLeftEvent;
import org.bigbluebutton.webconference.voice.events.ParticipantMutedEvent;
import org.bigbluebutton.webconference.voice.events.ParticipantTalkingEvent;
import org.bigbluebutton.webconference.voice.events.StartRecordingEvent;
import org.bigbluebutton.webconference.voice.events.ChannelCallStateEvent;
import org.bigbluebutton.webconference.voice.events.ChannelHangupCompleteEvent;
import org.bigbluebutton.webconference.voice.events.DialEvent;
import org.freeswitch.esl.client.IEslEventListener;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class ESLEventListener implements IEslEventListener {
	private static Logger log = Red5LoggerFactory.getLogger(ESLEventListener.class, "bigbluebutton");
	
    private static final String START_TALKING_EVENT = "start-talking";
    private static final String STOP_TALKING_EVENT = "stop-talking";
    private static final String START_RECORDING_EVENT = "start-recording";
    private static final String STOP_RECORDING_EVENT = "stop-recording";
    
    private ConferenceEventListener conferenceEventListener;
    
    private HashMap<DialReferenceKeyPair, DialReferenceValuePair> dialReferences;
    
    @Override
    public void conferenceEventPlayFile(String uniqueId, String confName, int confSize, EslEvent event) {
        //Ignored, Noop
    }

    @Override
    public void backgroundJobResultReceived(EslEvent event) {
        log.debug( "Background job result received [{}]", event );
    }

    @Override
    public void exceptionCaught(ExceptionEvent e) {
//        setChanged();
//        notifyObservers(e);
    }

    @Override
    public void conferenceEventJoin(String uniqueId, String confName, int confSize, EslEvent event) {
        Integer memberId = this.getMemberIdFromEvent(event);
        Map<String, String> headers = event.getEventHeaders();
        String callerId = this.getCallerIdFromEvent(event);
        String callerIdName = this.getCallerIdNameFromEvent(event);
        boolean muted = headers.get("Speak").equals("true") ? false : true; //Was inverted which was causing a State issue
        boolean speeking = headers.get("Talking").equals("true") ? true : false;

        ParticipantJoinedEvent pj = new ParticipantJoinedEvent(memberId, confName, callerId, callerIdName, muted, speeking);
        conferenceEventListener.handleConferenceEvent(pj);
    }

    @Override
    public void conferenceEventLeave(String uniqueId, String confName, int confSize, EslEvent event) {
        Integer memberId = this.getMemberIdFromEvent(event);
        
        if(memberId == null) {
            return;
        }
           
        ParticipantLeftEvent pl = new ParticipantLeftEvent(memberId, confName);
        conferenceEventListener.handleConferenceEvent(pl);
    }

    @Override
    public void conferenceEventMute(String uniqueId, String confName, int confSize, EslEvent event) {
        Integer memberId = this.getMemberIdFromEvent(event);
        ParticipantMutedEvent pm = new ParticipantMutedEvent(memberId, confName, true);
        conferenceEventListener.handleConferenceEvent(pm);
    }

    @Override
    public void conferenceEventUnMute(String uniqueId, String confName, int confSize, EslEvent event) {
        Integer memberId = this.getMemberIdFromEvent(event);
        ParticipantMutedEvent pm = new ParticipantMutedEvent(memberId, confName, false);
        conferenceEventListener.handleConferenceEvent(pm);
    }

    @Override
    public void conferenceEventAction(String uniqueId, String confName, int confSize, String action, EslEvent event) {
        Integer memberId = this.getMemberIdFromEvent(event);
        ParticipantTalkingEvent pt;

        if (action == null) {
            return;
        }

        if (action.equals(START_TALKING_EVENT)) {
            pt = new ParticipantTalkingEvent(memberId, confName, true);
            conferenceEventListener.handleConferenceEvent(pt);        	
        } else if (action.equals(STOP_TALKING_EVENT)) {
            pt = new ParticipantTalkingEvent(memberId, confName, false);
            conferenceEventListener.handleConferenceEvent(pt);        	
        } else {
        	log.debug("Unknown conference Action [{}]", action);
        }
    }

    @Override
    public void conferenceEventTransfer(String uniqueId, String confName, int confSize, EslEvent event) {
        //Ignored, Noop
    }

    @Override
    public void conferenceEventThreadRun(String uniqueId, String confName, int confSize, EslEvent event) {
    	
    }
    
    //@Override
    public void conferenceEventRecord(String uniqueId, String confName, int confSize, EslEvent event) {
    	String action = event.getEventHeaders().get("Action");
    	
        if(action == null) {
            
            return;
        }
        
    	if (log.isDebugEnabled())
    		log.debug("Handling conferenceEventRecord " + action);
    	
    	if (action.equals(START_RECORDING_EVENT)) {
            StartRecordingEvent sre = new StartRecordingEvent(123, confName, true);
            sre.setRecordingFilename(getRecordFilenameFromEvent(event));
            sre.setTimestamp(getRecordTimestampFromEvent(event));
            
            if (log.isDebugEnabled())
            	log.debug("Processing conference event - action: {} time: {} file: {}", new Object[] {action,  sre.getTimestamp(), sre.getRecordingFilename()});
            
            conferenceEventListener.handleConferenceEvent(sre);    		
    	} else if (action.equals(STOP_RECORDING_EVENT)) {
        	StartRecordingEvent srev = new StartRecordingEvent(123, confName, false);
            srev.setRecordingFilename(getRecordFilenameFromEvent(event));
            srev.setTimestamp(getRecordTimestampFromEvent(event));
            
            if (log.isDebugEnabled())
            	log.debug("Processing conference event - action: {} time: {} file: {}", new Object[] {action,  srev.getTimestamp(), srev.getRecordingFilename()});
            
            conferenceEventListener.handleConferenceEvent(srev);    		
    	} else {
        	if (log.isDebugEnabled())
        		log.warn("Processing UNKNOWN conference Action {}", action);
    	}
    }


    @Override
    public void eventReceived(EslEvent event) {
        if (event.getEventName().equals(FreeswitchHeartbeatMonitor.EVENT_HEARTBEAT)) {
            //setChanged();
            //notifyObservers(event);
            return; 
        }
        else if(event.getEventName().equals("CHANNEL_CALLSTATE")) {
            String uniqueId = this.getUniqueIdFromEvent(event);
            String callState = this.getChannelCallStateFromEvent(event);
            String originalCallState = this.getOriginalChannelCallStateFromEvent(event);
            String idName = this.getCallerOrigCallerIDNameFromEvent(event);
            String channelName = this.getCallerChannelNameFromEvent(event);
            
            DialReferenceKeyPair key = new DialReferenceKeyPair(idName, channelName);
            
            if(!this.dialReferences.containsKey(key)) {
                return;
            }
            
            DialReferenceValuePair dialValue = this.dialReferences.get(key);
            String room = dialValue.getRoom();
            Integer participant = dialValue.getParticipant();

            dialValue.setUuid(uniqueId);
                         
            ChannelCallStateEvent cse = new ChannelCallStateEvent(uniqueId, 
                                                    idName, channelName, callState, 
                                                    originalCallState, room, participant);
            
            conferenceEventListener.handleDialEvent(cse);
            
            return;
        }
        else if(event.getEventName().equals("CHANNEL_HANGUP_COMPLETE")) {
            String uniqueId = this.getUniqueIdFromEvent(event);
            String callState = this.getChannelCallStateFromEvent(event);
            String hangupCause = this.getHangupCauseFromEvent(event);
            String idName = this.getCallerOrigCallerIDNameFromEvent(event);
            String channelName = this.getCallerChannelNameFromEvent(event);
            
            DialReferenceKeyPair key = new DialReferenceKeyPair(idName, channelName);
            
            if(!this.dialReferences.containsKey(key)) {
                return;
            }
            
            DialReferenceValuePair dialValue = this.dialReferences.get(key);
            String room = dialValue.getRoom();
            Integer participant = dialValue.getParticipant();

            ChannelHangupCompleteEvent hce = new ChannelHangupCompleteEvent(uniqueId, 
                                                    idName, channelName, callState, 
                                                    hangupCause, room, participant);
            
            conferenceEventListener.handleDialEvent(hce);
            
            return;
        }
    }

    public void initDialReferences() {
        this.dialReferences = new HashMap<DialReferenceKeyPair, DialReferenceValuePair>();
    }
    
    public void addDialReference(DialReferenceKeyPair keyPair, DialReferenceValuePair value) {
        if (!dialReferences.containsKey(keyPair))
            dialReferences.put(keyPair, value);
    }
    
    public void removeDialReference(DialReferenceKeyPair keyToRemove) {
        if (dialReferences.containsKey(keyToRemove)) {
            dialReferences.remove(keyToRemove);
        }
    }
    
    public DialReferenceValuePair getDialReferenceValue(DialReferenceKeyPair keyToRetrieve) {
        if (dialReferences.containsKey(keyToRetrieve)) {
            return dialReferences.get(keyToRetrieve);
        }
        else {
            return null;
        }
    }
    
    private String getChannelCallStateFromEvent(EslEvent e) {
        return e.getEventHeaders().get("Channel-Call-State");
    }
    
    private String getHangupCauseFromEvent(EslEvent e) {
        return e.getEventHeaders().get("Hangup-Cause");
    }
    
    private String getCallerChannelNameFromEvent(EslEvent e) {
        return e.getEventHeaders().get("Caller-Channel-Name");
    }
    
    private String getOriginalChannelCallStateFromEvent(EslEvent e) {
        return e.getEventHeaders().get("Original-Channel-Call-State");
    }
    
    private String getUniqueIdFromEvent(EslEvent e) {
        return e.getEventHeaders().get("Unique-ID");
    }
    
    private String getCallerOrigCallerIDNameFromEvent(EslEvent e) {
        return e.getEventHeaders().get("Caller-Orig-Caller-ID-Name");
    }
    
    private Integer getMemberIdFromEvent(EslEvent e) {
        try {
            return new Integer(e.getEventHeaders().get("Member-ID"));
        }
        catch(NumberFormatException ex) {
            return null;
        }
    }

    private String getCallerIdFromEvent(EslEvent e) {
        return e.getEventHeaders().get("Caller-Caller-ID-Number");
    }

    private String getCallerIdNameFromEvent(EslEvent e) {
        return e.getEventHeaders().get("Caller-Caller-ID-Name");
    }
    
    private String getRecordFilenameFromEvent(EslEvent e) {
    	return e.getEventHeaders().get("Path");
    }
    
    private String getRecordTimestampFromEvent(EslEvent e) {
    	return e.getEventHeaders().get("Event-Date-Timestamp");
    }
	
    public void setConferenceEventListener(ConferenceEventListener listener) {
        this.conferenceEventListener = listener;
    }
}
