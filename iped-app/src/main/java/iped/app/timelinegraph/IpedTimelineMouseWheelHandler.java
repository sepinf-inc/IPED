package iped.app.timelinegraph;

import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.io.Serializable;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.Zoomable;

public class IpedTimelineMouseWheelHandler implements MouseWheelListener, Serializable {

    /** The chart panel. */
    private IpedChartPanel chartPanel;
    long lastWhen = -1;

    /** The zoom factor. */
    double zoomFactor;
    private int zfMultiplier;

    /**
     * Creates a new instance for the specified chart panel.
     *
     * @param chartPanel
     *            the chart panel ({@code null} not permitted).
     */
    public IpedTimelineMouseWheelHandler(IpedChartPanel chartPanel) {
        this.chartPanel = chartPanel;
        this.zoomFactor = 0.10;
        this.chartPanel.addMouseWheelListener(this);
    }

    /**
     * Returns the current zoom factor. The default value is 0.10 (ten percent).
     *
     * @return The zoom factor.
     *
     * @see #setZoomFactor(double)
     */
    public double getZoomFactor() {
        return this.zoomFactor;
    }

    /**
     * Sets the zoom factor.
     *
     * @param zoomFactor
     *            the zoom factor.
     *
     * @see #getZoomFactor()
     */
    public void setZoomFactor(double zoomFactor) {
        this.zoomFactor = zoomFactor;
    }

    /**
     * Handles a mouse wheel event from the underlying chart panel.
     *
     * @param e
     *            the event.
     */
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (lastWhen != e.getWhen()) {// avoid calling the event more then once
            JFreeChart chart = this.chartPanel.getChart();
            if (chart == null) {
                return;
            }
            Plot plot = chart.getPlot();
            if (plot instanceof Zoomable) {
                Zoomable zoomable = (Zoomable) plot;
                handleZoomable(zoomable, e);
            } else if (plot instanceof PiePlot) {
                PiePlot pp = (PiePlot) plot;
                pp.handleMouseWheelRotation(e.getWheelRotation());
            }
        }
        lastWhen = e.getWhen();
    }

    /**
     * Handle the case where a plot implements the {@link Zoomable} interface.
     *
     * @param zoomable
     *            the zoomable plot.
     * @param e
     *            the mouse wheel event.
     */
    private void handleZoomable(Zoomable zoomable, MouseWheelEvent e) {
        // don't zoom unless the mouse pointer is in the plot's data area
        ChartRenderingInfo info = this.chartPanel.getChartRenderingInfo();
        PlotRenderingInfo pinfo = info.getPlotInfo();
        Point2D p = this.chartPanel.translateScreenToJava2D(e.getPoint());
        if (!pinfo.getDataArea().contains(p)) {
            return;
        }

        Plot plot = (Plot) zoomable;
        // do not notify while zooming each axis
        boolean notifyState = plot.isNotify();
        plot.setNotify(false);
        int clicks = e.getWheelRotation();
        double zf = 1.0 + this.zoomFactor;
        if (clicks < 0) {
            zf = 1.0 / zf;
        }
        int modf = e.getModifiersEx();
        boolean zoomDomain = ((modf & MouseWheelEvent.SHIFT_DOWN_MASK) == MouseWheelEvent.SHIFT_DOWN_MASK);
        boolean zoomRange = ((modf & MouseWheelEvent.CTRL_DOWN_MASK) == MouseWheelEvent.CTRL_DOWN_MASK);
        boolean zoomAll = (!zoomDomain && !zoomRange);

        if (lastWhen != e.getWhen() && e.getWhen() - lastWhen < 1000) {
            zfMultiplier++;
            if (zfMultiplier > 4)
                zfMultiplier = 4;
            if (zf > 1) {
                zf = 1 + (zf - 1) * zfMultiplier;
            } else {
                zf = 1 - (1 - zf) * zfMultiplier;
            }
        } else {
            zfMultiplier = 0;
        }

        if (chartPanel.isDomainZoomable() && (zoomDomain || zoomAll)) {
            zoomable.zoomDomainAxes(zf, pinfo, p, true);
        }
        if (chartPanel.isRangeZoomable() && (zoomRange || zoomAll)) {
            zoomable.zoomRangeAxes(zf, pinfo, p, true);
        }
        plot.setNotify(notifyState); // this generates the change event too
    }

}
