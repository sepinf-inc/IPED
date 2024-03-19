package iped.app.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JTable;

import iped.data.IItemId;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IFilterer;
import iped.viewers.api.IItemRef;
import iped.viewers.api.IQuantifiableFilter;

public class FiltererMenu extends JPopupMenu implements ActionListener {
    private JMenuItem clearMenuitem;
    private Object selected;
    private JMenuItem gotToRefMenuItem;
    SliderMenuItem sliderMenuItem;

    public static final String CLEAR_FILTERS_STR = Messages.get("FiltererMenu.clearFilters");
    public static final String GOTO_ITEM_STR = Messages.get("FiltererMenu.goToItem");
    private static final String REFERENCED_ITEM_NOT_IN_RS = Messages.get("FiltererMenu.ItemNotInRS");

    public FiltererMenu() {
        clearMenuitem = new JMenuItem(CLEAR_FILTERS_STR);
        clearMenuitem.addActionListener(this);
        this.add(clearMenuitem);

        gotToRefMenuItem = new JMenuItem(GOTO_ITEM_STR);
        gotToRefMenuItem.addActionListener(this);
        this.add(gotToRefMenuItem);

        this.add(new JSeparator());
        sliderMenuItem = new SliderMenuItem();
        this.add(sliderMenuItem);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == clearMenuitem) {
            ((IFilterer) selected).clearFilter();
        }

        JTable table;
        if (e.getSource() == gotToRefMenuItem) {
            IItemId refId = ((IItemRef) selected).getItemRefId();
            gotoItem(refId);
            return;
        }

        App.get().filtersPanel.updateUI();
        App.get().getAppListener().updateFileListing();
    }

    static public void gotoItem(IItemId refId) {
        JTable table;
        if (refId != null) {
            table = App.get().resultsTable;
            IMultiSearchResult results = App.get().getResults();
            int row = -1;
            for (int i = 0; i < results.getLength(); i++) {
                if (results.getItem(i).equals(refId)) {
                    row = i;
                    break;
                }
            }
            if (row != -1) {
                table.changeSelection(table.convertRowIndexToView(row), 0, false, false);
            } else {
                JOptionPane.showMessageDialog(table, FiltererMenu.REFERENCED_ITEM_NOT_IN_RS);
            }
        }
    }

    public void setContext(Object o) {
        this.selected = o;
        if ((o instanceof IFilterer) && (((IFilterer) o).getDefinedFilters().size() > 0)) {
            clearMenuitem.setVisible(true);
        } else {
            clearMenuitem.setVisible(false);
        }
        if ((o instanceof IItemRef) && (((IItemRef) selected).getItemRefId() != null)) {
            gotToRefMenuItem.setVisible(true);
        } else {
            gotToRefMenuItem.setVisible(false);
        }

        sliderMenuItem.setVisible(false);
        if ((o instanceof IQuantifiableFilter)) {
            sliderMenuItem.setVisible(true);
            sliderMenuItem.setFilter((IQuantifiableFilter) o);
        }
    }

    @Override
    public void setVisible(boolean b) {
        if (!b && sliderMenuItem.hasSliderChanged()) {
            App.get().appletListener.updateFileListing();
        }
        super.setVisible(b);
    }
}
