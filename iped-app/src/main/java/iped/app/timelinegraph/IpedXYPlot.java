package iped.app.timelinegraph;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Date;

import javax.swing.UIManager;

import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.SeriesRenderingOrder;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRendererState;
import org.jfree.chart.util.Args;
import org.jfree.data.DomainOrder;
import org.jfree.data.general.DatasetUtils;
import org.jfree.data.xy.AbstractIntervalXYDataset;
import org.jfree.data.xy.XYDataset;

import iped.app.ui.themes.Theme;

public class IpedXYPlot extends XYPlot {

    IpedChartPanel ipedChartPanel;

    private Stroke filterLimitStroke;
    private AffineTransform affineTransform;
    Color filterIntervalFillPaint;

    public IpedXYPlot(IpedChartPanel ipedChartPanel, AbstractIntervalXYDataset timeTableCumulativeXYDataset, DateAxis domainAxis, NumberAxis rangeAxis, XYItemRenderer renderer) {
        super(timeTableCumulativeXYDataset, domainAxis, rangeAxis, renderer);
        this.ipedChartPanel = ipedChartPanel;

        this.filterIntervalFillPaint = new Color(0, 0, 255, 43);
        this.filterLimitStroke = new BasicStroke(3, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 1);
        this.affineTransform = new AffineTransform();
        this.affineTransform.rotate(Math.toRadians(90), 0, 0);
    }

    @Override
    public void drawOutline(Graphics2D g2, Rectangle2D area) {
        super.drawOutline(g2, area);
    }

    @Override
    public void drawBackground(Graphics2D g2, Rectangle2D area) {
        // TODO Auto-generated method stub
        super.drawBackground(g2, area);

        drawDefinedFiltersRectangles(g2, area, false);
    }

    @Override
    protected void drawNoDataMessage(Graphics2D g2, Rectangle2D area) {
        super.drawNoDataMessage(g2, area);
    }

    /**
     * Draws defined filters rectangles (if present). The drawing is performed in
     * XOR mode, therefore when this method is called twice in a row, the second
     * call will completely restore the state of the canvas.
     *
     * @param g2
     *            the graphics device.
     * @param xor
     *            use XOR for drawing?
     */
    private void drawDefinedFilterRectangles(Date[] dates, Graphics2D g2, Rectangle2D area, boolean xor) {
        if (xor) {
            // Set XOR mode to draw the zoom rectangle
            g2.setXORMode(Color.GRAY);
        }
        g2.setPaint(ipedChartPanel.filterIntervalFillPaint);

        int xstart = (int) ipedChartPanel.ipedChartsPanel.domainAxis.dateToJava2D(dates[0], area, ipedChartPanel.ipedChartsPanel.combinedPlot.getDomainAxisEdge());
        int xend = (int) ipedChartPanel.ipedChartsPanel.domainAxis.dateToJava2D(dates[1], area, ipedChartPanel.ipedChartsPanel.combinedPlot.getDomainAxisEdge());

        double minX = area.getMinX();
        double maxX = area.getMaxX();
        double maxY = area.getMaxY();

        int x = (int) Math.max(minX, xstart);
        int y = (int) area.getMinY();
        int w = (int) Math.min(xend - x, maxX - x);
        double h = area.getHeight();

        Rectangle2D rectangle2d = new Rectangle2D.Double(x, y, w, h);

        g2.fill(rectangle2d);

        drawDefinedFiltersDates(dates, g2, area, xor);

        if (xor) {
            // Reset to the default 'overwrite' mode
            g2.setPaintMode();
        }
    }

    /**
     * Draws defined filters rectangles (if present). The drawing is performed in
     * XOR mode, therefore when this method is called twice in a row, the second
     * call will completely restore the state of the canvas.
     *
     * @param g2
     *            the graphics device.
     * @param xor
     *            use XOR for drawing?
     */
    public void drawDefinedFiltersRectangles(Graphics2D g2, Rectangle2D area, boolean xor) {
        for (Date[] dates : ipedChartPanel.definedFilters) {
            drawDefinedFilterRectangles(dates, g2, area, xor);
        }
    }

