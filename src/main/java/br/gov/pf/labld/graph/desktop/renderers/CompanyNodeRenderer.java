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
public class CompanyNodeRenderer extends ShapeNodeRenderer {

  @Override
  public Shape drawShape(Node arg0, RenderContext arg1) {
    GeneralPath shape = new GeneralPath();
    shape.moveTo(4.0, 0.0);
    shape.lineTo(0.0, 3.0);
    shape.lineTo(1.0, 3.0);
    shape.lineTo(1.0, 7.0);
    shape.lineTo(3.0, 7.0);
    shape.lineTo(3.0, 5.0);
    shape.lineTo(5.0, 5.0);
    shape.lineTo(5.0, 7.0);
    shape.lineTo(7.0, 7.0);
    shape.lineTo(7.0, 2.9699998);
    shape.lineTo(8.0, 2.9999998);
    shape.lineTo(4.0, -2.3841858E-7);
    shape.closePath();
    return shape;
  }

}
