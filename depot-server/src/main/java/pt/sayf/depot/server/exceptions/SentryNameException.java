package pt.sayf.depot.server.exceptions;

/**
 * SentryNameException class for invalid sentryNames
 * 
 * @author DanLopess - Daniel Lopes
 * @version 1.0.0
 * @return Sentry Name is not valid
 */

public class SentryNameException extends Exception { 
    /**
     * Automatically Generated serialVersionUID 
     */
    private static final long serialVersionUID = -1673906806125996502L;

    public SentryNameException(String errorMessage) {
        super(errorMessage);
    }

    public SentryNameException() {
        super("Sentry Name is not valid.");
    }
}