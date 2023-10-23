package iped.app.timelinegraph.popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

import iped.app.timelinegraph.IpedChartPanel;
import iped.app.ui.Messages;

public class SeriesAxisPopupMenu extends JPopupMenu implements ActionListener {

    IpedChartPanel ipedChartPanel;

    JMenuItem donotBreak;
    JMenuItem breakByBookmark;
    JMenuItem breakByMetadata;
    JMenuItem breakByCategory;

    ButtonGroup bg = new ButtonGroup();

    public SeriesAxisPopupMenu(IpedChartPanel ipedChartPanel) {
        this.ipedChartPanel = ipedChartPanel;

        donotBreak = new JRadioButtonMenuItem(Messages.getString("TimeLineGraph.donotBreak"));
        bg.add(donotBreak);
        donotBreak.addActionListener(this);
        add(donotBreak);

        breakByBookmark = new JRadioButtonMenuItem(Messages.getString("TimeLineGraph.breakByBookmark"), ipedChartPanel.getSplitByBookmark());
        bg.add(breakByBookmark);
        breakByBookmark.addActionListener(this);
        add(breakByBookmark);

        /*
         * breakByCategory = new
         * JRadioButtonMenuItem(Messages.getString("TimeLineGraph.breakByCategory"));
         * bg.add(breakByCategory); breakByCategory.addActionListener(this);
         * add(breakByCategory);
         */
        // TODO
        /*
         * breakByMetadata = new
         * JRadioButtonMenuItem(Messages.getString("TimeLineGraph.breakByMetadata"));
         * bg.add(breakByMetadata); breakByMetadata.addActionListener(this);
         * add(breakByMetadata);
         */
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == breakByBookmark) {
            ipedChartPanel.setSplitByBookmark(true);
        }
        if (e.getSource() == donotBreak) {
            ipedChartPanel.setSplitByBookmark(false);
            ipedChartPanel.setSplitByCategory(false);
        }
        if (e.getSource() == breakByMetadata) {
            // TODO
        }
        if (e.getSource() == breakByCategory) {
            ipedChartPanel.setSplitByCategory(true);
        }

        ipedChartPanel.getIpedChartsPanel().refreshChart();
    }

}
