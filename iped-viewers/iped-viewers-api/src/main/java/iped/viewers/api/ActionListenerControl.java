package iped.viewers.api;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.HashSet;

public interface ActionListenerControl {
    HashMap<ActionListenerControl, HashSet<ActionListener>> listenerLists = new HashMap();

    public default void addActionListener(ActionListener listener) {
        HashSet<ActionListener> listenerList = listenerLists.get(this);
        if (listenerList == null) {
            listenerList = new HashSet<>();
            listenerLists.put(this, listenerList);
        }
        listenerList.add(listener);
    }

    public default void removeActionListener(ActionListener listener) {
        HashSet<ActionListener> listenerList = listenerLists.get(this);
        if (listenerList == null) {
            listenerList = new HashSet<>();
            listenerLists.put(this, listenerList);
        }
        listenerList.remove(listener);
    }

    public default HashSet<ActionListener> getActionListenerList() {
        return listenerLists.get(this);
    }

    default void fireActionListener(ActionEvent actionEvent) {
        HashSet<ActionListener> listenerList = getActionListenerList();
        if (listenerList != null) {
            for (ActionListener l : listenerList) {
                l.actionPerformed(actionEvent);
            }
        }
    }

}
