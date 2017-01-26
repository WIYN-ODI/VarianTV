package org.wiyn.infrastructure.varianTV.varianTVcomm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by harbeck on 1/26/17.
 * <p>
 * Class to provide the communication interface with a Varian TV 301 turbo vacuum pump. A set of private classes
 * handles the  low level serial I/O, howver, this class instanciates only with Input / Output streams. It is
 * the responsibility of an external factory to create those streams.
 * <p>
 * The communication is intentionally hidden in private functions.
 */


public class VarianTV301 {

    Logger log = LogManager.getLogger(VarianTV301.class);
    public int commError = 0;

    enum COMMODE {RS232, RS485}


    // Definition of standard byte values for data flow management
    final static int STX = 0x02;
    final static int ETX = 0x03;
    final static int ACK = 0x06;
    final static int ADDRBASE = 0x80;
    final static int COMM_READ = 0x30;
    final static int COMM_WRITE = 0x31;


    // Definition of states
    public static final int STATUS_STOP = 0;
    public static final int STATUS_WAIT_INTERLOCK = 1;
    public static final int STATUS_STARTING = 2;
    public static final int STATUS_AUTO_TUNING = 3;
    public static final int STATUS_BRAKING = 4;
    public static final int STATUS_NORMAL = 5;
    public static final int STATUS_FAIL = 6;

    // Definition of error more bits.
    final static int FAIL_NOCONNECT = 0x01 << 0;
    final static int FAIL_PUMP_OVERTEMP = 0x01 << 1;
    final static int FAIL_CONTRLOLLER_OVERTEMP = 0x01 << 2;
    final static int FAIL_POWER = 0x01 << 3;
    final static int FAIL_AUX = 0x01 << 4;
    final static int FAIL_OVERVOLTAGE = 0x01 << 5;
    final static int FAIL_OVERCURRENT = 0x01 << 6;
    final static int FAIL_HIGHLOAD = 0x01 << 7;

    // RS485 Address. For future use.
    int myAddress = 0;

    /**
     * the maximum rotational frequency of the pump.
     */
    int maxFrequency = 963;


    COMMODE comMode = COMMODE.RS232;


    Semaphore busy = new Semaphore(1);

    /**
     * Output stream to write to the pump controller
     */
    private OutputStream outStream = null;
    /**
     * INputsteam to read back from the pump controller
     */
    private InputStream inStream = null;


    public static VarianTV301 connectViaTCPBridgeFactory(String hostname, int port) {

        // TODO: tcp socket stuff etc. then instnciate a VARIAN object with the tcp in / out streams.

        return null;

    }


    /**
     * Instanciate and set up communication with a device. in RS232 mode.
     *
     * @param in
     * @param out
     */

    public VarianTV301(InputStream in, OutputStream out) {
        this.inStream = in;
        this.outStream = out;
    }


    /**
     * Standard cleanup procedure.
     * <p>
     * This is to be called when shutting down the external application.
     */

    public void onExit() {

        try {
            if (inStream != null)
                inStream.close();
            if (outStream != null)
                outStream.close();

        } catch (IOException e) {
            log.error(e);
        }
    }



    public boolean updateStatus (VarianStatus s) {
        this.block();

        s.ErrorStatus = this.getCurrentErrorStatus();
        s.OpsStatus = this.getCurrentOperationStatus();
        this.release();
        if (commError == 0)
            return true;

        commError = 0;
        return false;
    }

    /**
     * Send the command to start the pump.
     */

    public boolean start_pump() {
        boolean retVal = false;
        this.block();
        writeBooleanMsg(100, true);
        retVal =  acknowledge() || (commError > 0);
        commError = 0;
        this.release();
        return retVal;
    }


    /**
     * Send command to shut own the pump
     */

    public boolean stop_pump() {
        boolean retVal = false;
        this.block();
        writeBooleanMsg(100, false);
        retVal =  acknowledge() || (commError > 0);
        commError = 0;
        this.release();
        return retVal;
    }


    /**
     * Request the current error status.
     * <p>
     * the returned byte needs to be interpreted bit by bit using the FAIL_.... values defined in this class.
     *
     * @return
     */
    private int getCurrentErrorStatus() {
        writeRequestMsg(206);
        float f = readNumericValue();
        return (int) f;
    }

    /**
     * Get the current operational status of the pump
     *
     * @return Comapre with STATUS_.... definitions.
     */
    private int getCurrentOperationStatus() {
        writeRequestMsg(205);
        float f = readNumericValue();
        return (int) f;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////
    //// INTERNAL FUNCTIONS FROM HERE ON.
    ////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    private String printBBasHext(byte[] bb) {

        StringBuilder sb = new StringBuilder();

        for (int ii = 0; ii < bb.length; ii++) {
            sb.append(String.format("%02x ", bb[ii]));
        }

        return sb.toString();

    }


