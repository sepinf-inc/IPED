package br.gov.pf.labld.graph.desktop;

import org.kharon.layout.graphviz.GraphVizAlgorithm;
import org.kharon.layout.graphviz.GraphVizResolver;

import dpf.sp.gpinf.indexer.Configuration;

public class GraphVizIpedResolver implements GraphVizResolver {

  @Override
  public String resolveBinaryPath(GraphVizAlgorithm algo) {
    if (isWindows()) {
      String path = Configuration.getInstance().appRoot + "/tools/graphviz/" + algo.getCmd() + ".exe";
      return path;
    } else {
      return algo.getCmd();
    }
  }

  private boolean isWindows() {
    String os = System.getProperty("os.name");
    if (os == null) {
      throw new IllegalStateException("os.name");
    }
    os = os.toLowerCase();
    return os.startsWith("windows");
  }

}
