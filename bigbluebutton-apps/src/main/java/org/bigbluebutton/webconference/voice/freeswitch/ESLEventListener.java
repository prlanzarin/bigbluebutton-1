package org.bigbluebutton.webconference.voice.freeswitch;


import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bigbluebutton.webconference.voice.events.ChannelCallStateEvent;
import org.bigbluebutton.webconference.voice.events.ChannelHangupCompleteEvent;
import org.bigbluebutton.webconference.voice.events.ConferenceEventListener;
import org.bigbluebutton.webconference.voice.events.VoiceStartRecordingEvent;
import org.bigbluebutton.webconference.voice.events.VoiceUserJoinedEvent;
import org.bigbluebutton.webconference.voice.events.VoiceUserLeftEvent;
import org.bigbluebutton.webconference.voice.events.VoiceUserMutedEvent;
import org.bigbluebutton.webconference.voice.events.VoiceUserTalkingEvent;
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
    
    private Map<String, DialReferenceValuePair> outboundDialReferences = new ConcurrentHashMap<String, DialReferenceValuePair>();
    
    @Override
    public void conferenceEventPlayFile(String uniqueId, String confName, int confSize, EslEvent event) {
        //Ignored, Noop
    }

    private static final Pattern DIAL_ORIGINATION_UUID_PATTERN = Pattern.compile(".* dial .*origination_uuid='([^']*)'.*");
    private static final Pattern DIAL_RESPONSE_PATTERN = Pattern.compile("^\\[Call Requested: result: \\[(.*)\\].*\\]$");
    private static final String[] DIAL_IGNORED_RESPONSES = new String[]{ "SUCCESS" };
    
    @Override
    public void backgroundJobResultReceived(EslEvent event) {
        log.debug( "Background job result received [{}]", event );
        
        log.debug(event.getEventBodyLines().toString());
        log.debug(event.getEventHeaders().toString());
        
        String arg = event.getEventHeaders().get("Job-Command-Arg");
        if (arg != null) {
            Matcher matcher = DIAL_ORIGINATION_UUID_PATTERN.matcher(arg);
            if (matcher.matches()) {
                String uuid = matcher.group(1).trim();
                String responseString = event.getEventBodyLines().toString().trim();
                
                log.debug("Background job result for uuid {}, response: {}", uuid, responseString);
                
                matcher = DIAL_RESPONSE_PATTERN.matcher(responseString);
                if (matcher.matches()) {
                    String error = matcher.group(1).trim();
                    
                    if (Arrays.asList(DIAL_IGNORED_RESPONSES).contains(error)) {
                        log.debug("Ignoring error code {}", error);
                        return;
                    }

                    DialReferenceValuePair ref = removeDialReference(uuid);
                    if (ref == null) {
                        return;
                    }
                    
                    ChannelHangupCompleteEvent hce = new ChannelHangupCompleteEvent(uuid, 
                            "HANGUP", error, ref.getRoom(), ref.getParticipant());
                    conferenceEventListener.handleConferenceEvent(hce);
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ExceptionEvent e) {
//        setChanged();
//        notifyObservers(e);
    }

    private static final Pattern GLOBAL_AUDION_PATTERN = Pattern.compile("(GLOBAL_AUDIO)_(.*)$");
    private static final Pattern CALLERNAME_PATTERN = Pattern.compile("(.*)-bbbID-(.*)$");
    
    @Override
    public void conferenceEventJoin(String uniqueId, String confName, int confSize, EslEvent event) {
    	
        Integer memberId = this.getMemberIdFromEvent(event);
        Map<String, String> headers = event.getEventHeaders();
        String callerId = this.getCallerIdFromEvent(event);
        String callerIdName = this.getCallerIdNameFromEvent(event);
        boolean muted = headers.get("Speak").equals("true") ? false : true; //Was inverted which was causing a State issue
        boolean speaking = headers.get("Talking").equals("true") ? true : false;

        String voiceUserId = callerIdName;
        
        log.info("User joined voice conference, user=[" + callerIdName + "], conf=[" + confName + "]");
        log.debug("Details of the user connection: {}", event.getEventHeaders().toString());
        
        Matcher gapMatcher = GLOBAL_AUDION_PATTERN.matcher(callerIdName);
        if (gapMatcher.matches()) {
        	log.debug("Ignoring GLOBAL AUDIO USER [{}]", callerIdName);
        	return;
        }
        		
		    Matcher matcher = CALLERNAME_PATTERN.matcher(callerIdName);
		    if (matcher.matches()) {			
			    voiceUserId = matcher.group(1).trim();
			    callerIdName = matcher.group(2).trim();
		    } 
        
        VoiceUserJoinedEvent pj = new VoiceUserJoinedEvent(voiceUserId, memberId.toString(), confName, callerId, callerIdName, muted, speaking);
        conferenceEventListener.handleConferenceEvent(pj);
    }

    @Override
    public void conferenceEventLeave(String uniqueId, String confName, int confSize, EslEvent event) {   	
        Integer memberId = this.getMemberIdFromEvent(event);
        log.info("User left voice conference, user=[" + memberId.toString() + "], conf=[" + confName + "]");
        if(memberId == null) {
            return;
        }
        VoiceUserLeftEvent pl = new VoiceUserLeftEvent(memberId.toString(), confName);
        conferenceEventListener.handleConferenceEvent(pl);
    }

    @Override
    public void conferenceEventMute(String uniqueId, String confName, int confSize, EslEvent event) {
        Integer memberId = this.getMemberIdFromEvent(event);
        System.out.println("******************** Received Conference Muted Event from FreeSWITCH user[" + memberId.toString() + "]");
        log.info("User muted voice conference, user=[" + memberId.toString() + "], conf=[" + confName + "]");
        VoiceUserMutedEvent pm = new VoiceUserMutedEvent(memberId.toString(), confName, true);
        conferenceEventListener.handleConferenceEvent(pm);
    }

    @Override
    public void conferenceEventUnMute(String uniqueId, String confName, int confSize, EslEvent event) {
        Integer memberId = this.getMemberIdFromEvent(event);
        System.out.println("******************** Received ConferenceUnmuted Event from FreeSWITCH user[" + memberId.toString() + "]");
        log.info("User unmuted voice conference, user=[" + memberId.toString() + "], conf=[" + confName + "]");
        VoiceUserMutedEvent pm = new VoiceUserMutedEvent(memberId.toString(), confName, false);
        conferenceEventListener.handleConferenceEvent(pm);
    }

    @Override
    public void conferenceEventAction(String uniqueId, String confName, int confSize, String action, EslEvent event) {
        Integer memberId = this.getMemberIdFromEvent(event);
        VoiceUserTalkingEvent pt;
        
        System.out.println("******************** Receive conference Action [" + action + "]");
        
        if (action == null) {
            return;
        }

        if (action.equals(START_TALKING_EVENT)) {
            pt = new VoiceUserTalkingEvent(memberId.toString(), confName, true);
            conferenceEventListener.handleConferenceEvent(pt);        	
        } else if (action.equals(STOP_TALKING_EVENT)) {
            pt = new VoiceUserTalkingEvent(memberId.toString(), confName, false);
            conferenceEventListener.handleConferenceEvent(pt);        	
        } else {
        	log.debug("Unknown conference Action [{}]", action);
        	System.out.println("Unknown conference Action [" + action + "]");
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
            VoiceStartRecordingEvent sre = new VoiceStartRecordingEvent(confName, true);
            sre.setRecordingFilename(getRecordFilenameFromEvent(event));
            sre.setTimestamp(genTimestamp().toString());
            
            log.info("Voice conference recording started. file=[" + getRecordFilenameFromEvent(event) + "], conf=[" + confName + "]");
            
            conferenceEventListener.handleConferenceEvent(sre);    		
    	} else if (action.equals(STOP_RECORDING_EVENT)) {
        	VoiceStartRecordingEvent srev = new VoiceStartRecordingEvent(confName, false);
            srev.setRecordingFilename(getRecordFilenameFromEvent(event));
            srev.setTimestamp(genTimestamp().toString());
            
            log.info("Voice conference recording stopped. file=[" + getRecordFilenameFromEvent(event) + "], conf=[" + confName + "]");           
            conferenceEventListener.handleConferenceEvent(srev);    		
    	} else {
        	if (log.isDebugEnabled())
        		log.warn("Processing UNKNOWN conference Action {}", action);
    	}
    }

    private Long genTimestamp() {
    	return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }
    
	@Override
	public void eventReceived(EslEvent event) {
		System.out.println("ESL Event Listener received event=[" + event.getEventName() + "]");
//        if (event.getEventName().equals(FreeswitchHeartbeatMonitor.EVENT_HEARTBEAT)) {
////           setChanged();
//           notifyObservers(event);
//           return; 
//        }
        if(event.getEventName().equals("CHANNEL_CALLSTATE")) {
            String uniqueId = this.getUniqueIdFromEvent(event);
            String callState = this.getChannelCallStateFromEvent(event);
            String originalCallState = this.getOrigChannelCallStateFromEvent(event);
            String origCallerIdName = this.getOrigCallerIdNameFromEvent(event);
            String channelName = this.getCallerChannelNameFromEvent(event);
            
            log.debug("Received {} for uuid {}, CallState {}", event.getEventName(), uniqueId, callState);

            DialReferenceValuePair ref = getDialReferenceValue(uniqueId);
            if (ref == null) {
                return;
            }
            
            String room = ref.getRoom();
            String participant = ref.getParticipant();

            ChannelCallStateEvent cse = new ChannelCallStateEvent(uniqueId, callState, 
                                                    room, participant);
            
            conferenceEventListener.handleConferenceEvent(cse);
        }
        else if(event.getEventName().equals("CHANNEL_HANGUP_COMPLETE")) {
            String uniqueId = getUniqueIdFromEvent(event);
            String callState = getChannelCallStateFromEvent(event);
            String hangupCause = getHangupCauseFromEvent(event);
            String origCallerIdName = getOrigCallerIdNameFromEvent(event);
            String channelName = getCallerChannelNameFromEvent(event);

            log.debug("Received {} for uuid {}, CallState {}, HangupCause {}", event.getEventName(), uniqueId, callState, hangupCause);

            DialReferenceValuePair ref = removeDialReference(uniqueId);
            if (ref == null) {
                return;
            }
            
            String room = ref.getRoom();
            String participant = ref.getParticipant();

            ChannelHangupCompleteEvent hce = new ChannelHangupCompleteEvent(uniqueId, callState, 
                                                    hangupCause, room, participant);
            
            conferenceEventListener.handleConferenceEvent(hce);
        }
    }
    
    public void addDialReference(String uuid, DialReferenceValuePair value) {
        log.debug("Adding dial reference: {} -> {}, {}", uuid, value.getRoom(), value.getParticipant());
        if (!outboundDialReferences.containsKey(uuid)) {
            outboundDialReferences.put(uuid, value);
        }
    }
    
    private DialReferenceValuePair removeDialReference(String uuid) {
        log.debug("Removing dial reference: {}", uuid);
        DialReferenceValuePair r = outboundDialReferences.remove(uuid);
        if (r == null) {
            log.debug("Returning null because the uuid has already been removed");
        }
        log.debug("Current dial references size: {}", outboundDialReferences.size());
        return r;
    }
    
    private DialReferenceValuePair getDialReferenceValue(String uuid) {
        return outboundDialReferences.get(uuid);
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
    
    private String getOrigChannelCallStateFromEvent(EslEvent e) {
        return e.getEventHeaders().get("Original-Channel-Call-State");
    }
    
    private String getUniqueIdFromEvent(EslEvent e) {
        return e.getEventHeaders().get("Unique-ID");
    }
    
    private String getOrigCallerIdNameFromEvent(EslEvent e) {
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
    
    public void setConferenceEventListener(ConferenceEventListener listener) {
        this.conferenceEventListener = listener;
    }
}
