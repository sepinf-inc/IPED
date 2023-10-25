package iped.app.timelinegraph.popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.jfree.chart.entity.XYItemEntity;
import org.roaringbitmap.RoaringBitmap;

import iped.app.timelinegraph.IpedChartPanel;
import iped.app.timelinegraph.datasets.IpedTimelineDataset;
import iped.app.timelinegraph.swingworkers.BitSetHighlightWorker;
import iped.app.timelinegraph.swingworkers.EventPeriodCheckWorker;
import iped.app.ui.Messages;
import iped.data.IItemId;
import iped.engine.data.IPEDSource;
import iped.viewers.api.IMultiSearchResultProvider;

public class DataItemPopupMenu extends JPopupMenu implements ActionListener {
    XYItemEntity chartEntity;
    IpedChartPanel ipedChartPanel;

    JMenuItem highlightEventItens;
    JMenuItem highlightPeriodItens;
    JMenuItem checkEventItens;
    JMenuItem checkPeriodItens;

    List<XYItemEntity> entityList;

    public DataItemPopupMenu(IpedChartPanel ipedChartPanel) {
        this.ipedChartPanel = ipedChartPanel;

        highlightEventItens = new JMenuItem(Messages.getString("TimeLineGraph.highlightEventItensOnPeriod"));
        highlightEventItens.addActionListener(this);
        add(highlightEventItens);

        highlightPeriodItens = new JMenuItem(Messages.getString("TimeLineGraph.highlightItensOnPeriod"));
        highlightPeriodItens.addActionListener(this);
        add(highlightPeriodItens);

        checkEventItens = new JMenuItem(Messages.getString("TimeLineGraph.checkEventItensOnPeriod"));
        checkEventItens.addActionListener(this);
        add(checkEventItens);

        checkPeriodItens = new JMenuItem(Messages.getString("TimeLineGraph.checkItensOnPeriod"));
        checkPeriodItens.addActionListener(this);
        add(checkPeriodItens);
    }

    public XYItemEntity getChartEntity() {
        return chartEntity;
    }

    public void setChartEntity(XYItemEntity chartEntity) {
        this.chartEntity = chartEntity;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == highlightEventItens) {
            Calendar c = Calendar.getInstance(ipedChartPanel.getIpedChartsPanel().getTimeZone());
            c.setTime(new Date((long) chartEntity.getDataset().getXValue(chartEntity.getSeriesIndex(), chartEntity.getItem())));
            Calendar cEnd = (Calendar) c.clone();
            cEnd.add(Calendar.DAY_OF_MONTH, 1);

            IMultiSearchResultProvider msrp = ipedChartPanel.getIpedChartsPanel().getResultsProvider();
            IPEDSource is = (IPEDSource) msrp.getIPEDSource();

            RoaringBitmap bs = new RoaringBitmap();
            IpedTimelineDataset ds = (IpedTimelineDataset) chartEntity.getDataset();
            List<IItemId> ids = ds.getItems(chartEntity.getItem(), chartEntity.getSeriesIndex());
            if (ids != null) {
                for (Iterator iterator = ids.iterator(); iterator.hasNext();) {
                    IItemId iItemId = (IItemId) iterator.next();
                    bs.add(is.getLuceneId(iItemId));
                }
            }

            BitSetHighlightWorker bsHighlight = new BitSetHighlightWorker(ipedChartPanel.getIpedChartsPanel().getDomainAxis(), msrp, bs, true);
            bsHighlight.execute();
        }

        if (e.getSource() == highlightPeriodItens) {
            Calendar c = Calendar.getInstance(ipedChartPanel.getIpedChartsPanel().getTimeZone());
            c.setTime(new Date((long) chartEntity.getDataset().getXValue(chartEntity.getSeriesIndex(), chartEntity.getItem())));
            Calendar cEnd = (Calendar) c.clone();
            cEnd.add(Calendar.DAY_OF_MONTH, 1);
            ipedChartPanel.getIpedChartsPanel().highlightItemsOnInterval(c.getTime(), cEnd.getTime(), true);
        }
        if (e.getSource() == checkEventItens) {
            Calendar c = Calendar.getInstance(ipedChartPanel.getIpedChartsPanel().getTimeZone());
            c.setTime(new Date((long) chartEntity.getDataset().getXValue(chartEntity.getSeriesIndex(), chartEntity.getItem())));
            Calendar cEnd = (Calendar) c.clone();
            cEnd.add(Calendar.DAY_OF_MONTH, 1);

            IMultiSearchResultProvider msrp = ipedChartPanel.getIpedChartsPanel().getResultsProvider();
            IPEDSource is = (IPEDSource) msrp.getIPEDSource();

            RoaringBitmap bs = new RoaringBitmap();
            IpedTimelineDataset ds = (IpedTimelineDataset) chartEntity.getDataset();
            List<IItemId> ids = ds.getItems(chartEntity.getItem(), chartEntity.getSeriesIndex());
            if (ids != null) {
                for (Iterator iterator = ids.iterator(); iterator.hasNext();) {
                    IItemId iItemId = (IItemId) iterator.next();
                    bs.add(is.getLuceneId(iItemId));
                }
            }

            EventPeriodCheckWorker bsCheck = new EventPeriodCheckWorker(ipedChartPanel.getIpedChartsPanel().getDomainAxis(), msrp, bs, true);
            bsCheck.execute();
        }
        if (e.getSource() == checkPeriodItens) {
            long timestamp = (long) chartEntity.getDataset().getXValue(chartEntity.getSeriesIndex(), chartEntity.getItem());
            Date start = new Date(timestamp);
            Date end = new Date(timestamp + (long) ipedChartPanel.getIpedChartsPanel().getTimePeriodLength() - 1l);
            ipedChartPanel.getIpedChartsPanel().checkItemsOnInterval(start, end, false);
        }
    }

    public void setChartEntityList(List<XYItemEntity> entityList) {
        this.entityList = entityList;
    }

}
