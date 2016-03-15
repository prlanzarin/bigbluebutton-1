package org.bigbluebutton.voiceconf.sip;

public class FloorHolder {

    /**
     * Stores information about the current floor holder
     * of the conference.
     */

    private String userId = "";
    private Boolean hasVideo = null;

    public FloorHolder(String userId, boolean hasVideo){
        this.userId = userId;
        this.hasVideo = hasVideo;
    }

    public String getUserId(){
        return this.userId;
    }

    public void setUserId(String userId){
        this.userId = userId;
    }

    public boolean hasVideo(){
        return this.hasVideo;
    }

    public void setUserHasVideo(boolean flag){
        this.hasVideo = flag;
    }

}