    private void drawDefinedFiltersDates(Date[] dates, Graphics2D g2, Rectangle2D area, boolean xor) {
        if (xor) {
            // Set XOR mode to draw the zoom rectangle
            g2.setXORMode(Color.GRAY);
        }

        String strStartDate = ipedChartPanel.getIpedChartsPanel().getDomainAxis().ISO8601DateFormat(dates[0]);// iped.utils.DateUtil.dateToString(dates[0]);
        String strEndDate = ipedChartPanel.getIpedChartsPanel().getDomainAxis().ISO8601DateFormat(dates[1]);// iped.utils.DateUtil.dateToString(dates[1]);

        int xstart = (int) ipedChartPanel.ipedChartsPanel.domainAxis.dateToJava2D(dates[0], area, ipedChartPanel.ipedChartsPanel.combinedPlot.getDomainAxisEdge());
        int xend = (int) ipedChartPanel.ipedChartsPanel.domainAxis.dateToJava2D(dates[1], area, ipedChartPanel.ipedChartsPanel.combinedPlot.getDomainAxisEdge());

        double minX = area.getMinX();
        double maxX = area.getMaxX();
        double maxY = area.getMaxY();

        int x = (int) Math.max(minX, xstart);
        int y = (int) area.getMinY();
        int w = (int) Math.min(xend - x, maxX - x);
        double h = area.getHeight();

        Rectangle2D rectangle2d = new Rectangle2D.Double(x, y, w, h);

        g2.setPaint(Color.BLACK);
        g2.setStroke(this.filterLimitStroke);
        g2.setFont(g2.getFont().deriveFont(affineTransform));// rotate text 90
        g2.drawString(strStartDate, (int) rectangle2d.getMinX() + 2, (int) rectangle2d.getMinY() + g2.getFontMetrics().getHeight() + 2);
        g2.drawString(strEndDate, (int) rectangle2d.getMaxX() + 2, (int) rectangle2d.getMinY() + g2.getFontMetrics().getHeight() + 2);

        if (xor) {
            // Reset to the default 'overwrite' mode
            g2.setPaintMode();
        }
    }

    /**
     * Draws defined filters dates text (if present). The drawing is performed in
     * XOR mode, therefore when this method is called twice in a row, the second
     * call will completely restore the state of the canvas.
     *
     * @param g2
     *            the graphics device.
     * @param xor
     *            use XOR for drawing?
     */
    private void drawDefinedFiltersDates(Graphics2D g2, Rectangle2D area, boolean xor) {
        for (Date[] dates : ipedChartPanel.definedFilters) {
            drawDefinedFiltersDates(dates, g2, area, xor);
        }

    }

    /**
     * Multiplies the range on the range axis/axes by the specified factor.
     *
     * @param factor
     *            the zoom factor.
     * @param info
     *            the plot rendering info.
     * @param source
     *            the source point.
     * @param useAnchor
     *            a flag that controls whether or not the source point is used for
     *            the zoom anchor.
     *
     * @see #zoomDomainAxes(double, PlotRenderingInfo, Point2D, boolean)
     */
    @Override
    public void zoomRangeAxes(double factor, PlotRenderingInfo info, Point2D source, boolean useAnchor) {

        // perform the zoom on each range axis
        for (int i = 0; i < this.getRangeAxisCount(); i++) {
            ValueAxis yAxis = this.getRangeAxis(i);
            if (yAxis == null) {
                continue;
            }
            if (yAxis instanceof IpedHourAxis) {
                // does not zoom on range axis of IpedHourAxis
                continue;
            }
            if (useAnchor) {
                // get the relevant source coordinate given the plot orientation
                double sourceY = source.getY();
                if (this.getOrientation() == PlotOrientation.HORIZONTAL) {
                    sourceY = source.getX();
                }
                double anchorY = yAxis.java2DToValue(sourceY, info.getDataArea(), getRangeAxisEdge());
                yAxis.resizeRange2(factor, 0);
            } else {
                yAxis.resizeRange(factor);
            }
        }
    }

    /**
     * Multiplies the range on the range axis/axes by the specified factor.
     *
     * @param factor
     *            the zoom factor.
     * @param info
     *            the plot rendering info.
     * @param source
     *            the source point.
     *
     * @see #zoomDomainAxes(double, PlotRenderingInfo, Point2D, boolean)
     */
    @Override
    public void zoomRangeAxes(double factor, PlotRenderingInfo info, Point2D source) {
        // delegate to other method
        zoomRangeAxes(factor, info, source, false);
    }

    /**
     * Zooms in on the range axes.
     *
     * @param lowerPercent
     *            the lower bound.
     * @param upperPercent
     *            the upper bound.
     * @param info
     *            the plot rendering info.
     * @param source
     *            the source point.
     *
     * @see #zoomDomainAxes(double, double, PlotRenderingInfo, Point2D)
     */
    @Override
    public void zoomRangeAxes(double lowerPercent, double upperPercent, PlotRenderingInfo info, Point2D source) {
        for (int i = 0; i < this.getRangeAxisCount(); i++) {
            ValueAxis yAxis = this.getRangeAxis(i);
            if (yAxis != null) {
                yAxis.zoomRange(0, upperPercent);
            }
        }
    }

