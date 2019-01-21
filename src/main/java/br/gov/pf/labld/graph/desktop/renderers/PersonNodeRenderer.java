package br.gov.pf.labld.graph.desktop.renderers;

import java.awt.Shape;
import java.awt.geom.GeneralPath;

import org.kharon.Node;
import org.kharon.renderers.RenderContext;
import org.kharon.renderers.ShapeNodeRenderer;

/**
 * This class has been generated using
 * <a href="http://ebourg.github.io/flamingo-svg-transcoder/">Flamingo SVG
 * transcoder</a>.
 * 
 * Icon based on <a href="https://useiconic.com/open">Open Iconic</a>.
 */
public class PersonNodeRenderer extends ShapeNodeRenderer {

  @Override
  public Shape drawShape(Node node, RenderContext context) {
    GeneralPath shape = new GeneralPath();
    shape.moveTo(4.0, 0.0);
    shape.curveTo(2.9, 0.0, 2.0, 1.12, 2.0, 2.5);
    shape.curveTo(2.0, 3.88, 2.9, 5.0, 4.0, 5.0);
    shape.curveTo(5.1, 5.0, 6.0, 3.88, 6.0, 2.5);
    shape.curveTo(6.0, 1.1199999, 5.1, 0.0, 4.0, 0.0);
    shape.closePath();
    shape.moveTo(1.9100001, 5.0);
    shape.curveTo(0.85000014, 5.05, 1.1920929E-7, 5.92, 1.1920929E-7, 7.0);
    shape.lineTo(1.1920929E-7, 8.0);
    shape.lineTo(8.0, 8.0);
    shape.lineTo(8.0, 7.0);
    shape.curveTo(8.0, 5.92, 7.16, 5.05, 6.09, 5.0);
    shape.curveTo(5.55, 5.61, 4.8100004, 6.0, 4.0, 6.0);
    shape.curveTo(3.19, 6.0, 2.45, 5.61, 1.9100001, 5.0);
    shape.closePath();
    return shape;
  }

}
