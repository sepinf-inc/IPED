package iped.engine.task.transcript;

public class ProcessCrashedException extends RuntimeException {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public ProcessCrashedException() {
        super("External transcription process crashed.");
    }

}
