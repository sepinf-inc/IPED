package iped.app.timelinegraph;

import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Rectangle2D;

import org.jfree.chart.axis.AxisState;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.Range;

public class IpedNumberAxis extends NumberAxis implements MouseResponsiveChartEntity {

    private Paint mouseOverPaint;

    public IpedNumberAxis(String marcador) {
        super(marcador);
        setAutoRange(false);
        setAutoRangeMinimumSize(1.3);
    }

    @Override
    public AxisState draw(Graphics2D g2, double cursor, Rectangle2D plotArea, Rectangle2D dataArea, RectangleEdge edge, PlotRenderingInfo plotState) {
        if (mouseOverPaint != null) {
            g2.setPaint(mouseOverPaint);
            g2.fillRect(0, (int) dataArea.getMinY(), (int) dataArea.getMinX(), (int) dataArea.getMaxY() - (int) dataArea.getMinY());
        }
        AxisState result = super.draw(g2, cursor, plotArea, dataArea, edge, plotState);
        return result;
    }

    @Override
    public Paint getMouseOverPaint() {
        return mouseOverPaint;
    }

    @Override
    public void setMouseOverPaint(Paint mouseOverPaint) {
        this.mouseOverPaint = mouseOverPaint;

    }

    @Override
    public void configure() {
        super.configure();
    }

    @Override
    public void autoAdjustRange() {
        super.autoAdjustRange();
    }

    @Override
    public void setRange(Range range, boolean turnOffAutoRange, boolean notify) {
        if (range.getLowerBound() < 0) {
            super.setRange(new Range(0, range.getUpperBound()), turnOffAutoRange, notify);
        } else {
            super.setRange(range, turnOffAutoRange, notify);
        }
    }

}
