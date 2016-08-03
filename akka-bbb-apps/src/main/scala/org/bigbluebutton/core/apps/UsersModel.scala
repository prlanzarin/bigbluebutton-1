package org.bigbluebutton.core.apps

import scala.collection.mutable.HashMap
import org.bigbluebutton.core.api.UserVO
import org.bigbluebutton.core.api.Role._
import scala.collection.mutable.ArrayBuffer
import org.bigbluebutton.core.api.VoiceUser
import org.bigbluebutton.core.util.RandomStringGenerator
import org.bigbluebutton.core.api.Presenter
import org.bigbluebutton.core.api.RegisteredUser

class UsersModel {
  private var uservos = new collection.immutable.HashMap[String, UserVO]

  private var regUsers = new collection.immutable.HashMap[String, RegisteredUser]

  /* When reconnecting SIP global audio, users may receive the connection message
   * before the disconnection message.
   * This variable is a connection counter that should control this scenario.
   */
  private var globalAudioConnectionCounter = new collection.immutable.HashMap[String, Integer]

  private var locked = false
  private var meetingMuted = false
  private var recordingVoice = false
  private var GLOBAL_CALL_NAME_PREFIX = "GLOBAL_CALL_"

  private var currentPresenter = new Presenter("system", "system", "system")

  def setCurrentPresenterInfo(pres: Presenter) {
    currentPresenter = pres
  }

  def getCurrentPresenterInfo(): Presenter = {
    currentPresenter
  }

  def addRegisteredUser(token: String, regUser: RegisteredUser) {
    regUsers += token -> regUser
  }

  def getRegisteredUserWithToken(token: String): Option[RegisteredUser] = {
    regUsers.get(token)
  }

  def generateWebUserId: String = {
    val webUserId = RandomStringGenerator.randomAlphanumericString(6)
    if (!hasUser(webUserId)) webUserId else generateWebUserId
  }

  def addUser(uvo: UserVO) {
    uservos += uvo.userID -> uvo
  }

  def removeUser(userId: String): Option[UserVO] = {
    val user = uservos get (userId)
    user foreach (u => uservos -= userId)

    user
  }

  def hasSessionId(sessionId: String): Boolean = {
    uservos.contains(sessionId)
  }

  def hasUser(userID: String): Boolean = {
    uservos.contains(userID)
  }

  def numUsers(): Int = {
    uservos.size
  }

  def numWebUsers(): Int = {
    uservos.values filter (u => u.phoneUser == false) size
  }

  def numUsersInVoiceConference: Int = {
    val joinedUsers = uservos.values filter (u => u.voiceUser.joined)
    joinedUsers.size
  }

  def getUserWithExternalId(userID: String): Option[UserVO] = {
    uservos.values find (u => u.externUserID == userID)
  }

  def getUserWithVoiceUserId(voiceUserId: String): Option[UserVO] = {
    uservos.values find (u => u.voiceUser.userId == voiceUserId)
  }

  def getUser(userID: String): Option[UserVO] = {
    uservos.values find (u => u.userID == userID)
  }

  def getUsers(): Array[UserVO] = {
    uservos.values toArray
  }

  def getMediaSourceUser(name: String): Option[UserVO] = {
    uservos.values find (u => u.name == name)
  }

  def numModerators(): Int = {
    getModerators.length
  }

  def findAModerator(): Option[UserVO] = {
    uservos.values find (u => u.role == MODERATOR)
  }

  def noPresenter(): Boolean = {
    !getCurrentPresenter().isDefined
  }

  def getCurrentPresenter(): Option[UserVO] = {
    uservos.values find (u => u.presenter == true)
  }

  def unbecomePresenter(userID: String) = {
    uservos.get(userID) match {
      case Some(u) => {
        val nu = u.copy(presenter = false)
        uservos += nu.userID -> nu
      }
      case None => // do nothing	
    }
  }

