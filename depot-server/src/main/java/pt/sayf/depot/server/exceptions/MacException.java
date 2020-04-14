package pt.sayf.depot.server.exceptions;

/**
 * MacException class for invalid MAC addresses
 * 
 * @author DanLopess - Daniel Lopes
 * @version 1.0.0
 * @return Mac is not valid
 */

public class MacException extends Exception {
    /**
     * Automatically Generated serialVersionUID
     */
    private static final long serialVersionUID = 3425479125460283457L;

    public MacException(String errorMessage) {
        super(errorMessage);
    }

    public MacException() {
        super("MAC is not valid.");
    }
}