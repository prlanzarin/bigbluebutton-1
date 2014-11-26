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
package org.bigbluebutton.webconference.voice.freeswitch.actions;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class DialCommand extends FreeswitchCommand {

	private static final Logger log = Red5LoggerFactory.getLogger(
			DialCommand.class, "bigbluebutton");
	private final Map<String, String> options;
	private final Map<String, String> params;
	private final String participant;

	private static final String DEFAULT_MODULE = "sofia";
	private static final String DEFAULT_PROFILE = "external";

	public DialCommand(String room, String participant,
			Map<String, String> options, Map<String, String> params,
			String requesterId) {
		super(room, requesterId);
		this.participant = participant;
		this.options = new HashMap<String, String>(options);
		this.params = new HashMap<String, String>(params);
		
		if (!params.containsKey("module")) {
			this.params.put("module", DEFAULT_MODULE);
		}
		if (!params.containsKey("profile")) {
			this.params.put("profile", DEFAULT_PROFILE);
		}
		this.params.put("caller_number", room);
		this.options.put("origination_callee_id_name", params.get("destination"));
	}

	public String getParticipant() {
		return participant;
	}
	
	private String getModuleProfile() {
		return params.get("module") + "/" + params.get("profile") + "/";
	}
	
	public String getDestination() {
		return params.get("destination");
	}

	public String getCompleteDestination() {
		return getModuleProfile() + getDestination();
	}

	public String getOriginationCallerIdName() {
		return options.get("origination_caller_id_name");
	}

	private String generateOptionArgs() {
		String stringOptions = "";

		if (options.isEmpty())
			return stringOptions;

		stringOptions = "{";
		Iterator<Map.Entry<String, String>> entries = options.entrySet()
				.iterator();
		while (entries.hasNext()) {
			Map.Entry<String, String> entry = entries.next();
			String optionsArgument = "\'" + entry.getValue() + "\'";
			stringOptions = stringOptions + entry.getKey() + "="
					+ optionsArgument;
			if (entries.hasNext())
				stringOptions = stringOptions + ",";
			else
				stringOptions = stringOptions + "}";
		}

		return stringOptions;
	}

	private String generateParamArgs() {
		String stringParams = "";

		if (params.isEmpty())
			return stringParams;
		else {
			stringParams = getCompleteDestination();
			if (params.containsKey("caller_number")) {
				stringParams = stringParams + SPACE
						+ params.get("caller_number");
				if (params.containsKey("caller_name"))
					stringParams = stringParams + SPACE
							+ params.get("caller_name");
			}
		}

		return stringParams;
	}

	@Override
	public String getCommandArgs() {
		String action = "dial";
		String command = room + SPACE + action + SPACE + generateOptionArgs()
				+ generateParamArgs();

		return command;
	}

	public void setOriginationUuid(String uuid) {
		this.options.put("origination_uuid", uuid);
	}
}
