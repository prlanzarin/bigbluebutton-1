package org.bigbluebutton.core.db

import slick.jdbc.PostgresProfile.api._
import org.bigbluebutton.common2.msgs.MediaGroupParticipant

case class MediaGroupUserDbModel(
    groupId:         String,
    meetingId:       String,
    userId:          String,
    participantType: String,
    active:          Boolean
)

class MediaGroupUserDbTableDef(tag: Tag) extends Table[MediaGroupUserDbModel](tag, None, "user_mediaGroup") {
  val groupId = column[String]("groupId", O.PrimaryKey)
  val meetingId = column[String]("meetingId", O.PrimaryKey)
  val userId = column[String]("userId", O.PrimaryKey)
  val participantType = column[String]("participantType")
  val active = column[Boolean]("active")
  override def * = (groupId, meetingId, userId, participantType, active) <>
    (MediaGroupUserDbModel.tupled, MediaGroupUserDbModel.unapply)
}

object MediaGroupUserDAO {
  def insert(meetingId: String, groupId: String, mgp: MediaGroupParticipant, participantType: String) = {
    MediaGroupUserDAO.insertUser(meetingId, groupId, mgp.userId, participantType, mgp.active)
  }

  def insertUser(meetingId: String, groupId: String, userId: String, participantType: String, active: Boolean) = {
    DatabaseConnection.enqueue(
      TableQuery[MediaGroupUserDbTableDef].insertOrUpdate(
        MediaGroupUserDbModel(
          userId = userId,
          groupId = groupId,
          meetingId = meetingId,
          participantType = participantType,
          active = active
        )
      )
    )
  }

  def update(meetingId: String, groupId: String, mgp: MediaGroupParticipant, participantType: String) = {
    DatabaseConnection.enqueue(
      TableQuery[MediaGroupUserDbTableDef]
        .filter(_.meetingId === meetingId)
        .filter(_.groupId === groupId)
        .filter(_.userId === mgp.userId)
        .map(nmgp => (nmgp.active, nmgp.participantType))
        .update((mgp.active, participantType))
    )
  }

  def delete(meetingId: String, groupId: String, userId: String) = {
    DatabaseConnection.enqueue(
      TableQuery[MediaGroupUserDbTableDef]
        .filter(_.meetingId === meetingId)
        .filter(_.groupId === groupId)
        .filter(_.userId === userId)
        .delete
    )
  }

  def deleteAll(meetingId: String, groupId: String) = {
    DatabaseConnection.enqueue(
      TableQuery[MediaGroupUserDbTableDef]
        .filter(_.meetingId === meetingId)
        .filter(_.groupId === groupId)
        .delete
    )
  }

  def deleteAll(meetingId: String) = {
    DatabaseConnection.enqueue(
      TableQuery[MediaGroupUserDbTableDef]
        .filter(_.meetingId === meetingId)
        .delete
    )
  }

  def deleteAll() = {
    DatabaseConnection.enqueue(
      TableQuery[MediaGroupUserDbTableDef].delete
    )
  }

  def getActiveUsers(meetingId: String, groupId: String) = {
    DatabaseConnection.enqueue(
      TableQuery[MediaGroupUserDbTableDef]
        .filter(_.meetingId === meetingId)
        .filter(_.groupId === groupId)
        .filter(_.active === true)
        .result
    )
  }
}
