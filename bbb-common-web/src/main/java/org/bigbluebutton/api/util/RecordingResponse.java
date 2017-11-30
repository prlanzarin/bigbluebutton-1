package org.bigbluebutton.api.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.bigbluebutton.api.domain.MeetingInfo;
import org.bigbluebutton.api.domain.Breakout;
import org.bigbluebutton.api.domain.BreakoutRoom;
import org.bigbluebutton.api.domain.Metadata;
import org.bigbluebutton.api.domain.RecordingMetadata;
import org.bigbluebutton.api.domain.RecordingMetadataPlayback;
import org.bigbluebutton.api.domain.Download;

public class RecordingResponse {
  private String id;
  private String state;
  private boolean published;
  private String startTime;
  private String endTime;
  private int participants;
  private MeetingInfo meetingInfo;
  private String rawSize;
  private Breakout breakout;
  private BreakoutRoom[] breakoutRooms;
  private String[] recordingUsersExternalId;
  private Metadata metadata;
  private List<RecordingMetadataPlayback> playbacks = new ArrayList();
  private String metadataXml;
  private Boolean processingError = false;
  private Download download;
  private Boolean initialized = false;

  public void setId(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getState() {
    return state;
  }

  public void setPublished(boolean published) {
    this.published = published;
  }

  public boolean getPublished() {
    return published;
  }

  public void setStartTime(String startTime) {
    this.startTime = startTime;
  }

  public String getStartTime() {
    return startTime;
  }

  public void setEndTime(String endTime) {
    this.endTime = endTime;
  }

  public String getEndTime() {
    return endTime;
  }

  public void setParticipants(int participants) {
    this.participants = participants;
  }

  public int getParticipants() {
    return participants;
  }

  public void setMeeting(MeetingInfo meetingInfo) {
    this.meetingInfo = meetingInfo;
  }

  public MeetingInfo getMeeting() {
    return meetingInfo;
  }

  public void setRawSize(String rawSize) {
    this.rawSize = rawSize;
  }

  public String getRawSize() {
    return rawSize;
  }

  public void setBreakout(Breakout breakout) {
    this.breakout = breakout;
  }

  public Breakout getBreakout() {
    return breakout;
  }

  public void setBreakoutRooms(BreakoutRoom[] breakoutRooms) {
    this.breakoutRooms = breakoutRooms;
  }

  public BreakoutRoom[] getBreakoutRooms() {
    return breakoutRooms;
  }

  public void setRecordingUsersExternalId(String[] recordingUsersExternalId) {
    this.recordingUsersExternalId = recordingUsersExternalId;
  }

  public String[] getRecordingUsersExternalId() {
    return recordingUsersExternalId;
  }

  public void setMeta(Metadata metadata) {
    this.metadata = metadata;
  }

  public Metadata getMeta() {
    return metadata;
  }

  public void setPlaybacks(List<RecordingMetadataPlayback> playbacks) {
    this.playbacks = playbacks;
  }

  public List<RecordingMetadataPlayback> getPlaybacks() {
    return playbacks;
  }

  public void setMetadataXml(String metadataXml) {
    this.metadataXml = metadataXml;
  }

  public String getMetadataXml() {
    return metadataXml;
  }

  public void setProcessingError(Boolean processingError) {
    this.processingError = processingError;
  }

  public Boolean getProcessingError() {
    return processingError;
}

  public void setDownload(Download download) {
    this.download = download;
  }

  public Download getDownload() {
    return download;
  }

  public String getMeetingId() {
    MeetingInfo info = getMeeting();
    if (info == null) {
      return id;
    }
    return info.getId();
  }

  public String getMeetingName() {
    MeetingInfo info = getMeeting();
    if (info == null) {
      return getMeta().get().get("meetingName");
    }
    return info.getName();
  }

  public Boolean isBreakout() {
    MeetingInfo info = getMeeting();
    if (info == null) {
      return Boolean.parseBoolean(getMeta().get().get("isBreakout"));
    }
    return info.isBreakout();
  }

  public Boolean hasError() {
    return processingError;
  }

  public Integer calculateDuration() {
    if ((endTime == null) || (endTime == "") || (startTime == null) || (startTime == "")) return 0;

    int start = (int) Math.ceil((Long.parseLong(startTime)) / 60000.0);
    int end = (int) Math.ceil((Long.parseLong(endTime)) / 60000.0);

    return end - start;
  }

  public String getSize() {
    BigInteger size = BigInteger.ZERO;
    for (RecordingMetadataPlayback playback : playbacks) {
      if (playback != null && playback.getSize().length() > 0) {
        size = size.add(new BigInteger(playback.getSize()));
      }
    }

    if (download != null && download.getSize().length() > 0) {
      size = size.add(new BigInteger(download.getSize()));
    }

    return size.toString();
  }

  public void addPlayback(RecordingMetadataPlayback playback) {
    this.playbacks.add(playback);
  }

  public void importRecordingMetadata(RecordingMetadata recordingMetadata) {
    if (!this.initialized) {
      this.initialized = true;
      this.setId(recordingMetadata.getId());
      this.setState(recordingMetadata.getState());
      this.setPublished(recordingMetadata.getPublished());
      this.setStartTime(recordingMetadata.getStartTime());
      this.setEndTime(recordingMetadata.getEndTime());
      this.setParticipants(recordingMetadata.getParticipants());
      this.setMeeting(recordingMetadata.getMeeting());
      this.setRawSize(recordingMetadata.getRawSize());
      this.setRecordingUsersExternalId(recordingMetadata.getRecordingUsersExternalId());
      this.setBreakout(recordingMetadata.getBreakout());
      this.setBreakoutRooms(recordingMetadata.getBreakoutRooms());
      this.setMeta(recordingMetadata.getMeta());
      this.setProcessingError(recordingMetadata.hasError());
    }
    if (recordingMetadata.getPlayback() != null) {
      this.addPlayback(recordingMetadata.getPlayback());
    }
    if (recordingMetadata.getDownload() != null) {
      this.setDownload(recordingMetadata.getDownload());
    }
  }
}
