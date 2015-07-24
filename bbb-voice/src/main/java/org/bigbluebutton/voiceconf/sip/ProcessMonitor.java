package org.bigbluebutton.voiceconf.sip;

import java.io.InputStream;

import org.slf4j.Logger;
import org.red5.logging.Red5LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;



public class ProcessMonitor implements Runnable {
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

    private Thread thread = null;
    private ProcessMonitorObserver observer;

    public ProcessMonitor(String[] command,String name) {
        this.command = command;
        this.process = null;
        this.inputStreamMonitor = null;
        this.errorStreamMonitor = null;
        this.name = name;
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
            log.debug("Creating thread to execute {}",this.name);
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
            log.debug("Exiting thread that executes {}. Exit value: {} ",this.name,ret);
            notifyProcessMonitorObserverOnFinished();
        }
        else{
            log.debug("Exiting thread that executes {}. Exit value: {}",this.name,ret);
            notifyProcessMonitorObserverOnFinishedUnsuccessfully();
        }
    }

    private void notifyProcessMonitorObserverOnFinishedUnsuccessfully() {
        if(observer != null){
            log.debug("Notifying ProcessMonitorObserver that process finished unsuccessfully");
            observer.handleProcessFinishedUnsuccessfully(this.name,inputStreamMonitor.getOutput());
        }else {
            log.debug("Cannot notify ProcessMonitorObserver that process finished unsuccessfully: ProcessMonitorObserver null");
        }
    }

    private void notifyProcessMonitorObserverOnFinished() {
        if(observer != null){
            log.debug("Notifying ProcessMonitorObserver that {} successfully finished",this.name);
            observer.handleProcessFinishedWithSuccess(this.name,inputStreamMonitor.getOutput());
        }else {
            log.debug("Cannot notify ProcessMonitorObserver that {} finished: ProcessMonitorObserver null",this.name);
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
            log.debug("Closing {} process",this.name);
            this.process.destroy();
            this.process = null;
        }
    }

    public void destroy() {
        clearData();
        log.debug("ProcessMonitor successfully finished");
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

    public void forceDestroy(){
        try {
            Runtime.getRuntime().exec("kill -9 "+ getPid());
        } catch (IOException e) {
            log.debug("Failed to force-kill {} process",this.name);
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
