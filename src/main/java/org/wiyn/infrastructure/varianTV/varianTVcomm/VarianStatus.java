package org.wiyn.infrastructure.varianTV.varianTVcomm;

import java.util.Date;
import java.util.concurrent.Semaphore;

/**
 * Created by harbeck on 1/26/17.
 *
 * Data holding class for  varian status.
 */
public class VarianStatus {


    Date lastUpdate;
    int OpsStatus;
    int ErrorStatus;

    float power; // poewr consupmtion in watts
    float rps; // rotations per second

    public VarianStatus() {

    }


    public String toString () {

        StringBuilder sb = new StringBuilder();


        sb.append ("========  Varian Status updated " + lastUpdate + "=====================\n\n" );

        sb.append ("Operational status:   " + OspStatusToString(OpsStatus) + "\n");

        sb.append ("Error Status:\n");
        sb.append (String.format ("|%-9s|%-9s|%-9s|%-9s|%-9s|%-9s|%-9s|%-9s| \n",
                "HI LOAD", "Over Cur", "Over Vol", "AUX", "POWER", "CTL TEMP", " PUMP TMP", "CONNECT"));

        sb.append ("|");
        for (int ii = 7; ii >=0; ii--) {
            boolean set = (ErrorStatus & (0x01 << ii) ) != 0;
            sb.append (String.format ("  %-5s  |", set ? "ERROR" : " OK "));
        }

        sb.append ("\n\n");

        sb.append (String.format ("RPS [Hz]: % 5f      Power [W]: % 5f ", rps, power));


        return sb.toString();
    }

    private String OspStatusToString(int opsStatus) {

        switch (opsStatus) {
            case VarianTV301.STATUS_STOP:
                return "STOPPED";
            case VarianTV301.STATUS_WAIT_INTERLOCK:
                return "Waiting for interlock";
            case VarianTV301.STATUS_STARTING:
                return "Starting...";
            case VarianTV301.STATUS_AUTO_TUNING:
                return "Auto-tuning";
            case VarianTV301.STATUS_BRAKING:
                return "Braking";
            case VarianTV301.STATUS_NORMAL:
                return "Normal";
            case VarianTV301.STATUS_FAIL:
                return "! F A I L !";

        }

        return ("UNKNOWN");
    }



    public static void main (String args[]) {
        VarianStatus vs = new VarianStatus();
        vs.lastUpdate = new Date();
        vs.ErrorStatus |= VarianTV301.FAIL_CONTRLOLLER_OVERTEMP;

        System.out.println (vs);
    }
}
