package dpf.sp.gpinf.indexer.desktop;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;

public class ClearFilterButton extends JButton {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private String oneFilter = Messages.getString("App.ClearFilter");
    private String manyFilters = Messages.getString("App.ClearFilters");

    private Color alertBackground = Color.RED;
    private Color alertForeground = Color.WHITE;
    private Color defaultBackground = this.getBackground();
    private Color defaultForeground = this.getForeground();

    private List<ClearFilterListener> listeners = new ArrayList<>();

    public ClearFilterButton() {
        super();
        this.addActionListener(new ClearAction());
    }

    public void addClearListener(ClearFilterListener listener) {
        this.listeners.add(listener);
    }

    public void setNumberOfFilters(int numFilters) {
        if (numFilters == 0) {
            this.setVisible(false);
        } else if (numFilters == 1) {
            this.setText(oneFilter);
            this.setBackground(defaultBackground);
            this.setForeground(defaultForeground);
            this.setVisible(true);
        } else {
            this.setText(manyFilters);
            this.setBackground(alertBackground);
            this.setForeground(alertForeground);
            this.setVisible(true);
        }
    }

    private class ClearAction implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            for (ClearFilterListener l : listeners) {
                l.clearFilter();
            }
            App.get().appletListener.updateFileListing();
        }

    }

}
