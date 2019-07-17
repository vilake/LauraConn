/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package lauraconn;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.TooManyListenersException;
import gnu.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 *
 * @author Viorel Trusca
 */
public class LauraConn implements Runnable, SerialPortEventListener {

    static CommPortIdentifier portId;
    static Enumeration portList;
    InputStream inputStream;
    SerialPort serialPort;
    static String commNo;
    Thread readThread;
    String cerere = null;
    String newLine = null;
    int cerereID = 0;
    boolean validEntry;
    static final char LAURA_ETX = 0x03;
    static final char LAURA_LF = 0x0a;
    Connection conn = null;
    Statement stmt = null;

    public static void main(String[] args) {

        if (args.length > 0) {
            commNo = args[0];
        } else {
            commNo = "COM1";
        }

        portList = CommPortIdentifier.getPortIdentifiers();

        while (portList.hasMoreElements()) {
            portId = (CommPortIdentifier) portList.nextElement();
            if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                if (portId.getName().equals(commNo)) {
                    LauraConn reader = new LauraConn();
                }
            }
        }
    }

    public LauraConn() {
        try {
            serialPort = (SerialPort) portId.open("LauraConn", 2000);
        } catch (PortInUseException e) {
            System.out.println(e);
        }
        try {
            inputStream = serialPort.getInputStream();
        } catch (IOException e) {
            System.out.println(e);
        }
        try {
            serialPort.addEventListener(this);
        } catch (TooManyListenersException e) {
            System.out.println(e);
        }
        serialPort.notifyOnDataAvailable(true);
        try {
            serialPort.setSerialPortParams(19200,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);
        } catch (UnsupportedCommOperationException e) {
            System.out.println(e);
        }
        readThread = new Thread(this);
        readThread.start();
        System.out.println("Astept rezultate ...");
    }

    @Override
    public void run() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.out.println(e);
        }
    }

    private String getParamID(String lauraCode) {
        String paramID = "";
        switch (lauraCode) {
            case "BLD":
                paramID = "26";
                break;
            case "LEU":
                paramID = "31";
                break;
            case "BIL":
                paramID = "27";
                break;
            case "UBG":
                paramID = "35";
                break;
            case "KET":
                paramID = "28";
                break;
            case "GLU":
                paramID = "30";
                break;
            case "PRO":
                paramID = "33";
                break;
            case "pH":
                paramID = "34";
                break;
            case "NIT":
                paramID = "32";
                break;
            case "SG":
                paramID = "29";
                break;
        }
        //System.out.println("cod="+lauraCode+", id="+paramID);
        return paramID;
    }

    private String getResultValue(String result, String paramCode) {
        String rtn = result;
        if (result.equals("NEG")) {
            rtn = "negativ";
        } else {
            if (result.equals("NORM")) {
                rtn = "normal";
            }
        }

        if ((paramCode.equals("SG")) && (result.contains("."))) {
            String[] parti = result.split("\\.");
            rtn = "";
            for (String tmp : parti) {
                rtn += tmp;
            }
        }
        //System.out.println(paramCode + " ---> "+rtn);
        return rtn;
    }

    private void loadMySQLDriver() {
        String dbUrl = "jdbc:mysql://192.168.1.3/profdiag?"
                + "user=dummy&password=secret";

        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            conn = DriverManager.getConnection(dbUrl);
            stmt = conn.createStatement();
        } //end try
        catch (Exception ex) {
            System.out.println("Exception: " + ex.getMessage());
        }
    }

    public void unloadMySQLDriver() {
        try {
            if (stmt != null) {
                stmt.close();
            }
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            System.out.println("Exception: " + e.getMessage());
        }
    }

    private int updateMySQL(String sql) {
        int rowsEffected = 0;
        try {
            if (conn != null) {
                rowsEffected = stmt.executeUpdate(sql);
                if (false == conn.getAutoCommit()) {
                    conn.commit();
                }
            }
            return rowsEffected;
        } catch (SQLException e) {
            e.printStackTrace(System.out);
            return 0;
        }
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        switch (event.getEventType()) {
            case SerialPortEvent.BI:
            case SerialPortEvent.OE:
            case SerialPortEvent.FE:
            case SerialPortEvent.PE:
            case SerialPortEvent.CD:
            case SerialPortEvent.CTS:
            case SerialPortEvent.DSR:
            case SerialPortEvent.RI:
            case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
                break;
            case SerialPortEvent.DATA_AVAILABLE:
                int c;
                validEntry = false;
                String paramCode;
                String rezultat;
                StringBuilder readBuffer = new StringBuilder();
                loadMySQLDriver();
                try {
                    while ((c = inputStream.read()) != -1) {
                        //System.out.print((char) c);
                        if (c == LAURA_LF) {   /* linie noua */
                            readBuffer.append((char) c);
                            if ((readBuffer != null) && (!readBuffer.toString().isEmpty())) {
                                System.out.print(readBuffer);
                                if (readBuffer.toString().startsWith("Pat.ID:")) {
                                    validEntry = true;
                                    try {
                                        cerere = readBuffer.substring(9, 15).trim();
                                        cerereID = Integer.parseInt(cerere);
                                    } catch (NumberFormatException ex) {
                                        validEntry = false;
                                    }
                                } else if (((readBuffer.toString().startsWith(" "))
                                        || (readBuffer.toString().startsWith("*")))
                                        && (validEntry)) {
                                    // este linie cu rezultate
                                    paramCode = readBuffer.substring(1, 4).trim();
                                    rezultat = readBuffer.substring(4, 11).trim();
                                    // updatez baza de date
                                    // update rezultate set valoare_p='75', anormal=1
                                    // where cerere_id=48003 and analiza_id=122 and param_id=29
                                    // daca nu intoarce ca s-a modificat cel putin o inregistrare
                                    // intorc eroare ca nu exista cererea
                                    String sql = "update rezultate set valoare_p='"
                                            + getResultValue(rezultat, paramCode) + "'";
                                    if (readBuffer.toString().startsWith("*")) {
                                        sql += ", anormal=1";
                                    }
                                    sql += " where cerere_id=";
                                    sql += cerere;
                                    sql += " and analiza_id=122 and param_id=";
                                    sql += getParamID(paramCode);
                                    //System.out.println(sql);
                                    if (updateMySQL(sql) == 0) {
                                        validEntry = false;
                                    }
                                }
                            }
                            readBuffer.setLength(0);
                        } else {
                            if (c == LAURA_ETX) {
                                if (!validEntry) {
                                    System.out.println("EROARE: ID-ul cererii nu exista in baza de date. "
                                            + "Verifica daca ai introdus corect ID-ul in aparat, la Pat.ID !!!\n\r");
                                }
                                validEntry = false;
                            }
                            readBuffer.append((char) c);
                        }
                    }
                    System.out.println("Astept rezultate ...");

                } catch (IOException e) {
                    System.out.println(e);
                }
                unloadMySQLDriver();
                break;
        }
    }
}
