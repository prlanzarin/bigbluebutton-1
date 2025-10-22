export enum ParticipantTypes {
  SENDER = 'SENDONLY',
  RECEIVER = 'RECVONLY',
  SENDRECV = 'SENDRECV',
}
export type MediaGroupParticipantType = ParticipantTypes.SENDER | ParticipantTypes.RECEIVER | ParticipantTypes.SENDRECV;

export enum MediaType {
  AUDIO = 'audio',
  CAMERA= 'camera',
  SCREENSHARE = 'screenshare',
}

export type MediaGroupParticipant = {
  userId: string;
  groupId: string;
  mediaType: MediaType;
  participantType: MediaGroupParticipantType;
  active: boolean;
}

export type MediaGroupStream = {
  userId: string;
  groupId: string;
  mediaType: MediaType;
  participantType: MediaGroupParticipantType;
  active: boolean;
};

export type MediaSendersData = {
  senders: MediaGroupStream[];
  inAnyGroup: boolean;
}

export const SUBSCRIPTION_RETRY = {
  MAX_RETRIES: 3,
  RETRY_INTERVAL: 2000,
  BACKOFF_MULTIPLIER: 1.5,
};
