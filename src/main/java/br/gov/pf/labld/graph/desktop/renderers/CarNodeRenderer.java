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
 * Icon based on <a href="https://www.flaticon.com/free-icon/car-front_60738#term=car&page=1&position=32">Material Design</a>.
 */
public class CarNodeRenderer extends ShapeNodeRenderer {

  @Override
  public Shape drawShape(Node node, RenderContext renderContext) {
    GeneralPath shape = new GeneralPath();
    shape.moveTo(405.45, 51.0);
    shape.curveTo(400.349, 35.7, 385.05002, 25.5, 369.75, 25.5);
    shape.lineTo(89.25, 25.5);
    shape.curveTo(71.4, 25.5, 58.65, 35.7, 53.55, 51.0);
    shape.lineTo(0.0, 204.0);
    shape.lineTo(0.0, 408.0);
    shape.curveTo(0.0, 423.3, 10.2, 433.5, 25.5, 433.5);
    shape.lineTo(51.0, 433.5);
    shape.curveTo(66.3, 433.5, 76.5, 423.3, 76.5, 408.0);
    shape.lineTo(76.5, 382.5);
    shape.lineTo(382.5, 382.5);
    shape.lineTo(382.5, 408.0);
    shape.curveTo(382.5, 423.3, 392.7, 433.5, 408.0, 433.5);
    shape.lineTo(433.5, 433.5);
    shape.curveTo(448.8, 433.5, 459.0, 423.3, 459.0, 408.0);
    shape.lineTo(459.0, 204.0);
    shape.lineTo(405.45, 51.0);
    shape.closePath();
    shape.moveTo(89.25, 306.0);
    shape.curveTo(68.85, 306.0, 51.0, 288.15, 51.0, 267.75);
    shape.curveTo(51.0, 247.35, 68.85, 229.5, 89.25, 229.5);
    shape.curveTo(109.65, 229.5, 127.5, 247.35, 127.5, 267.75);
    shape.curveTo(127.5, 288.15, 109.65, 306.0, 89.25, 306.0);
    shape.closePath();
    shape.moveTo(369.75, 306.0);
    shape.curveTo(349.35, 306.0, 331.5, 288.15, 331.5, 267.75);
    shape.curveTo(331.5, 247.35, 349.35, 229.5, 369.75, 229.5);
    shape.curveTo(390.15, 229.5, 408.0, 247.35, 408.0, 267.75);
    shape.curveTo(408.0, 288.15, 390.15, 306.0, 369.75, 306.0);
    shape.closePath();
    shape.moveTo(51.0, 178.5);
    shape.lineTo(89.25, 63.75);
    shape.lineTo(369.75, 63.75);
    shape.lineTo(408.0, 178.5);
    shape.lineTo(51.0, 178.5);
    shape.closePath();
    return shape;
  }

}
