package org.bigbluebutton.core.apps.voice

import org.bigbluebutton.core.BigBlueButtonGateway
import org.bigbluebutton.core.api._

class VoiceInGateway(bbbGW: BigBlueButtonGateway) {
	
  def muteAllExceptPresenter(meetingID: String, requesterID: String, mute: Boolean) {
    bbbGW.accept(new MuteAllExceptPresenterRequest(meetingID, requesterID, mute))
  }
  
  def muteAllUsers(meetingID: String, requesterID: String, mute: Boolean) {
	  bbbGW.accept(new MuteMeetingRequest(meetingID, requesterID, mute))
	}
	
	def isMeetingMuted(meetingID: String, requesterID: String) {
	  bbbGW.accept(new IsMeetingMutedRequest(meetingID, requesterID))
	}
	
	def muteUser(meetingID: String, requesterID: String, userID: String, mute: Boolean) {
	  bbbGW.accept(new MuteUserRequest(meetingID, requesterID, userID, mute))
	}
	
	def lockUser(meetingID: String, requesterID: String, userID: String, lock: Boolean) {
	  bbbGW.accept(new LockUserRequest(meetingID, requesterID, userID, lock))
	}
	
	def ejectUserFromVoice(meetingID: String, userId: String, ejectedBy: String) {
	  bbbGW.accept(new EjectUserFromVoiceRequest(meetingID, userId, ejectedBy))
	}
			
	def voiceUserJoined(meetingId: String, userId: String, webUserId: String, 
	                            conference: String, callerIdNum: String, 
	                            callerIdName: String,
								muted: Boolean, talking: Boolean, hasVideo: Boolean, hasFloor: Boolean) {
//	  println("VoiceInGateway: Got voiceUserJoined message for meeting [" + meetingId + "] user[" + callerIdName + "]")
	  val voiceUser = new VoiceUser(userId, webUserId, 
	                                callerIdName, callerIdNum,  
	                                true, false, muted, talking, hasVideo, hasFloor)
	  bbbGW.accept(new VoiceUserJoined(meetingId, voiceUser))
	}
	
	def voiceUserLeft(meetingId: String, userId: String) {
//	  println("VoiceInGateway: Got voiceUserLeft message for meeting [" + meetingId + "] user[" + userId + "]")
	  bbbGW.accept(new VoiceUserLeft(meetingId, userId))
	}
	
	def voiceUserLocked(meetingId: String, userId: String, locked: Boolean) {
	  bbbGW.accept(new VoiceUserLocked(meetingId, userId, locked))
	}
	
	def voiceUserMuted(meetingId: String, userId: String, muted: Boolean) {
	  bbbGW.accept(new VoiceUserMuted(meetingId, userId, muted))
	}
	
	def voiceUserTalking(meetingId: String, userId: String, talking: Boolean) {
	  bbbGW.accept(new VoiceUserTalking(meetingId, userId, talking))
	}
	
	def voiceRecording(meetingId: String, recordingFile: String, 
			            timestamp: String, recording: java.lang.Boolean) {
	  bbbGW.accept(new VoiceRecording(meetingId, recordingFile, 
			            timestamp, recording))
	}

	def sipVideoPaused(meetingId: String) {
	  bbbGW.accept(new SipVideoPaused(meetingId))
	}

	def sipVideoResumed(meetingId: String) {
	  bbbGW.accept(new SipVideoResumed(meetingId))
	}

	def activeTalkerChanged(meetingId: String, userId: String) {
	  bbbGW.accept(new ActiveTalkerChanged(meetingId, userId))
	}
  
  def dial(meetingId: String, userId: String, options: Map[String, String], params: Map[String, String]) {
    bbbGW.accept(new VoiceOutboundDialRequest(meetingId, userId, options, params))
  }
  
  def cancelDial(meetingId: String, userId: String, uuid: String) {
    bbbGW.accept(new VoiceCancelDialRequest(meetingId, userId, uuid))
  }

  def sendDtmf(meetingId: String, userId: String, uuid: String, dtmfDigit:String) {
    bbbGW.accept(new VoiceSendDtmfRequest(meetingId, userId, uuid, dtmfDigit))
  }
  
  def dialing(meetingId: String, userId: String, uuid: String, callState: String) {
    bbbGW.accept(new VoiceDialing(meetingId, userId, uuid, callState));
  }
  
  def hangingUp(meetingId: String, userId: String, uuid: String, callState: String, hangupCause: String) {
    bbbGW.accept(new VoiceHangingUp(meetingId, userId, uuid, callState, hangupCause));
  }
}
