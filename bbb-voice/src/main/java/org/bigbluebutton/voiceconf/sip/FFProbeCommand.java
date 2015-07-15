package org.bigbluebutton.voiceconf.sip;

import java.lang.Runtime;

import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;

import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

public class FFProbeCommand {

  private static Logger log = Red5LoggerFactory.getLogger(ProcessMonitor.class, "sip");
  private String input;

  public FFProbeCommand(String input) {
    this.input = input;
  }

  public Map<String, String> run() throws IOException {
    String[] command = {
      "/usr/local/bin/ffprobe",
      "-select_streams", "v:0",
      "-show_streams",
      "-analyzeduration", "1",
      "-loglevel", "quiet",
      "-i", this.input
    };

    Process process = Runtime.getRuntime().exec(command);

    if(process == null) {
       log.debug("FFProbe: process is null");
       return null;
    }

    BufferedReader stdOutput = new BufferedReader(
            new InputStreamReader(process.getInputStream()));

    Pattern pattern = Pattern.compile("(.*)=(.*)");
    Map<String, String> result = new HashMap<String, String>();

    String line;
    while ((line = stdOutput.readLine()) != null) {
      Matcher matcher = pattern.matcher(line);
      if(matcher.matches()) {
        result.put(matcher.group(1), matcher.group(2));
      }
    }

    return result;
  }
}
