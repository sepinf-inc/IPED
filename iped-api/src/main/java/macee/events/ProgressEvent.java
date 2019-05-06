package macee.events;

public class ProgressEvent {

    private final int progress;
    private int maximum = 0;

    public ProgressEvent(int value) {
        this.progress = value;
    }

    public ProgressEvent(int value, int maximum) {
        this(value);
        this.maximum = maximum;
    }

    public int getProgress() {
        return progress;
    }

    public int getMaximum() {
        return this.maximum;
    }

}
