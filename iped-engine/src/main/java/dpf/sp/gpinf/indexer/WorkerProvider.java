package dpf.sp.gpinf.indexer;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

public class WorkerProvider {

    private static WorkerProvider instance = new WorkerProvider();

    private ArrayList<PropertyChangeListener> listeners = new ArrayList<>();

    private Thread executorthread;

    private volatile boolean canceled = false;

    private WorkerProvider() {
    }

    public static WorkerProvider getInstance() {
        return instance;
    }

    public void setExecutorThread(Thread executorthread) {
        this.executorthread = executorthread;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        listeners.add(l);
    }

    public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        for (PropertyChangeListener l : listeners) {
            l.propertyChange(new PropertyChangeEvent(this, propertyName, oldValue, newValue));
        }
    }

    public boolean isCancelled() {
        return canceled;
    }

    public void cancel(boolean interrupt) {
        canceled = true;
        if (interrupt && executorthread != null) {
            executorthread.interrupt();
        }
    }

}
