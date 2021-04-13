package dpf.sp.gpinf.indexer.desktop;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Icon;

import bibliothek.gui.dock.common.action.CButton;

public class TimelineListener implements ActionListener, ClearFilterListener {

    private CButton timelineButton;
    private Icon defaultIcon, filteredIcon;
    private boolean timelineViewEnabled = false;

    public TimelineListener(CButton timelineButton, Icon filteredIcon) {
        this.timelineButton = timelineButton;
        this.defaultIcon = timelineButton.getIcon();
        this.filteredIcon = filteredIcon;
    }

    public boolean isTimelineViewEnabled() {
        return this.timelineViewEnabled;
    }

    @Override
    public void clearFilter() {
        timelineViewEnabled = false;
        updateGUI(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        toggleTimelineTableView();
    }

    public void toggleTimelineTableView() {
        timelineViewEnabled = !timelineViewEnabled;
        updateGUI(true);
    }

    private void updateGUI(boolean updateResults) {
        if (timelineViewEnabled) {
            timelineButton.setIcon(filteredIcon);
            ColumnsManager.getInstance().moveTimelineColumns(5);
        } else {
            timelineButton.setIcon(defaultIcon);
            ColumnsManager.getInstance().moveTimelineColumns(14);
        }
        if (updateResults) {
            App.get().appletListener.updateFileListing();
        }
    }

}
