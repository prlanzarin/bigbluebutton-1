package org.bigbluebutton.voiceconf.sip;

import java.io.InputStream;

import org.slf4j.Logger;
import org.red5.logging.Red5LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;

import org.bigbluebutton.voiceconf.red5.media.transcoder.VideoTranscoderObserver;


public class ProcessMonitor implements Runnable {
    private static Logger log = Red5LoggerFactory.getLogger(ProcessMonitor.class, "sip");

    private String[] command;
    private Process process;
    private static final int EXIT_WITH_SUCCESS_CODE = 0;
    private static final int FATAL_ERROR_CODE = 128;
    private static final int EXIT_WITH_SIGKILL_CODE = FATAL_ERROR_CODE + 9;
    private static final int ACCEPTABLE_EXIT_CODES[] = {EXIT_WITH_SUCCESS_CODE,EXIT_WITH_SIGKILL_CODE};
    ProcessStream inputStreamMonitor;
    ProcessStream errorStreamMonitor;

    private Thread thread = null;
    private VideoTranscoderObserver observer;

    public ProcessMonitor(String[] command) {
        this.command = command;
        this.process = null;
        this.inputStreamMonitor = null;
        this.errorStreamMonitor = null;
    }

    public String toString() {
        if (this.command == null || this.command.length == 0) { 
            return "";
        }
        
        StringBuffer result = new StringBuffer();
        String delim = "";
        for (String i : this.command) {
        	result.append(delim).append(i);
            delim = " ";
        }
        return result.toString();
    }

    public void setCommand(String[] command){
        this.command = command;
    }
    public void run() {
        try {
            log.debug("Creating thread to execute FFmpeg");
            log.debug("Executing: " + this.toString());
            this.process = Runtime.getRuntime().exec(this.command);

            if(this.process == null) {
                log.debug("process is null");
                return;
            }

            InputStream is = this.process.getInputStream();
            InputStream es = this.process.getErrorStream();

            inputStreamMonitor = new ProcessStream(is,"STDOUT");
            errorStreamMonitor = new ProcessStream(es,"STDERR");

            inputStreamMonitor.start();
            errorStreamMonitor.start();

            this.process.waitFor();
        }
        catch(SecurityException se) {
            log.debug("Security Exception");
        }
        catch(IOException ioe) {
            log.debug("IO Exception");
        }
        catch(NullPointerException npe) {
            log.debug("NullPointer Exception");
        }
        catch(IllegalArgumentException iae) {
            log.debug("IllegalArgument Exception");
        }
        catch(InterruptedException ie) {
            log.debug("Interrupted Exception");
        }

        int ret = this.process.exitValue();

        if (acceptableExitCode(ret)){
            log.debug("Exiting thread that executes FFmpeg. Exit value: "+ ret);
            notifyVideoTranscoderObserverOnFinished();
        }
        else{
            log.debug("Exiting thread that executes FFmpeg. Exit value: "+ ret);
            notifyVideoTranscoderObserverOnRestart();
        }
    }

    private void notifyVideoTranscoderObserverOnRestart() {
        if(observer != null){
            log.debug("Notifying VideoTranscoder to restart");
            observer.handleTranscodingRestarted();
        }else {
            log.debug("Cannot notify VideoTranscoder to restart: VideoTranscoderObserver null");
        }
    }

    private void notifyVideoTranscoderObserverOnFinished() {
        if(observer != null){
            log.debug("Notifying VideoTranscoderObserver that FFmpeg successfully finished");
            observer.handleTranscodingFinishedWithSuccess();
        }else {
            log.debug("Cannot notify VideoTranscoderObserver that FFmpeg finished: VideoTranscoderObserver null");
        }
    }

	public void start() {
        this.thread = new Thread(this);
        this.thread.start();
    }

    public void restart(){
        clearData();
        start();
    }

    public void clearData(){
        if(this.inputStreamMonitor != null 
            && this.errorStreamMonitor != null) {
            this.inputStreamMonitor.close();
            this.errorStreamMonitor.close();
            this.inputStreamMonitor = null;
            this.errorStreamMonitor = null;
        }

        if(this.process != null) {
            log.debug("Closing FFmpeg process");
            this.process.destroy();
            this.process = null;
        }
    }

    public void destroy() {
        clearData();
        log.debug("ProcessMonitor successfully finished");
    }

    public void setVideoTranscoderObserver(VideoTranscoderObserver observer){
        if (observer==null){
            log.debug("Cannot assign observer: VideoTranscoderObserver null");
        }else this.observer = observer;
    }

    public int getPid(){
        Field f;
        int pid;
        try {
            f = this.process.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            pid = (int)f.get(this.process);
            return pid;
        } catch (IllegalArgumentException | IllegalAccessException
                | NoSuchFieldException | SecurityException e) {
            // TODO Auto-generated catch block
            log.debug("Error when obtaining ffmpeg PID");
            return -1;
        }
    }

    public void forceDestroy(){
        try {
            Runtime.getRuntime().exec("kill -9 "+ getPid());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            log.debug("Failed to force-kill ffmpeg process");
            e.printStackTrace();
        }
    }

    public boolean acceptableExitCode(int code){
        int i;
        if ((ACCEPTABLE_EXIT_CODES == null) || (code < 0)) return false;
        for(i=0;i<ACCEPTABLE_EXIT_CODES.length;i++)
            if (ACCEPTABLE_EXIT_CODES[i] == code)
                return true;
        return false;
    }
}
