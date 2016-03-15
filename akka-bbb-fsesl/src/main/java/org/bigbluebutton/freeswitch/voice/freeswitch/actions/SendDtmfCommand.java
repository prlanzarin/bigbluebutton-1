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
package org.bigbluebutton.freeswitch.voice.freeswitch.actions;

public class SendDtmfCommand extends FreeswitchCommand {

    private final String COMMAND = "uuid_send_dtmf";
    private final String uuid;
	private final String dtmfDigit;

    public SendDtmfCommand(String room, String uuid, String dtmfDigit, String requesterId) {
        super(room, requesterId);
        this.uuid = uuid;
		this.dtmfDigit = dtmfDigit;
    }

    public String getUuid() {
        return this.uuid;
    }

    public String getDtmfDigit() {
        return this.dtmfDigit;
    }

    public String getCommand() {
        return this.COMMAND;
    }

    public String getCommandArgs() {
	    return this.uuid + SPACE + this.dtmfDigit;
    }
}
