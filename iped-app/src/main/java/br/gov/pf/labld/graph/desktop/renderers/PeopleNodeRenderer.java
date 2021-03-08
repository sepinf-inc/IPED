package br.gov.pf.labld.graph.desktop.renderers;

import java.awt.Shape;
import java.awt.geom.GeneralPath;

import org.kharon.Node;
import org.kharon.renderers.RenderContext;
import org.kharon.renderers.ShapeNodeRenderer;

/**
 * This class was generated using
 * <a href="http://ebourg.github.io/flamingo-svg-transcoder/">Flamingo SVG
 * transcoder</a>.
 * 
 * Icon based on <a href="https://useiconic.com/open">Open Iconic</a>.
 */
public class PeopleNodeRenderer extends ShapeNodeRenderer {

    @Override
    public Shape drawShape(Node node, RenderContext context) {
        GeneralPath shape = new GeneralPath();

        shape.moveTo(5.5, 0.0);
        shape.curveTo(4.99, 0.0, 4.55, 0.35, 4.2799997, 0.88);
        shape.curveTo(4.7299995, 1.4200001, 5.0, 2.1599998, 5.0, 3.0100002);
        shape.curveTo(5.0, 3.3000002, 4.97, 3.5600002, 4.91, 3.8200002);
        shape.curveTo(5.1, 3.93, 5.29, 4.01, 5.5, 4.01);
        shape.curveTo(6.33, 4.01, 7.0, 3.1100001, 7.0, 2.0100002);
        shape.curveTo(7.0, 0.9100003, 6.33, 0.010000229, 5.5, 0.010000229);
        shape.closePath();
        shape.moveTo(2.5, 1.0);
        shape.curveTo(1.6700001, 1.0, 1.0, 1.9, 1.0, 3.0);
        shape.curveTo(1.0, 4.1, 1.6700001, 5.0, 2.5, 5.0);
        shape.curveTo(3.33, 5.0, 4.0, 4.1, 4.0, 3.0);
        shape.curveTo(4.0, 1.9000001, 3.33, 1.0, 2.5, 1.0);
        shape.closePath();
        shape.moveTo(7.25, 4.16);
        shape.curveTo(6.82, 4.67, 6.23, 4.98, 5.56, 5.0);
        shape.curveTo(5.83, 5.38, 6.0, 5.84, 6.0, 6.34);
        shape.lineTo(6.0, 7.0);
        shape.lineTo(8.0, 7.0);
        shape.lineTo(8.0, 5.34);
        shape.curveTo(8.0, 4.82, 7.69, 4.37, 7.25, 4.15);
        shape.closePath();
        shape.moveTo(0.75, 5.16);
        shape.curveTo(0.31, 5.3799996, 0.0, 5.83, 0.0, 6.35);
        shape.lineTo(0.0, 8.01);
        shape.lineTo(5.0, 8.01);
        shape.lineTo(5.0, 6.3500004);
        shape.curveTo(5.0, 5.8300004, 4.69, 5.38, 4.25, 5.1600003);
        shape.curveTo(3.8, 5.6900005, 3.19, 6.0000005, 2.5, 6.0000005);
        shape.curveTo(1.81, 6.0000005, 1.2, 5.6800003, 0.75, 5.1600003);
        shape.closePath();

        return shape;
    }

}
