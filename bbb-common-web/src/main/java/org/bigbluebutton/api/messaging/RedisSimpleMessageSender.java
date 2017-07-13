/**
* BigBlueButton open source conferencing system - http://www.bigbluebutton.org/
*
* Copyright (c) 2017 BigBlueButton Inc. and by respective authors (see below).
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
package org.bigbluebutton.api.messaging;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;

import org.bigbluebutton.common.messages.PublishRecordingMessage;
import org.bigbluebutton.common.messages.UnpublishRecordingMessage;
import org.bigbluebutton.common.messages.DeleteRecordingMessage;
import org.bigbluebutton.common.messages.MessagingConstants;

public class RedisSimpleMessageSender {
	private static Logger log = LoggerFactory.getLogger(RedisSimpleMessageSender.class);

	private JedisPool redisPool;
	private volatile boolean sendMessage = false;

	private final Executor msgSenderExec = Executors.newSingleThreadExecutor();
	private final Executor runExec = Executors.newSingleThreadExecutor();
	private BlockingQueue<MessageToSend> messages = new LinkedBlockingQueue<MessageToSend>();

	private String host;
	private int port;

	public void stop() {
		sendMessage = false;
		redisPool.destroy();
	}

	public void start() {
		GenericObjectPoolConfig config = new GenericObjectPoolConfig();
		config.setMaxTotal(32);
		config.setMaxIdle(8);
		config.setMinIdle(1);
		config.setTestOnBorrow(true);
		config.setTestOnReturn(true);
		config.setTestWhileIdle(true);
		config.setNumTestsPerEvictionRun(12);
		config.setMaxWaitMillis(5000);
		config.setTimeBetweenEvictionRunsMillis(60000);
		config.setBlockWhenExhausted(true);

		// Set the name of this client to be able to distinguish when doing
		// CLIENT LIST on redis-cli
		redisPool = new JedisPool(config, host, port, Protocol.DEFAULT_TIMEOUT, null, Protocol.DEFAULT_DATABASE, "BbbCommonWebPub");

		log.info("Redis message publisher starting!");
		try {
			sendMessage = true;

			Runnable messageSender = new Runnable() {
				public void run() {
					while (sendMessage) {
						try {
							MessageToSend msg = messages.take();
							publish(msg.getChannel(), msg.getMessage());
						} catch (InterruptedException e) {
							log.warn("Failed to get message from queue.");
						}
					}
				}
			};
			msgSenderExec.execute(messageSender);
		} catch (Exception e) {
			log.error("Error subscribing to channels: " + e.getMessage());
		}
	}

	public void send(String channel, String message) {
		MessageToSend msg = new MessageToSend(channel, message);
		messages.add(msg);
	}

	private void publish(final String channel, final String message) {
		Runnable task = new Runnable() {
			public void run() {
				Jedis jedis = redisPool.getResource();
				try {
					jedis.publish(channel, message);
				} catch(Exception e){
					log.warn("Cannot publish the message to pubsub", e);
				} finally {
					if (jedis != null) {
						jedis.close();
					}
				}
			}
		};

		runExec.execute(task);
	}

	public void setHost(String host){
		this.host = host;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void publishRecording(String recordId, String meetingId, String externalMeetingId, String format, boolean publish) {
		if (publish) {
			publishRecording(recordId, meetingId, externalMeetingId, format);
		} else {
			unpublishRecording(recordId, meetingId, externalMeetingId, format);
		}
	}

	private void publishRecording(String recordId, String meetingId, String externalMeetingId, String format) {
		PublishRecordingMessage msg = new PublishRecordingMessage(recordId, meetingId, externalMeetingId, format);
		send(MessagingConstants.FROM_BBB_RECORDING_CHANNEL, msg.toJson());
	}

	private void unpublishRecording(String recordId, String meetingId, String externalMeetingId, String format) {
		UnpublishRecordingMessage msg = new UnpublishRecordingMessage(recordId, meetingId, externalMeetingId, format);
		send(MessagingConstants.FROM_BBB_RECORDING_CHANNEL, msg.toJson());
	}

	public void deleteRecording(String recordId, String meetingId, String externalMeetingId, String format) {
		DeleteRecordingMessage msg = new DeleteRecordingMessage(recordId, meetingId, externalMeetingId, format);
		send(MessagingConstants.FROM_BBB_RECORDING_CHANNEL, msg.toJson());
	}
}