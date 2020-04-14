package pt.sayf.depot.server.exceptions;

/**
 * ObservationException class for invalid observations
 * 
 * @author DanLopess - Daniel Lopes
 * @version 1.0.0
 * @return Default: Observation is not valid
 */

public class ObservationException extends Exception {
    /**
     * Automatically Generated serialVersionUID
     */
    private static final long serialVersionUID = -1210338583919447663L;

    public ObservationException(String errorMessage) {
        super(errorMessage);
    }

    public ObservationException() {
        super("Observation is not valid.");
    }
}