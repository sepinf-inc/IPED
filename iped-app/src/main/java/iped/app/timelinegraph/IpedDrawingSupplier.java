package iped.app.timelinegraph;

import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;

import org.jfree.chart.plot.DefaultDrawingSupplier;
import org.jfree.chart.plot.DrawingSupplier;

public class IpedDrawingSupplier extends DefaultDrawingSupplier {

    static DrawingSupplier singleton = new DefaultDrawingSupplier();

    @Override
    public Paint getNextPaint() {
        return singleton.getNextPaint();
    }

    @Override
    public Paint getNextFillPaint() {
        return singleton.getNextFillPaint();
    }

    @Override
    public Paint getNextOutlinePaint() {
        // TODO Auto-generated method stub
        return singleton.getNextOutlinePaint();
    }

    @Override
    public Stroke getNextStroke() {
        // TODO Auto-generated method stub
        return singleton.getNextStroke();
    }

    @Override
    public Stroke getNextOutlineStroke() {
        // TODO Auto-generated method stub
        return singleton.getNextOutlineStroke();
    }

    @Override
    public Shape getNextShape() {
        // TODO Auto-generated method stub
        return singleton.getNextShape();
    }

}
