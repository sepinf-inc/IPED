package iped.app.timelinegraph;

import java.awt.Graphics2D;
import java.awt.geom.RectangularShape;

import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.ui.RectangleEdge;

public class IpedXYBarPainter extends StandardXYBarPainter {

    XYBarRenderer parentRenderer;

    public IpedXYBarPainter(XYBarRenderer parentRenderer) {
        this.parentRenderer = parentRenderer;
    }

    @Override
    public void paintBar(Graphics2D g2, XYBarRenderer renderer, int row, int column, RectangularShape bar, RectangleEdge base) {
        // TODO Auto-generated method stub
        super.paintBar(g2, this.parentRenderer, row, column, bar, base);
    }

    @Override
    public void paintBarShadow(Graphics2D g2, XYBarRenderer renderer, int row, int column, RectangularShape bar, RectangleEdge base, boolean pegShadow) {
        // TODO Auto-generated method stub
        super.paintBarShadow(g2, this.parentRenderer, row, column, bar, base, pegShadow);
    }

}