    /**
     * Draws a representation of the data within the dataArea region, using the
     * current renderer.
     * <P>
     * The {@code info} and {@code crosshairState} arguments may be {@code null}.
     *
     * @param g2
     *            the graphics device.
     * @param dataArea
     *            the region in which the data is to be drawn.
     * @param index
     *            the dataset index.
     * @param info
     *            an optional object for collection dimension information.
     * @param crosshairState
     *            collects crosshair information ({@code null} permitted).
     *
     * @return A flag that indicates whether any data was actually rendered.
     */
    public boolean render(Graphics2D g2, Rectangle2D dataArea, int index, PlotRenderingInfo info, CrosshairState crosshairState) {

        boolean foundData = false;
        XYDataset dataset = getDataset(index);

        IpedStackedXYBarRenderer renderer = (IpedStackedXYBarRenderer) getRenderer(index);

        if (!DatasetUtils.isEmptyOrNull(dataset)) {
            foundData = true;
            ValueAxis xAxis = getDomainAxisForDataset(index);
            ValueAxis yAxis = getRangeAxisForDataset(index);
            if (xAxis == null || yAxis == null) {
                return foundData; // can't render anything without axes
            }
            if (renderer == null) {
                renderer = (IpedStackedXYBarRenderer) getRenderer();
                if (renderer == null) { // no default renderer available
                    return foundData;
                }
            }

            XYItemRendererState state = renderer.initialise(g2, dataArea, this, dataset, info);
            int passCount = renderer.getPassCount();

            // reset cached min/max rendered value in X axis
            renderer.minEndX = null;
            renderer.maxStartX = null;

            for (int pass = 0; pass < passCount; pass++) {
                int seriesCount = dataset.getSeriesCount();
                for (int item = 0; item < dataset.getItemCount(0); item++) {
                    for (int series = 0; series < seriesCount; series++) {
                        if (item < dataset.getItemCount(series)) {
                            renderer.drawItem(g2, state, dataArea, info, this, xAxis, yAxis, dataset, series, item, crosshairState, pass);
                        }
                    }
                }
            }
        }
        return foundData;
    }

    /**
     * Finds a range of item indices that is guaranteed to contain all the x-values
     * from x0 to x1 (inclusive).
     *
     * @param dataset
     *            the dataset ({@code null} not permitted).
     * @param series
     *            the series index.
     * @param xLow
     *            the lower bound of the x-value range.
     * @param xHigh
     *            the upper bound of the x-value range.
     *
     * @return The indices of the boundary items.
     */
    public static int[] findLiveItems(XYDataset dataset, int series, double xLow, double xHigh) {
        // here we could probably be a little faster by searching for both
        // indices simultaneously, but I'll look at that later if it seems
        // like it matters...
        int i0 = findLiveItemsLowerBound(dataset, series, xLow, xHigh);
        int i1 = findLiveItemsUpperBound(dataset, series, xLow, xHigh);
        if (i0 > i1) {
            i0 = i1;
        }
        return new int[] { i0, i1 };
    }

