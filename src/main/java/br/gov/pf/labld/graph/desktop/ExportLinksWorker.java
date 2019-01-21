package br.gov.pf.labld.graph.desktop;

import java.awt.Cursor;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Iterator;

import javax.swing.SwingWorker;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;

import br.gov.pf.labld.graph.ExportLinksQuery;
import br.gov.pf.labld.graph.GraphService;
import br.gov.pf.labld.graph.GraphServiceFactoryImpl;
import br.gov.pf.labld.graph.LinkQueryListener;
import dpf.sp.gpinf.indexer.desktop.Messages;

public class ExportLinksWorker extends SwingWorker<Void, Void> implements LinkQueryListener {

  private GraphModel model;
  private ExportLinksDialog dialog;
  private File output;
  private ExportLinksQuery query;

  private Writer out;

  public ExportLinksWorker(GraphModel model, ExportLinksDialog dialog, File output, ExportLinksQuery query) {
    super();
    this.model = model;
    this.dialog = dialog;
    this.output = output;
    this.query = query;
  }

  @Override
  public void linkFound(Node node1, Node node2) {
    try {

      writeNode(node1);
      out.write(",");
      writeNode(node2);

      out.write("\r\n");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void writeNode(Node node) throws IOException {
    String[] fieldNames = model.getDefaultFieldNames(node);

    String type = model.getType(node);
    String field = null;
    String value = null;
    for (String fieldName : fieldNames) {
      try {
        if (value == null || value.isEmpty()) {
          Object property = node.getProperty(fieldName);
          if (property != null) {
            value = property.toString();
            field = fieldName;
          }
        }
      } catch (NotFoundException e) {
        // Nothing to do.
      }
    }
    if (value == null) {
      Iterator<String> keys = node.getPropertyKeys().iterator();
      if (keys.hasNext()) {
        field = keys.next();
        value = node.getProperty(field).toString();
      } else {
        field = "";
        value = "";
      }
    }
    out.write("\"");
    out.write(type);
    out.write("\"");
    out.write(",");
    out.write("\"");
    out.write(field);
    out.write("\"");
    out.write(",");
    out.write("\"");
    out.write(value);
    out.write("\"");

  }

  @Override
  protected Void doInBackground() throws Exception {
    out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), Charset.forName("utf-8")));

    writeHeader();

    GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
    graphService.findLinks(query, this);

    dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    dialog.setEnabled(false);

    return null;
  }

  private void writeHeader() throws IOException {

    out.write(Messages.get("GraphAnalysis.Type"));
    out.write(",");
    out.write(Messages.get("GraphAnalysis.Property"));
    out.write(",");
    out.write(Messages.get("GraphAnalysis.Value"));
    out.write(",");
    out.write(Messages.get("GraphAnalysis.Type"));
    out.write(",");
    out.write(Messages.get("GraphAnalysis.Property"));
    out.write(",");
    out.write(Messages.get("GraphAnalysis.Value"));
    out.write("\r\n");

  }

  @Override
  protected void done() {
    if (out != null) {
      try {
        out.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    dialog.showSucessMessage(output);
  }

}
