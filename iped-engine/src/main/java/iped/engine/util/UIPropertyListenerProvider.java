package iped.engine.util;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

import javax.swing.SwingUtilities;

public class UIPropertyListenerProvider {

    private static UIPropertyListenerProvider instance = new UIPropertyListenerProvider();

    private ArrayList<PropertyChangeListener> listeners = new ArrayList<>();
    private ArrayList<PropertyChangeListener> uiListeners = new ArrayList<>();

    private Thread executorthread;

    private volatile boolean canceled = false;

    private UIPropertyListenerProvider() {
    }

    public static UIPropertyListenerProvider getInstance() {
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
