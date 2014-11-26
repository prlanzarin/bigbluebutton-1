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

package org.bigbluebutton.webconference.voice.freeswitch;

public class DialStates {

    private String uniqueId;
    
    private final String ringingState = "RINGING";
    private final String earlyState = "EARLY";
    private final String hangupState = "HANGUP";
    private final String downState = "DOWN";
    private final String activeState = "ACTIVE";
    
    private String hangupCause;
    
    private String originalState;
    private String currentState; 
    
    public DialStates(String uniqueId, String firstState) {
        this.uniqueId = uniqueId;
        this.currentState = firstState;
    }
    
    public void updateState(String newState) {
        if(checkStates(newState))
        {
            this.originalState = this.currentState;
            this.currentState = newState;
        }
    }
    
    public void setHangupCause(String hangupCause) {
        this.hangupCause = hangupCause;
    }
    
    public String getUniqueId() {
        return this.uniqueId;
    }
    
    public String getCurrentState() {
        return this.currentState;
    }
    
    public String getHangupCause() {
        return this.hangupCause;
    }
    
    private boolean checkStates(String newState) {
        if(newState.equals(this.ringingState)) {
                return false;
        }
        else if(newState.equals(this.earlyState)) {
            if(!this.currentState.equals(this.ringingState))
                return false;
            else
                return true;
        }
        else if(newState.equals(this.hangupState)) {
            if(this.currentState.equals(this.downState))
                return false;
            else
                return true;
        }
        else if(newState.equals(this.downState)) {
            if(!this.currentState.equals(this.hangupState))
                return false;
            else
                return true;
        }
        else if(newState.equals(this.activeState)) {
            if(!this.currentState.equals(this.earlyState))
                return false;
            else
                return true;
        }
        else
        {
            return false;
        }
    }
}
