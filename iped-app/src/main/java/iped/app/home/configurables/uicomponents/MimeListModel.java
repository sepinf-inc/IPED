package iped.app.home.configurables.uicomponents;

import java.util.List;
import java.util.function.Predicate;

import javax.swing.ListModel;
import javax.swing.event.ListDataListener;

public class MimeListModel implements ListModel<String>{
    Predicate<String> toHide;
    List<String> availableMimes;

    public MimeListModel(List<String> list) {
        this.availableMimes = list;
    }

    public MimeListModel(List<String> list, Predicate<String> toHide) {
        this(list);
        this.toHide = toHide;
    }

    @Override
    public int getSize() {
        return (int) availableMimes.stream().filter(new Predicate<String>() {
            @Override
            public boolean test(String t) {
                return toHide==null || !toHide.test(t);
            }
        }).count();
    }
    @Override
    public String getElementAt(int index) {
        return (String) availableMimes.stream().filter(new Predicate<String>() {
            @Override
            public boolean test(String t) {
                return toHide==null || !toHide.test(t);
            }
        }).toArray()[index];
    }
    @Override
    public void addListDataListener(ListDataListener l) {
    }

    @Override
    public void removeListDataListener(ListDataListener l) {
    }
    public Object getViewToModelIndex(int i) {
        // TODO Auto-generated method stub
        return null;
    }
}
