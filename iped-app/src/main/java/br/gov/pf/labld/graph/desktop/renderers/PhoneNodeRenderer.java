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
 * Icon based on <a href="https://www.flaticon.com/free-icon/phone-symbol-of-an-auricular-inside-a-circle_34067#term=phone&page=1&position=14">Simple Collection</a>.
 */
public class PhoneNodeRenderer extends ShapeNodeRenderer {

  @Override
  public Shape drawShape(Node node, RenderContext renderContext) {
    GeneralPath shape = new GeneralPath();
    shape.moveTo(242.607, 0.0);
    shape.curveTo(108.629, 0.0, 0.001, 108.628, 0.001, 242.606);
    shape.curveTo(0.001, 376.582, 108.629, 485.212, 242.60701, 485.212);
    shape.curveTo(376.58502, 485.212, 485.211, 376.581, 485.211, 242.606);
    shape.curveTo(485.212, 108.628, 376.585, 0.0, 242.607, 0.0);
    shape.closePath();
    shape.moveTo(370.719, 353.989);
    shape.lineTo(351.294, 373.41803);
    shape.curveTo(347.82602, 376.88104, 337.67102, 379.04202, 337.345, 379.04202);
    shape.curveTo(275.893, 379.57803, 216.724, 355.44003, 173.25, 311.96204);
    shape.curveTo(129.657, 268.34402, 105.491, 208.96404, 106.14, 147.30504);
    shape.curveTo(106.14, 147.27704, 108.364, 137.41304, 111.829, 133.98103);
    shape.lineTo(131.253, 114.55403);
    shape.curveTo(138.36101, 107.41303, 152.015, 104.186035, 161.58, 107.38603);
    shape.lineTo(165.666, 108.74903);
    shape.curveTo(175.203, 111.94603, 185.216, 122.49103, 187.851, 132.20602);
    shape.lineTo(197.622, 168.06802);
    shape.curveTo(200.25699, 177.81102, 196.70299, 191.67203, 189.597, 198.78003);
    shape.lineTo(176.627, 211.75203);
    shape.curveTo(189.361, 258.89404, 226.35, 295.89, 273.5, 308.65503);
    shape.lineTo(286.465, 295.68002);
    shape.curveTo(293.606, 288.53903, 307.462, 284.98804, 317.184, 287.61902);
    shape.lineTo(353.041, 297.42502);
    shape.curveTo(362.758, 300.09503, 373.301, 310.045, 376.49698, 319.579);
    shape.lineTo(377.86, 323.724);
    shape.curveTo(381.028, 333.262, 377.826, 346.913, 370.719, 353.989);
    shape.closePath();
    return shape;
  }

}