    /**
     * Finds the lower index of the range of live items in the specified data
     * series.
     *
     * @param dataset
     *            the dataset ({@code null} not permitted).
     * @param series
     *            the series index.
     * @param xLow
     *            the lowest x-value in the live range.
     * @param xHigh
     *            the highest x-value in the live range.
     *
     * @return The index of the required item.
     *
     * @see #findLiveItemsUpperBound(XYDataset, int, double, double)
     */
    public static int findLiveItemsLowerBound(XYDataset dataset, int series, double xLow, double xHigh) {
        Args.nullNotPermitted(dataset, "dataset");
        if (xLow >= xHigh) {
            throw new IllegalArgumentException("Requires xLow < xHigh.");
        }
        int itemCount = dataset.getItemCount(series);
        if (itemCount <= 1) {
            return 0;
        }
        if (dataset.getDomainOrder() == DomainOrder.ASCENDING) {
            // for data in ascending order by x-value, we are (broadly) looking
            // for the index of the highest x-value that is less than xLow
            int low = 0;
            int high = itemCount - 1;
            double lowValue = dataset.getXValue(series, low);
            if (lowValue >= xLow) {
                // special case where the lowest x-value is >= xLow
                return low;
            }
            double highValue = dataset.getXValue(series, high);
            if (highValue < xLow) {
                // special case where the highest x-value is < xLow
                return high;
            }
            while (high - low > 1) {
                int mid = (low + high) / 2;
                double midV = dataset.getXValue(series, mid);
                if (midV >= xLow) {
                    high = mid;
                } else {
                    low = mid;
                }
            }
            return high;
        } else if (dataset.getDomainOrder() == DomainOrder.DESCENDING) {
            // when the x-values are sorted in descending order, the lower
            // bound is found by calculating relative to the xHigh value
            int low = 0;
            int high = itemCount - 1;
            double lowValue = dataset.getXValue(series, low);
            if (lowValue <= xHigh) {
                return low;
            }
            double highValue = dataset.getXValue(series, high);
            if (highValue > xHigh) {
                return high;
            }
            while (high - low > 1) {
                int mid = (low + high) / 2;
                double midV = dataset.getXValue(series, mid);
                if (midV > xHigh) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            return high;
        } else {
            // we don't know anything about the ordering of the x-values,
            // but we can still skip any initial values that fall outside the
            // range...
            int index = 0;
            // skip any items that don't need including...
            double x = dataset.getXValue(series, index);
            while (index < itemCount && x < xLow) {
                index++;
                if (index < itemCount) {
                    x = dataset.getXValue(series, index);
                }
            }
            return Math.min(Math.max(0, index), itemCount - 1);
        }
    }

    /**
     * Finds the upper index of the range of live items in the specified data
     * series.
     *
     * @param dataset
     *            the dataset ({@code null} not permitted).
     * @param series
     *            the series index.
     * @param xLow
     *            the lowest x-value in the live range.
     * @param xHigh
     *            the highest x-value in the live range.
     *
     * @return The index of the required item.
     *
     * @see #findLiveItemsLowerBound(XYDataset, int, double, double)
     */
    public static int findLiveItemsUpperBound(XYDataset dataset, int series, double xLow, double xHigh) {
        Args.nullNotPermitted(dataset, "dataset");
        if (xLow >= xHigh) {
            throw new IllegalArgumentException("Requires xLow < xHigh.");
        }
        int itemCount = dataset.getItemCount(series);
        if (itemCount <= 1) {
            return 0;
        }
        if (dataset.getDomainOrder() == DomainOrder.ASCENDING) {
            int low = 0;
            int high = itemCount - 1;
            double lowValue = dataset.getXValue(series, low);
            if (lowValue > xHigh) {
                return low;
            }
            double highValue = dataset.getXValue(series, high);
            if (highValue <= xHigh) {
                return high;
            }
            int mid = (low + high) / 2;
            while (high - low > 1) {
                double midV = dataset.getXValue(series, mid);
                if (midV <= xHigh) {
                    low = mid;
                } else {
                    high = mid;
                }
                mid = (low + high) / 2;
            }
            return mid;
        } else if (dataset.getDomainOrder() == DomainOrder.DESCENDING) {
            // when the x-values are descending, the upper bound is found by
            // comparing against xLow
            int low = 0;
            int high = itemCount - 1;
            int mid = (low + high) / 2;
            double lowValue = dataset.getXValue(series, low);
            if (lowValue < xLow) {
                return low;
            }
            double highValue = dataset.getXValue(series, high);
            if (highValue >= xLow) {
                return high;
            }
            while (high - low > 1) {
                double midV = dataset.getXValue(series, mid);
                if (midV >= xLow) {
                    low = mid;
                } else {
                    high = mid;
                }
                mid = (low + high) / 2;
            }
            return mid;
        } else {
            // we don't know anything about the ordering of the x-values,
            // but we can still skip any trailing values that fall outside the
            // range...
            int index = itemCount - 1;
            // skip any items that don't need including...
            double x = dataset.getXValue(series, index);
            while (index >= 0 && x > xHigh) {
                index--;
                if (index >= 0) {
                    x = dataset.getXValue(series, index);
                }
            }
            return Math.max(index, 0);
        }
    }

    @Override
    public SeriesRenderingOrder getSeriesRenderingOrder() {
        // TODO Auto-generated method stub
        return SeriesRenderingOrder.FORWARD;
    }

    public void changeTheme(Theme newTheme) {
        Color fgColor = UIManager.getLookAndFeelDefaults().getColor("Viewer.foreground");
        if (fgColor == null) {
            fgColor = Color.BLACK;
        }
        Color bgColor = UIManager.getLookAndFeelDefaults().getColor("Viewer.background");
        if (bgColor == null) {
            bgColor = Color.WHITE;
        }

        this.setBackgroundPaint(bgColor);
        this.getDomainAxis().setTickLabelPaint(fgColor);
        this.getDomainAxis().setLabelPaint(fgColor);
        this.getRangeAxis().setTickLabelPaint(fgColor);
        this.getRangeAxis().setLabelPaint(fgColor);
        ipedChartPanel.getIpedChartsPanel().repaint();
    }
}
