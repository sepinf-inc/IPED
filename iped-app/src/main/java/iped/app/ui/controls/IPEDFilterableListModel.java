package iped.app.ui.controls;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.AbstractListModel;
import javax.swing.event.ListDataListener;

public class IPEDFilterableListModel<E> extends AbstractListModel<E> {
    Predicate<E> toHide;
    List<E> list;
    private int lastIndex=Integer.MAX_VALUE;
    private Iterator<E> iterator;
    private E e;

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
        if(list!=null) {
            for (Iterator iterator = list.iterator(); iterator.hasNext();) {
                E e = (E) iterator.next();
                if(toHide==null || !toHide.test(e)) {
                    curIndex++;
                }
            }
        }
       return curIndex;
    }
    @Override
    public E getElementAt(int index) {
        if(lastIndex>index) {
            iterator = list.iterator();
            lastIndex=-1;
        }
        if(lastIndex==index) {
            return e;
        }
        for (; iterator.hasNext();) {
            e = (E) iterator.next();
            if(toHide==null || !toHide.test(e)) {
                lastIndex++;
                if(lastIndex==index) {
                    return e;
                }                        
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
