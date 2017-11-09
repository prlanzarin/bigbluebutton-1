#!/usr/bin/ruby
# encoding: UTF-8

# Copyright ⓒ 2017 BigBlueButton Inc. and by respective authors.
#
# This file is part of BigBlueButton open source conferencing system.
#
# BigBlueButton is free software: you can redistribute it and/or modify it
# under the terms of the GNU Lesser General Public License as published by the
# Free Software Foundation, either version 3 of the License, or (at your
# option) any later version.
#
# BigBlueButton is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
# FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more
# details.
# 
# You should have received a copy of the GNU Lesser General Public License
# along with BigBlueButton.  If not, see <http://www.gnu.org/licenses/>.

require '../lib/recordandplayback'
require 'rubygems'
require 'yaml'
require 'fileutils'

def sanity_archived_meetings(recording_dir)
  archived_done_files = Dir.glob("#{recording_dir}/status/archived/*.done")
  archived_done_files.each do |archived_done|
    match = /([^\/]*).done$/.match(archived_done)
    meeting_id = match[1]

    next if ! Dir.glob("#{recording_dir}/status/sanity*/#{meeting_id}.*").empty?

    BigBlueButton.redis_publisher.put_sanity_started(meeting_id)

    step_start_time = BigBlueButton.monotonic_clock
    ret = BigBlueButton.exec_ret("ruby", "sanity/sanity.rb", "-m", meeting_id)
    step_stop_time = BigBlueButton.monotonic_clock
    step_time = step_stop_time - step_start_time

    step_succeeded = (ret == 0)

    BigBlueButton.redis_publisher.put_sanity_ended(meeting_id, {
      "success" => step_succeeded,
      "step_time" => step_time
    })

    sanity_dir = find_sanity_dir(recording_dir)
    if step_succeeded
      sanity_done = "#{sanity_dir}/#{meeting_id}.done"
      FileUtils.touch(sanity_done)

      BigBlueButton.logger.info("Successfully sanity checked #{meeting_id}")
      post_archive(meeting_id)
      FileUtils.rm_f(archived_done)
    else
      BigBlueButton.logger.error("Sanity check failed on #{meeting_id}")
      sanity_fail = "#{sanity_dir}/#{meeting_id}.fail"
      FileUtils.touch(sanity_fail)
    end
  end
end

def find_sanity_dir(recording_dir)
  BigBlueButton.logger.info("Finding best sanity dir to create the .done file")
  possibilities = Dir.glob("#{recording_dir}/status/sanity*")
  best = nil
  possibilities.each do |sanity_dir|
    duration = 0.0
    Dir.glob("#{sanity_dir}/*.done").each do |sanity_done|
      match = /([^\/]*).done$/.match(sanity_done)
      record_id = match[1]
      next if ! File.exists? "#{recording_dir}/raw/#{record_id}/events.xml"
      rec_events = BigBlueButton::Events.match_start_and_stop_rec_events(BigBlueButton::Events.get_start_and_stop_rec_events("#{recording_dir}/raw/#{record_id}/events.xml"))
      rec_events.each do |e|
        duration += e[:stop_timestamp] - e[:start_timestamp]
      end
    end
    BigBlueButton.logger.info("#{sanity_dir} => #{duration}")
    if best.nil? || duration < best[:duration]
      best = {
        :duration => duration,
        :sanity_dir => sanity_dir
      }
    end
  end
  BigBlueButton.logger.info("Best: #{best[:sanity_dir]} => #{best[:duration]}")
  best[:sanity_dir]
end

def post_archive(meeting_id)
  Dir.glob("post_archive/*.rb").sort.each do |post_archive_script|
    match = /([^\/]*).rb$/.match(post_archive_script)
    post_type = match[1]
    BigBlueButton.logger.info("Running post archive script #{post_type}")

    BigBlueButton.redis_publisher.put_post_archive_started(post_type, meeting_id)

    step_start_time = BigBlueButton.monotonic_clock
    ret = BigBlueButton.exec_ret("ruby", post_archive_script, "-m", meeting_id)
    step_stop_time = BigBlueButton.monotonic_clock
    step_time = step_stop_time - step_start_time
    step_succeeded = (ret == 0)

    BigBlueButton.redis_publisher.put_post_archive_ended(post_type, meeting_id, {
      "success" => step_succeeded,
      "step_time" => step_time
    })

    if not step_succeeded
      BigBlueButton.logger.warn("Post archive script #{post_archive_script} failed")
    end
  end
end

begin
  props = YAML::load(File.open('bigbluebutton.yml'))
  redis_host = props['redis_host']
  redis_port = props['redis_port']
  BigBlueButton.redis_publisher = BigBlueButton::RedisWrapper.new(redis_host, redis_port)

  log_dir = props['log_dir']
  recording_dir = props['recording_dir']

  logger = Logger.new("#{log_dir}/bbb-rap-worker.log")
  logger.level = Logger::INFO
  BigBlueButton.logger = logger

  BigBlueButton.logger.debug("Running rap-sanity-worker...")
  
  sanity_archived_meetings(recording_dir)

  BigBlueButton.logger.debug("rap-sanity-worker done")

rescue Exception => e
  BigBlueButton.logger.error(e.message)
  e.backtrace.each do |traceline|
    BigBlueButton.logger.error(traceline)
  end
end
