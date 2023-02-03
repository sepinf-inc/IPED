package iped.app.timelinegraph;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.Title;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;

public class IpedChart extends JFreeChart {
    IpedChartPanel ipedchartPanel;
    IpedLegendTitle legend;

    public IpedChart(Plot plot) {
        super(null, JFreeChart.DEFAULT_TITLE_FONT, plot, false);
        this.ipedchartPanel = ipedchartPanel;
        legend = new IpedLegendTitle(plot);
        legend.setMargin(new RectangleInsets(1.0, 1.0, 1.0, 1.0));
        legend.setBackgroundPaint(Color.WHITE);
        legend.setPosition(RectangleEdge.BOTTOM);
        addSubtitle(legend);
        legend.addChangeListener(this);
    }

    public void setIpedChartPanel(IpedChartPanel ipedchartPanel) {
        this.ipedchartPanel = ipedchartPanel;
        legend.setIpedChartPanel(ipedchartPanel);
    }

    @Override
    protected EntityCollection drawTitle(Title t, Graphics2D g2, Rectangle2D area, boolean entities) {
        return super.drawTitle(t, g2, area, entities);
    }

}
