package iped.app.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import iped.viewers.api.IFilterer;

public class FiltererMenu extends JPopupMenu implements ActionListener{
    private JMenuItem clearMenuitem;
    private Object selected;

    public FiltererMenu(){
        clearMenuitem = new JMenuItem("Clear filters");
        clearMenuitem.addActionListener(this);
        this.add(clearMenuitem);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if(e.getSource()==clearMenuitem) {
            ((IFilterer)selected).clearFilter();
        }

        App.get().filtersPanel.updateUI();
        App.get().getAppListener().updateFileListing();
    }
    
    public void setContext(Object o) {
        this.selected = o;
        if((o instanceof IFilterer)&&(((IFilterer)o).getDefinedFilters().size()>0)) {
            clearMenuitem.setVisible(true);
        }else {
            clearMenuitem.setVisible(false);
        }
    }
}
