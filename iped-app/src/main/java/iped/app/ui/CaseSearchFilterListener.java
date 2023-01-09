package iped.app.ui;

public interface CaseSearchFilterListener {
    public void init();

    public void onStart();

    public void onDone();

    public void onCancel(boolean mayInterruptIfRunning);
}
