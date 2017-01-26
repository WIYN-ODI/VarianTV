package org.wiyn.infrastructure.varianTV.gui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wiyn.infrastructure.varianTV.varianTVcomm.VarianStatus;
import org.wiyn.infrastructure.varianTV.varianTVcomm.VarianTV301;

import java.io.IOException;
import java.net.Socket;

/**
 * Created by harbeck on 1/26/17.
 */





public class TextStatusDisplay implements Runnable {

    Logger log = LogManager.getLogger(TextStatusDisplay.class);

    static String defaultHost = "";
    static int defaultPort = 8080;

    VarianTV301 varian = null;
    Socket tcpsocket = null;
    VarianStatus varianStatus = new VarianStatus();

    private boolean stop = false;


    public TextStatusDisplay() throws IOException {



        new Thread (this).start();



        System.in.read();
        stop = true;
        this.closeConenction();
        System.exit(0);

    }




    public void run() {

        while (!stop) {

            if (getConnection()) {

                while (varian.updateStatus(varianStatus)){
                    System.out.println (varianStatus);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }


            }

        }


    }


    private boolean getConnection () {



        try {
            tcpsocket = new Socket (defaultHost, defaultPort);
            tcpsocket.setSoTimeout(120 * 1000);

            varian = new VarianTV301(tcpsocket.getInputStream(), tcpsocket.getOutputStream());

            log.info("Connected to " + defaultHost + "  " + defaultPort);
        } catch (IOException e) {
            log.error ("Error while conencting ", e);
            return false;
        }

        return true;

    }


    private void closeConenction () {
        varian.block();
        try {
            tcpsocket.close();
        } catch (IOException e) {
            log.error ("While closing socket: ", e);
        }
    }


    public static void main (String args[]) {



        if (args.length>0) {
            defaultHost= args[1];
            defaultPort = Integer.parseInt(args[2]);
        }


        try {
            new TextStatusDisplay();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

}
