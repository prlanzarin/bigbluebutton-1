package org.bigbluebutton.voiceconf.sip;

import java.io.InputStream;

import org.slf4j.Logger;
import org.red5.logging.Red5LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class ProcessMonitor {
    private static Logger log = Red5LoggerFactory.getLogger(ProcessMonitor.class, "sip");

    private String[] command;
    private Process process;
    private String name;
    private static final int EXIT_WITH_SUCCESS_CODE = 0;
    private static final int FATAL_ERROR_CODE = 128;
    private static final int EXIT_WITH_SIGKILL_CODE = FATAL_ERROR_CODE + 9;
    private static final int ACCEPTABLE_EXIT_CODES[] = {EXIT_WITH_SUCCESS_CODE,EXIT_WITH_SIGKILL_CODE};
    ProcessStream inputStreamMonitor;
    ProcessStream errorStreamMonitor;
    private String inputStreamMonitorOutput;
    private String errorStreamMonitorOutput;

    private Thread thread;
    private ProcessMonitorObserver observer;

    public ProcessMonitor(String[] command,String name) {
        this.command = command;
        this.process = null;
        this.thread = null;
        this.inputStreamMonitor = null;
        this.errorStreamMonitor = null;
        this.name = name;
        this.inputStreamMonitorOutput = null;
        this.errorStreamMonitor = null;
    }

    @Override
    public String toString() {
        if (this.command == null || this.command.length == 0) { 
            return "";
        }
        
        Pattern pattern = Pattern.compile("(.*) (.*)");
        StringBuffer result = new StringBuffer();
        String delim = "";
        for (String i : this.command) {
            Matcher matcher = pattern.matcher(i);
            if(matcher.matches()) {
                result.append(delim).append("\""+matcher.group(1)+" "+matcher.group(2)+"\"");
            }else result.append(delim).append(i);
            delim = " ";
        }
        return removeLogLevelFlag(result.toString());
    }

    private String getCommandString(){
        //used by the process's thread instead of toString()
        return this.toString();
    }

    public void setCommand(String[] command){
        this.command = command;
    }

    private void notifyProcessMonitorObserverOnFinishedUnsuccessfully() {
        if(observer != null){
            log.debug("Notifying ProcessMonitorObserver that process finished unsuccessfully");
            observer.handleProcessFinishedUnsuccessfully(this.name,inputStreamMonitorOutput);
        }else {
            log.debug("Cannot notify ProcessMonitorObserver that process finished unsuccessfully: ProcessMonitorObserver null");
        }
    }

    private void notifyProcessMonitorObserverOnFinished() {
        if(observer != null){
            log.debug("Notifying ProcessMonitorObserver that {} successfully finished",this.name);
            observer.handleProcessFinishedWithSuccess(this.name,inputStreamMonitorOutput);
        }else {
            log.debug("Cannot notify ProcessMonitorObserver that {} finished: ProcessMonitorObserver null",this.name);
        }
    }

	public synchronized void start() {
        if(this.thread == null){
            this.thread = new Thread( new Runnable(){
                public void run(){
                    try {
                        log.debug("Creating thread to execute {}",name);
                        process = Runtime.getRuntime().exec(command);
                        log.debug("Executing (pid={}): {}",getPid(),getCommandString());

                        if(process == null) {
                            log.debug("process is null");
                            return;
                        }

                        InputStream is = process.getInputStream();
                        InputStream es = process.getErrorStream();

                        inputStreamMonitor = new ProcessStream(is,"STDOUT");
                        errorStreamMonitor = new ProcessStream(es,"STDERR");

                        inputStreamMonitor.start();
                        errorStreamMonitor.start();

                        process.waitFor();
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

                    int ret = process.exitValue();

                    if (acceptableExitCode(ret)){
                        log.debug("Exiting thread that executes {}. Exit value: {} ",name,ret);
                        storeProcessOutputs(inputStreamMonitor.getOutput(), errorStreamMonitor.getOutput());
                        clearData();
                        notifyProcessMonitorObserverOnFinished();
                    }
                    else{
                        log.debug("Exiting thread that executes {}. Exit value: {}",name,ret);
                        storeProcessOutputs(inputStreamMonitor.getOutput(), errorStreamMonitor.getOutput());
                        clearData();
                        notifyProcessMonitorObserverOnFinishedUnsuccessfully();
                    }
                }
            });
            this.thread.start();
        }else
            log.debug("Can't start a new process monitor: It is already running.");
    }

    public synchronized void restart(){
        clearData();
        start();
    }

    private void clearData(){
        closeProcessStream();
        closeProcess();
        clearMonitorThread();
    }

    private void clearMonitorThread(){
        if (this.thread !=null)
            this.thread=null;
    }

    private void closeProcessStream(){
        if(this.inputStreamMonitor != null){
            this.inputStreamMonitor.close();
            this.inputStreamMonitor = null;
        }
        if (this.errorStreamMonitor != null) {
            this.errorStreamMonitor.close();
            this.errorStreamMonitor = null;
        }
    }

    private void closeProcess(){
        if(this.process != null) {
            log.debug("Closing {} process",this.name);
            this.process.destroy();
            this.process = null;
        }
    }

    private void storeProcessOutputs(String inputStreamOutput,String errorStreamOutput){
        this.inputStreamMonitorOutput = inputStreamOutput;
        this.errorStreamMonitorOutput = errorStreamOutput;
    }

    public synchronized void destroy() {
        if (this.thread != null){
            clearData();
            log.debug("ProcessMonitor successfully finished");
        }else
            log.debug("Can't destroy this process monitor: There's no process running.");
    }

    public void setProcessMonitorObserver(ProcessMonitorObserver observer){
        if (observer==null){
            log.debug("Cannot assign observer: ProcessMonitorObserver null");
        }else this.observer = observer;
    }

    public int getPid(){
        Field f;
        int pid;
        try {
            if (this.process == null) return -1;
            f = this.process.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            pid = (int)f.get(this.process);
            return pid;
        } catch (IllegalArgumentException | IllegalAccessException
                | NoSuchFieldException | SecurityException e) {
            log.debug("Error when obtaining {} PID",this.name);
            return -1;
        }
    }

    public synchronized void forceDestroy(){
        if (this.thread != null) {
        try {
            int pid = getPid();
            if (pid < 0){
                log.debug("Process doesn't exist. Not destroying it...");
                return;
            }else
                Runtime.getRuntime().exec("kill -9 "+ getPid());
        } catch (IOException e) {
            log.debug("Failed to force-kill {} process",this.name);
            e.printStackTrace();
        }
        }else
            log.debug("Can't force-destroy this process monitor: There's no process running.");
    }

    private boolean acceptableExitCode(int code){
        int i;
        if ((ACCEPTABLE_EXIT_CODES == null) || (code < 0)) return false;
        for(i=0;i<ACCEPTABLE_EXIT_CODES.length;i++)
            if (ACCEPTABLE_EXIT_CODES[i] == code)
                return true;
        return false;
    }

    public boolean isFFmpegProcess(){
        return this.name.toLowerCase().contains("ffmpeg");
    }

    /**
     * Removes loglevel flag of ffmpeg command.
     * Usefull for faster debugging
     */
    private String removeLogLevelFlag(String commandString){
        if (isFFmpegProcess()){
            return commandString.replaceAll("-loglevel \\w+", "");
        }else return commandString;
    }

}
