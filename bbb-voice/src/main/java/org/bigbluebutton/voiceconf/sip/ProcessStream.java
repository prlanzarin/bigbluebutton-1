package org.bigbluebutton.voiceconf.sip;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.red5.logging.Red5LoggerFactory;

import java.io.IOException;

public class ProcessStream {
    private static Logger log = Red5LoggerFactory.getLogger(ProcessStream.class, "sip");
    private InputStream stream;
    private Thread thread;
    private String type;
    private String output;

    ProcessStream(InputStream stream, String type) {
        if(stream != null)
            this.stream = stream;
            this.type = type;
            this.output = "";
    }

    protected void start() {
        this.thread = new Thread( new Runnable(){
            public void run(){
                try {
                    String line;
                    InputStreamReader isr = new InputStreamReader(stream);
                    BufferedReader ibr = new BufferedReader(isr);
                    output = "";
                    while ((line = ibr.readLine()) != null) {
                        //log.debug("[{}]"+line,type);
                        output+=line+"\n";
                    }

                    close();
                }
                catch(IOException ioe) {
                    log.debug("Finishing process stream [type={}] because there's no more data to be read",type);
                    close();
                }
            }
        });
        this.thread.start();
    }

    protected void close() {
        try {
            if(this.stream != null) {
                //log.debug("Closing process stream");
                this.stream.close();
                this.stream = null;
            }
        }
        catch(IOException ioe) {
            log.debug("IOException");
        }
    }

    protected String getOutput(){
        return this.output;
    }
}
