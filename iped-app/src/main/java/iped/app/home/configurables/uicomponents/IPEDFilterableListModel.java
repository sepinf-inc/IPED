package iped.app.home.configurables.uicomponents;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.AbstractListModel;
import javax.swing.event.ListDataListener;

public class IPEDFilterableListModel<E> extends AbstractListModel<E> {
    Predicate<E> toHide;
    List<E> list;

    public IPEDFilterableListModel(List<E> list) {
        this.list = list;
    }

    public IPEDFilterableListModel(List<E> list, Predicate<E> toHide) {
        this(list);
        this.toHide = toHide;
    }

    @Override
    public int getSize() {
        int curIndex=0;
        for (Iterator iterator = list.iterator(); iterator.hasNext();) {
            E e = (E) iterator.next();
            if(toHide==null || !toHide.test(e)) {
                curIndex++;
            }
        }
        return curIndex;
    }
    @Override
    public E getElementAt(int index) {
        int curIndex=0;
        for (Iterator iterator = list.iterator(); iterator.hasNext();) {
            E e = (E) iterator.next();
            if(toHide==null || !toHide.test(e)) {
                if(curIndex==index) {
                    return e;
                }                        
                curIndex++;
            }
        }
        return null;
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
    
    public void setFilter(Predicate<E> filter) {
        this.toHide = filter;
    }
}
