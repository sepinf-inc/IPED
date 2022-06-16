package iped.engine;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

import javax.swing.SwingUtilities;

public class WorkerProvider {

    private static WorkerProvider instance = new WorkerProvider();

    private ArrayList<PropertyChangeListener> listeners = new ArrayList<>();
    private ArrayList<PropertyChangeListener> uiListeners = new ArrayList<>();

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

    public void addPropertyChangeListener(PropertyChangeListener l, boolean isUIListener) {
        if (isUIListener) {
            uiListeners.add(l);
        } else {
            listeners.add(l);
        }
    }

    public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        PropertyChangeEvent event = new PropertyChangeEvent(this, propertyName, oldValue, newValue);
        for (PropertyChangeListener l : listeners) {
            l.propertyChange(event);
        }
        for (PropertyChangeListener l : uiListeners) {
            if (SwingUtilities.isEventDispatchThread()) {
                l.propertyChange(event);
            } else {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        l.propertyChange(event);
                    }
                });
            }
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