  def becomePresenter(userID: String) = {
    uservos.get(userID) match {
      case Some(u) => {
        val nu = u.copy(presenter = true)
        uservos += nu.userID -> nu
      }
      case None => // do nothing	
    }
  }

  def getModerators(): Array[UserVO] = {
    uservos.values filter (u => u.role == MODERATOR) toArray
  }

  def getViewers(): Array[UserVO] = {
    uservos.values filter (u => u.role == VIEWER) toArray
  }

  def getRegisteredUserWithUserID(userID: String): Option[RegisteredUser] = {
    regUsers.values find (ru => userID contains ru.id)
  }

  def removeRegUser(userID: String) {
    getRegisteredUserWithUserID(userID) match {
      case Some(ru) => {
        regUsers -= ru.authToken
      }
      case None =>
    }
  }

  def addGlobalAudioConnection(userID: String): Boolean = {
    globalAudioConnectionCounter.get(userID) match {
      case Some(vc) => {
        globalAudioConnectionCounter += userID -> (vc + 1)
        false
      }
      case None => {
        globalAudioConnectionCounter += userID -> 1
        true
      }
    }
  }

  def removeGlobalAudioConnection(userID: String): Boolean = {
    globalAudioConnectionCounter.get(userID) match {
      case Some(vc) => {
        if (vc == 1) {
          globalAudioConnectionCounter -= userID
          true
        } else {
          globalAudioConnectionCounter += userID -> (vc - 1)
          false
        }
      }
      case None => {
        false
      }
    }
  }

  def startRecordingVoice() {
    recordingVoice = true
  }

  def stopRecordingVoice() {
    recordingVoice = false
  }

  def isVoiceRecording: Boolean = {
    recordingVoice
  }

  def isGlobalCallAgent(callername: String): Boolean = {
    Option(callername) match {
      case Some(c) => c.startsWith(GLOBAL_CALL_NAME_PREFIX)
      case None => false
    }
  }

  def getPhoneUsersSendingVideo(): Array[UserVO] = {
    uservos.values filter (u => (u.phoneUser == true && u.hasStream == true && !u.mediaSourceUser)) toArray
  }

  def activeTalkerChangedInWebconference(oldActiveTalkerUserId: String, newActiveTalkerUserId: String): Boolean = {
    val changed: Boolean = getUser(oldActiveTalkerUserId) match {
      case Some(ou) =>
        getUser(newActiveTalkerUserId) match {
          case Some(nu) => ou.phoneUser ^ nu.phoneUser
          case _ => ou.phoneUser ^ newActiveTalkerUserId.startsWith(GLOBAL_CALL_NAME_PREFIX)
        }

      case _ =>
        getUser(newActiveTalkerUserId) match {
          case Some(nu) => oldActiveTalkerUserId.startsWith(GLOBAL_CALL_NAME_PREFIX) ^ newActiveTalkerUserId.startsWith(GLOBAL_CALL_NAME_PREFIX)
          case _ => false
        }
    }
    return changed
  }

  def getUserMainWebcamStream(userId: String): String = {
    getUser(userId) match {
      case Some(u) =>
        Option(u.webcamStreams) match {
          case Some(streams) => streams.head
          case None => ""
        }
      case None => ""
    }
  }

  def getTranscoderParam(key: String, params: Map[String, String]): Option[String] = {
    Option(params) match {
      case Some(map) =>
        map.get(key) match {
          case Some("") => None
          case Some(s) => Option(s)
          case _ => None
        }
      case _ => None
    }
  }

  def isMediaSourceUser(callerIdNum: String, kurentoToken: String): Boolean = {
    System.out.println("isMediaSourceUser ? callerIdNum = " + callerIdNum + " , kurentoToken = " + kurentoToken)
    Option(callerIdNum) match {
      case Some("") => false
      case Some(c) => c.contains(kurentoToken)
      case None => false
    }
  }
}
