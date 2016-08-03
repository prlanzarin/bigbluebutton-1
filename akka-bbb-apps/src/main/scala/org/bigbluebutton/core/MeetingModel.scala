package org.bigbluebutton.core

import org.bigbluebutton.core.api.Permissions
import java.util.concurrent.TimeUnit

case object StopMeetingActor
case class MeetingProperties(meetingID: String, externalMeetingID: String, meetingName: String, recorded: Boolean,
  voiceBridge: String, duration: Long, autoStartRecording: Boolean, allowStartStopRecording: Boolean,
  moderatorPass: String, viewerPass: String, createTime: Long, createDate: String, kurentoToken: String)

class MeetingModel {
  private var audioSettingsInited = false
  private var permissionsInited = false
  private var permissions = new Permissions()
  private var recording = false;
  private var muted = false;
  private var meetingEnded = false
  private var meetingMuted = false
  private var _isSipVideoPresent = false
  private var _isSipPhonePresent = false
  private var _talkerUserId = ""
  private var _globalVideoStreamName = ""
  private var _globalVideoStreamWidth = ""
  private var _globalVideoStreamHeight = ""
  private var _globalCallCallername = ""
  private var _globalCallLocalIpAddress = ""
  private var _globalCallLocalVideoPort = ""
  private var _globalCallRemoteVideoPort = ""
  private var _sipHost = ""

  val TIMER_INTERVAL = 30000
  private var hasLastWebUserLeft = false
  private var lastWebUserLeftOnTimestamp: Long = 0
  private var VIDEOCONFERENCE_STREAM_NAME = "sip_"
  val VIDEOCONFERENCE_LOGO_PREFIX = "video_conf_"

  private var voiceRecordingFilename: String = ""

  val startedOn = timeNowInMinutes;

  def muteMeeting() {
    meetingMuted = true
  }

  def unmuteMeeting() {
    meetingMuted = false
  }

  def isMeetingMuted(): Boolean = {
    meetingMuted
  }

  def recordingStarted() {
    recording = true
  }

  def recordingStopped() {
    recording = false
  }

  def isRecording(): Boolean = {
    recording
  }

  def lastWebUserLeft() {
    lastWebUserLeftOnTimestamp = timeNowInMinutes
  }

  def lastWebUserLeftOn(): Long = {
    lastWebUserLeftOnTimestamp
  }

  def resetLastWebUserLeftOn() {
    lastWebUserLeftOnTimestamp = 0
  }

  def setVoiceRecordingFilename(path: String) {
    voiceRecordingFilename = path
  }

  def getVoiceRecordingFilename(): String = {
    voiceRecordingFilename
  }

  def permisionsInitialized(): Boolean = {
    permissionsInited
  }

  def initializePermissions() {
    permissionsInited = true
  }

  def audioSettingsInitialized(): Boolean = {
    audioSettingsInited
  }

  def initializeAudioSettings() {
    audioSettingsInited = true
  }

  def permissionsEqual(other: Permissions): Boolean = {
    permissions == other
  }

  def lockLayout(lock: Boolean) {
    permissions = permissions.copy(lockedLayout = lock)
  }

  def getPermissions(): Permissions = {
    permissions
  }

  def setPermissions(p: Permissions) {
    permissions = p
  }

  def meetingHasEnded() {
    meetingEnded = true
  }

  def hasMeetingEnded(): Boolean = {
    meetingEnded
  }

  def timeNowInMinutes(): Long = {
    TimeUnit.NANOSECONDS.toMinutes(System.nanoTime())
  }

  def setSipVideoPresent(value: Boolean) {
    _isSipVideoPresent = value
  }

  def isSipVideoPresent(): Boolean = {
    _isSipVideoPresent
  }

  def setSipPhonePresent(value: Boolean) {
    _isSipPhonePresent = value
  }

  def isSipPhonePresent(): Boolean = {
    _isSipPhonePresent
  }

  def setTalkerUserId(userId: String) {
    _talkerUserId = userId
  }

  def talkerUserId(): String = {
    _talkerUserId
  }

  def isTalker(userId: String): Boolean = {
    _talkerUserId == userId
  }

  def setGlobalVideoStreamName(streamName: String) {
    _globalVideoStreamName = streamName
  }

  def globalVideoStreamName(): String = {
    _globalVideoStreamName
  }

  def setGlobalVideoStreamWidth(streamWidth: String) {
    _globalVideoStreamWidth = streamWidth
  }

  def globalVideoStreamWidth(): String = {
    _globalVideoStreamWidth
  }

  def setGlobalVideoStreamHeight(streamHeight: String) {
    _globalVideoStreamHeight = streamHeight
  }

  def globalVideoStreamHeight(): String = {
    _globalVideoStreamHeight
  }

  def setGlobalCallCallername(callername: String) {
    _globalCallCallername = callername
  }

  def globalCallCallername(): String = {
    _globalCallCallername
  }

  def setGlobalCallLocalIpAddress(localIpAddress: String) {
    _globalCallLocalIpAddress = localIpAddress
  }

  def globalCallLocalIpAddress(): String = {
    _globalCallLocalIpAddress
  }

  def setGlobalCallLocalVideoPort(localVideoPort: String) {
    _globalCallLocalVideoPort = localVideoPort
  }

  def globalCallLocalVideoPort(): String = {
    _globalCallLocalVideoPort
  }

  def setGlobalCallRemoteVideoPort(remoteVideoPort: String) {
    _globalCallRemoteVideoPort = remoteVideoPort
  }

  def globalCallRemoteVideoPort(): String = {
    _globalCallRemoteVideoPort
  }

  def setSipHost(sipHost: String) {
    _sipHost = sipHost
  }

  def sipHost(): String = {
    _sipHost
  }

  def isSipVideoEnabled(): Boolean = {
    !globalCallCallername.isEmpty() && !globalCallLocalIpAddress.isEmpty() && !globalCallLocalVideoPort.isEmpty() && !globalCallRemoteVideoPort.isEmpty() && !sipHost.isEmpty()
  }

  def isVideoconferenceStream(streamName: String): Boolean = {
    Option(streamName) match {
      case Some(s) => s.startsWith(VIDEOCONFERENCE_STREAM_NAME)
      case None => false
    }
  }
}
