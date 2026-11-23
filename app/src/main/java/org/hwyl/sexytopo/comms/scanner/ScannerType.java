package org.hwyl.sexytopo.comms.scanner;

/**
 * The kinds of cross-section scanner SexyTopo can link to, kept apart from InstrumentType as a
 * scanner is connected beside the instrument rather than in its place.
 */
public enum ScannerType {
    XSCAN("XScan-"),
    // developer mode's pretend scanner, so the link can be tried without one
    TEST(null);

    private final String prefix;

    ScannerType(String bluetoothPrefix) {
        this.prefix = bluetoothPrefix;
    }

    /** The scanner type a Bluetooth name belongs to, or null if it isn't a scanner. */
    public static ScannerType byName(String name) {
        if (name == null) {
            return null;
        }
        for (ScannerType type : values()) {
            if (type.prefix != null && name.startsWith(type.prefix)) {
                return type;
            }
        }
        return null;
    }

    public static boolean isScanner(String name) {
        return byName(name) != null;
    }
}
