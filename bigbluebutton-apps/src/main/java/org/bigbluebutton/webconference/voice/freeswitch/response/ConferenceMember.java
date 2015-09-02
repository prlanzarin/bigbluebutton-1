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

package org.bigbluebutton.webconference.voice.freeswitch.response;

/**
 *
 * @author leif
 */
public class ConferenceMember {

    protected Integer memberId;
    protected ConferenceMemberFlags flags;
    protected String uuid;
    protected String callerIdName;
    protected String callerNetworkAddress;
    protected String callerId;
    protected Integer joinTime;
    protected Integer lastTalking;

    public Integer getId() {
        return memberId;
    }

    public ConferenceMemberFlags getFlags() {
        return flags;
    }

    public String getCallerId() {
        return callerId;
    }

    public String getCallerIdName() {
        return getValidCallerIdName(callerIdName,callerNetworkAddress);
    }

    public static String getValidCallerIdName(String callerIdName, String callerNetworkAddress) {
        /*
         * If Freeswitch sends 'unknown' as the callerIdName, it means something is wrong with the caller's sip user name
         * (probably the sip phone that made the call has the sip user name empty or misconfigured)
         * In this case, we set the ip address as the callerIdName.
         */
        String validCallerIdName = callerIdName;
        if (isUnknownCaller(callerIdName) && callerNetworkAddress != null && !callerNetworkAddress.isEmpty())
            validCallerIdName = callerNetworkAddress;
        return validCallerIdName;
    }

    public static boolean isUnknownCaller(String callerIdName) {
        return callerIdName.equals("unknown");
    }

    public String getCallerNetworkAddress() {
        return callerNetworkAddress;
    }

    public boolean getMuted() {
        return flags.getIsMuted();
    }

    public boolean getSpeaking() {
        return flags.getIsSpeaking();
    }

    public boolean getHasVideo() {
        return flags.getHasVideo();
    }

    public void setFlags(ConferenceMemberFlags flags) {
        this.flags = flags;
    }

    public void setId(int parseInt) {
        memberId = parseInt;
    }

    public void setUUID(String tempVal) {
        this.uuid = tempVal;
    }

    public void setCallerIdName(String tempVal) {
        this.callerIdName = tempVal;
    }

    public void setCallerNetworkAddress(String tempVal) {
        this.callerNetworkAddress = tempVal;
    }

    public void setCallerId(String tempVal) {
        this.callerId = tempVal;
    }

    public void setJoinTime(int parseInt) {
        this.joinTime = parseInt;
    }

    void setLastTalking(int parseInt) {
        this.lastTalking = parseInt;
    }

}
