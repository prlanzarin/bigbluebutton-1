/**
* BigBlueButton open source conferencing system - http://www.bigbluebutton.org/
*
* Copyright (c) 2016 BigBlueButton Inc. and by respective authors (see below).
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

package org.bigbluebutton.freeswitch.voice.freeswitch;

public final class DialReferenceValuePair {

    private final String room;
    private final String participant;

    public DialReferenceValuePair(String room, String participant) {
        this.room = room;
        this.participant = participant;
    }

    public String getRoom() {
        return this.room;
    }

    public String getParticipant() {
        return this.participant;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof DialReferenceValuePair)
                && ((DialReferenceValuePair) obj).room.equals(room)
                && ((DialReferenceValuePair) obj).participant.equals(participant);
    }
}
