package tk.jasoryeh.conductor.downloaders.exceptions;

/**
 * Generic file retrieval exception
 */
public class RetrievalException extends Exception {
    public RetrievalException() {
        super();
    }

    public RetrievalException(String message) {
        super(message);
    }

    public RetrievalException(Exception causedBy) {
        super("Retrieval Exception caused by: " + causedBy.getCause() + " - " + causedBy.getLocalizedMessage());
        causedBy.printStackTrace();
    }
}
