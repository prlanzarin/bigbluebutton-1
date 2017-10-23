package org.bigbluebutton.common.messages;

import java.util.Map;
import java.util.HashMap;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class UpdateKurentoTokenMessage implements IBigBlueButtonMessage {
	public static final String UPDATE_KURENTO_TOKEN  = "update_kurento_token_message";
	public static final String VERSION = "0.0.1";

	public static final String KURENTO_TOKEN = "kurento_token";

	public final String kurentoToken;

	public UpdateKurentoTokenMessage(String kurentoToken) {
		this.kurentoToken = kurentoToken;
	}

	public String toJson() {
		HashMap<String, Object> payload = new HashMap<String, Object>();
		payload.put(KURENTO_TOKEN, kurentoToken);

		java.util.HashMap<String, Object> header = MessageBuilder.buildHeader(UPDATE_KURENTO_TOKEN, VERSION, null);

		return MessageBuilder.buildJson(header, payload);
	}

	public static UpdateKurentoTokenMessage fromJson(String message) {
		JsonParser parser = new JsonParser();
		JsonObject obj = (JsonObject) parser.parse(message);

		if (obj.has("header") && obj.has("payload")) {
			JsonObject header = (JsonObject) obj.get("header");
			JsonObject payload = (JsonObject) obj.get("payload");

			if (header.has("name")) {
				String messageName = header.get("name").getAsString();
				if (UPDATE_KURENTO_TOKEN.equals(messageName)) {
					if (payload.has(KURENTO_TOKEN)){
						String kurentoToken = payload.get(KURENTO_TOKEN).getAsString();
						return new UpdateKurentoTokenMessage(kurentoToken);
					}
				}
			}
		}
		return null;
	}
}
