package org.hwyl.sexytopo.comms.scanner;

/**
 * A way of reaching a scanner: Bluetooth, or developer mode's test scanner. It tells the
 * ScannerLink when it connects, what bytes arrive and when it disconnects.
 */
public interface ScannerConnection {

    void connect();

    void disconnect();

    boolean isConnected();

    void send(byte[] frame);
}
