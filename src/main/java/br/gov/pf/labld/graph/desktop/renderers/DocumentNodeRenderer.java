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
public class DocumentNodeRenderer extends ShapeNodeRenderer {

  @Override
  public Shape drawShape(Node node, RenderContext context) {
    GeneralPath shape = new GeneralPath();
    shape.moveTo(0.0, 0.0);
    shape.lineTo(0.0, 8.0);
    shape.lineTo(7.0, 8.0);
    shape.lineTo(7.0, 4.0);
    shape.lineTo(3.0, 4.0);
    shape.lineTo(3.0, 0.0);
    shape.lineTo(0.0, 0.0);
    shape.closePath();
    shape.moveTo(4.0, 0.0);
    shape.lineTo(4.0, 3.0);
    shape.lineTo(7.0, 3.0);
    shape.lineTo(4.0, 0.0);
    shape.closePath();
    shape.moveTo(1.0, 2.0);
    shape.lineTo(2.0, 2.0);
    shape.lineTo(2.0, 3.0);
    shape.lineTo(1.0, 3.0);
    shape.lineTo(1.0, 2.0);
    shape.closePath();
    shape.moveTo(1.0, 4.0);
    shape.lineTo(2.0, 4.0);
    shape.lineTo(2.0, 5.0);
    shape.lineTo(1.0, 5.0);
    shape.lineTo(1.0, 4.0);
    shape.closePath();
    shape.moveTo(1.0, 6.0);
    shape.lineTo(5.0, 6.0);
    shape.lineTo(5.0, 7.0);
    shape.lineTo(1.0, 7.0);
    shape.lineTo(1.0, 6.0);
    shape.closePath();
    return shape;
  }

}