    public boolean block () {
        try {
            return busy.tryAcquire(500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error ("Error while reserving serial communication");
        }
        log.warn ("trying to reserve serial communication timed out.");
        return false;
    }

    public void release () {
        this.busy.release();
    }


    /**
     * Wrapper to writeMessage to write boolean value to a command window
     *
     * @param window
     * @param value
     */

    private void writeBooleanMsg(int window, Boolean value) {
        writeMessage(window, value ? "1" : "0");
    }


    /**
     * Wrapper to writeMessage to write a number to a command window
     *
     * @param window
     * @param num
     */

    private void writeNumericMessage(int window, float num) {
        writeMessage(window, String.format("%06f"));
    }

    /**
     * Wrapper to writeMessage to write a string to a command window.
     *
     * @param window
     * @param msg
     */
    private void writeStringMessage(int window, String msg) {
        if (msg.length() > 10) {
            log.error("Message must not exceed 10 characters! Ignoring!");
            return;
        }

        msg = String.format("%10 s", msg);
        msg = msg.replace(" ", "_");
        writeMessage(window, msg);
    }

    /**
     * Wrapper to writeMessage to make a read request on a data / status window
     */

    private void writeRequestMsg(int window) {
        writeMessage(window, null);
    }


    /**
     * Write /read from a command / value window.
     * <p>
     * Proceduere handles checksumming, addressing, etc from this class' setup.
     *
     * @param window
     * @param data
     */

    private void writeMessage(int window, String data) {

        ByteArrayOutputStream bb = new ByteArrayOutputStream();

        bb.write((byte) STX);
        bb.write((byte) (ADDRBASE + myAddress));
        bb.write(String.format("%03d", window).getBytes(), 0, 3);

        if (data != null) {
            bb.write((byte) COMM_WRITE);
            bb.write(data.getBytes(), 0, data.length());
        } else {
            bb.write((byte) COMM_READ);
        }
        bb.write((byte) ETX);

        byte[] bbc = bb.toByteArray();

        int cr = bbc[1];
        for (int ii = 2; ii < bbc.length; ii++) {
            cr = cr ^ bbc[ii];
        }
        cr = cr & 0xFF; // force unisigned byte usage.

        log.debug("CR: " + cr);
        bb.write(String.format("%02x", cr).getBytes(), 0, 2);


        log.debug(printBBasHext(bb.toByteArray()));


        try {
            outStream.write(bb.toByteArray());

        } catch (IOException e) {
            log.error("While writing to Varian: ", e);
            commError++;
        }


    }


    /**
     * Read a numeric response from the pump controller.
     * <p>
     * This is a wrapper around the readMessage function to do the numeric parsing.
     *
     * @return
     */

    private float readNumericValue() {
        float value;
        String s = readStringMessage();
        try {
            value = Float.parseFloat(s);
        } catch (Exception e) {
            log.error("While reading back num value from pump controller msg  [" + s + "]", e);
            value = Float.NaN;
        }

        return value;

    }

    /**
     * read answer as boolean
     *
     * @return
     */

    private boolean readBooleanValue() {
        String s = readStringMessage();

        boolean retVal;
        try {
            retVal = Boolean.parseBoolean(s);
        } catch (Exception e) {
            retVal = false;
        }
        return retVal;
    }


    /**
     * Verify response is an acknowledge
     *
     * @return
     */
    private boolean acknowledge() {
        byte[] bb = readMessage();
        if (bb.length == 1 && bb[0] == ACK)
            return true;

        log.warn("Acknowledge failed, nessage received was " + printBBasHext(bb));
        return false;
    }


    private String readStringMessage() {
        return readMessage().toString();
    }

    /**
     * Low-level read back from pump controller.
     * <p>
     * Wait for start of transmisison byte
     * read back the device address
     * read the message until end of transmision is received
     * read and discard the two CRC bytes
     * <p>
     * TODO: do crc checksumming.
     *
     * @return
     */

    public byte[] readMessage() {


        ByteArrayOutputStream bb = new ByteArrayOutputStream();
        int address = 0;
        try {

            // First, wait for the magic start of transmisison byte
            while (inStream.read() != STX) ;

            // get address byte for future use
            address = inStream.read();

            // read in the raw message
            int inByte;
            while ((inByte = inStream.read()) != ETX) {

                bb.write((byte) inByte);
            }

            // read back the CRC to clear the line.
            inStream.read();
            inStream.read();

        } catch (IOException e) {
            log.error("While reading in message from pump", e);
            commError ++;
        }

        // For future use: sanity check on device address. IN case of RS242 we want to install a distibution of messages here.

        if (address != ADDRBASE + myAddress) {
            log.warn("Processing response from a different pump device. Check your setup!");
        }

        // And return the message.
        byte[] ba = bb.toByteArray();
        log.info("Read back from pump: addr [" + address + "]: " + this.printBBasHext(ba));
        return ba;
    }

    public static void main(String args[]) {
        String retVal = null;


        VarianTV301 tv = new VarianTV301(System.in, System.out);
        tv.writeBooleanMsg(0, true);

    }


}

