package br.gov.pf.labld.graph.desktop;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.kharon.Node;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.NotFoundException;

public class GraphModel {

  private double angleInRadians = Math.PI / 8d;
  private double distanceMultiplier = 3d;
  private double distanceStep = 0.2d;
  private double angleStep = Math.PI / 32d;

  private Set<String> knownTypes = new HashSet<>(
      Arrays.asList("EVIDENCIA", "PESSOA_FISICA", "PESSOA_JURIDICA", "PLACA", "EMAIL", "TELEFONE"));

  public Node convert(org.neo4j.graphdb.Node neo4jNode) {
    String nodeId = Long.toString(neo4jNode.getId());
    Node node = new Node(nodeId);

    String[] fieldNames = getDefaultFieldNames(neo4jNode);
    String type = getType(neo4jNode);
    node.setType(type);
    node.setLabel(getLabel(neo4jNode, fieldNames));

    return node;
  }

  public String getType(org.neo4j.graphdb.Node neo4jNode) {
    Iterator<Label> iterator = neo4jNode.getLabels().iterator();
    String type = null;
    while (iterator.hasNext()) {
      type = iterator.next().name();
      if (knownTypes.contains(type)) {
        return type;
      }
    }
    return type;
  }

  public String[] getDefaultFieldNames(org.neo4j.graphdb.Node neo4jNode) {
    // TODO Move to configuration file
    String type = getType(neo4jNode);
    if (type.equals("EVIDENCIA")) {
      return new String[] { "name", "path", "hash" };
    } else if (type.equals("PESSOA_FISICA")) {
      return new String[] { "cpf", "titulo_eleitor", "cnh", "pispasep" };
    } else if (type.equals("PESSOA_JURIDICA")) {
      return new String[] { "cnpj" };
    } else if (type.equals("PLACA")) {
      return new String[] { "placa" };
    } else if (type.equals("EMAIL")) {
      return new String[] { "email" };
    } else if (type.equals("TELEFONE")) {
      return new String[] { "telefone" };
//    } else if (type.equals("IPEDCASE")) {
//      return new String[] { "name" };
    } else {
      Iterable<String> keys = neo4jNode.getPropertyKeys();
      List<String> props = new ArrayList<>();

      for (String key : keys) {
        props.add(key);
      }

      Collections.sort(props);
      return props.toArray(new String[props.size()]);
    }
  }

  public String getLabel(org.neo4j.graphdb.Node neo4jNode, String... labels) {
    for (String label : labels) {
      try {
        return neo4jNode.getProperty(label).toString();
      } catch (NotFoundException e) {
        // Nothing to do.
      }
    }
    return getLabel(neo4jNode);
  }

  public Point calculateRelativePosition(Node source, Node newNode, int nodeDegree) {
    double x = source.getX();
    double y = source.getY();

    double angle = nodeDegree * angleInRadians;
    double revolution = Math.floor(angle / (2 * Math.PI));

    double distance = (source.getSize() + newNode.getSize()) * distanceMultiplier;
    distance = distance * (1 + distanceStep * revolution);

    angle = angle + angleStep * revolution;

    double newX = x + distance * Math.cos(angle);
    double newY = y - distance * Math.sin(angle);

    return new Point((int) newX, (int) newY);
  }

  public Point calculateRelativePosition(Node source, Node newNode) {
    int nodeDegree = source.getDegree();
    return calculateRelativePosition(source, newNode, nodeDegree);
  }

  public Point calculateRelativePosition(Node source, Node target, Node newNode, int hop, int length, int degree) {
    double x = source.getX();
    double y = source.getY();

    double x2 = target.getX();
    double y2 = target.getY();

    double newNodeSize = newNode.getSize();

    Point2D.Double point = new Point2D.Double(x, y);
    Point2D.Double point2 = new Point2D.Double(x2, y2);

    double distance = point.distance(point2);
    double distanceInc = hop * distance / length;

    double deltaX = Math.max(point.x, point2.x) - Math.min(point.x, point2.x);

    double incInRad = Math.asin(deltaX / distance);

    double inc = point.y - point2.y;

    double newX = Math.min(point.x, point2.x) + distanceInc * Math.sin(incInRad);
    double newY = Math.min(point.y, point2.y) + distanceInc * Math.cos(incInRad);

    double mod = degree % 2;
    double upOrDown = mod == 0 ? 1 : -1;
    double multiplier = 1 * mod + ((degree - mod) / 2);
    double step = newNodeSize * multiplier * (1 + distanceStep);

    double dx = step * Math.cos(incInRad);
    double dy = step * Math.sin(incInRad);

    if (inc > 0) {
      newX += -1 * upOrDown * dx;
      newY += upOrDown * dy;
    } else {
      newX += upOrDown * dx;
      newY += upOrDown * dy;
    }

    return new Point((int) newX, (int) newY);
  }

  public Point calculateRelativePosition(Node source, Node target, Node newNode) {
    int degree = Math.max(source.getDegree(), target.getDegree());
    return calculateRelativePosition(source, target, newNode, 1, 2, degree);
  }

}
