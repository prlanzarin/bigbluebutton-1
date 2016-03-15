/**
 * BigBlueButton open source conferencing system - http://www.bigbluebutton.org/
 *
 * Copyright (c) 2015 BigBlueButton Inc. and by respective authors (see below).
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

package org.bigbluebutton.modules.phone.events
{
    import flash.events.Event;

    public class RequestedSipParamsEvent extends Event
    {

        public static const REQUESTED_SIP_PARAMS_EVENT:String = 'requested sip params event';
		public var sipServerHost:String;


        public function RequestedSipParamsEvent(sipServerHost:String)
        {
            super(REQUESTED_SIP_PARAMS_EVENT, true, false);
            this.sipServerHost = sipServerHost;
        }
    }
}

