package eu.dariolucia.reatmetric.processing;

public class ProcessingModelException extends Exception {
    public ProcessingModelException(String s) {
        super(s);
    }

    public ProcessingModelException(Throwable cause) {
        super(cause);
    }

    public ProcessingModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
