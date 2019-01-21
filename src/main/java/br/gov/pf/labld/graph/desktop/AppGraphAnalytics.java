package br.gov.pf.labld.graph.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.kharon.Edge;
import org.kharon.Graph;
import org.kharon.GraphPane;
import org.kharon.Node;
import org.kharon.NodeAdapter;
import org.kharon.StageAdapter;
import org.kharon.StageMode;
import org.kharon.layout.Layout;
import org.kharon.renderers.Renderers;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.gov.pf.labld.graph.EdgeQueryListener;
import br.gov.pf.labld.graph.GraphService;
import br.gov.pf.labld.graph.GraphServiceFactoryImpl;
import br.gov.pf.labld.graph.GraphTask;
import br.gov.pf.labld.graph.NodeEdgeQueryListener;
import br.gov.pf.labld.graph.NodeQueryListener;
import br.gov.pf.labld.graph.PathQueryListener;
import br.gov.pf.labld.graph.desktop.renderers.CarNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.CompanyNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.DocumentNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.EmailNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.MoneyBagNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.MoneyTransferNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.PersonNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.PhoneNodeRenderer;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.desktop.FileProcessor;
import dpf.sp.gpinf.indexer.desktop.Messages;
import dpf.sp.gpinf.indexer.search.ItemId;

public class AppGraphAnalytics extends JPanel {

  private static Logger LOGGER = LoggerFactory.getLogger(AppGraphAnalytics.class);

  private static final long serialVersionUID = 1882865120277226931L;

  final GraphModel graphModel = new GraphModel();
  Graph graph;
  GraphPane graphPane;
  private NodePopup nodePopup;
  private StagePopup stagePopup;
  private GraphStatusBar graphStatusBar;
  private GraphToolBar toolBar;

  private boolean databaseLoaded;

  private String contextMenuNodeId;

  private GraphSidePanel sidePanel;

  public AppGraphAnalytics() {
    super();
    this.setLayout(new BorderLayout());
    init();
  }

  public AppGraphAnalytics(boolean isDoubleBuffered) {
    super(isDoubleBuffered);
    this.setLayout(new BorderLayout());
    init();
  }

  public AppGraphAnalytics(LayoutManager layout) {
    super(layout);

    init();
  }

  private void init() {
    this.graph = new Graph();
    this.graph.getSettings().setDefaultEdgeColor(Color.BLUE);
    this.graph.getSettings().setDefaultLabelColor(Color.BLACK);
    this.graph.getSettings().setDefaultNodeColor(Color.BLACK);

    this.graphPane = new GraphPane(graph);

    this.graphPane.addNodeListener(new AppNodeListener());
    this.graphPane.addStageListener(new AppStageListerner());
    this.graphPane.setHistoryEnabled(true);
    this.graphPane.setBackground(Color.WHITE);

    Renderers renderers = this.graphPane.getRenderers();
    renderers.registerNodeRenderer("PESSOA_FISICA", new PersonNodeRenderer());
    renderers.registerNodeRenderer("PESSOA_JURIDICA", new CompanyNodeRenderer());
    renderers.registerNodeRenderer("TELEFONE", new PhoneNodeRenderer());
    renderers.registerNodeRenderer("PLACA", new CarNodeRenderer());
    renderers.registerNodeRenderer("EMAIL", new EmailNodeRenderer());
    renderers.registerNodeRenderer("CONTA_BANCARIA", new MoneyBagNodeRenderer());
    renderers.registerNodeRenderer("SWIFT", new MoneyTransferNodeRenderer());
    renderers.registerNodeRenderer("EVIDENCIA", new DocumentNodeRenderer());

    this.sidePanel = new GraphSidePanel(this.graphPane);

    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, this.graphPane, this.sidePanel);
    splitPane.setOneTouchExpandable(true);
    splitPane.setResizeWeight(0.9d);

    add(splitPane, BorderLayout.CENTER);

    this.nodePopup = new NodePopup(this);
    this.stagePopup = new StagePopup(this);

    this.graphStatusBar = new GraphStatusBar();
    add(this.graphStatusBar, BorderLayout.SOUTH);

    this.toolBar = new GraphToolBar(this);
    this.graphPane.getHistory().addListener(toolBar);
    add(this.toolBar, BorderLayout.NORTH);

    initGraphService();
  }

  private void initGraphService() {
    new LoadGraphDatabaseWorker().execute();
  }

  public void addEvidenceFilesToGraph(Collection<ItemId> items) {
    if (!items.isEmpty()) {
      String[] ids = items.stream().map(s -> Integer.toString(s.getId())).collect(Collectors.toList())
          .toArray(new String[items.size()]);
      new AddEvidenceFileWorker(ids).execute();
    }
  }

  public void addNodesToGraph(Collection<Long> ids) {
    if (!ids.isEmpty()) {
      new AddNodeWorker(this, ids).execute();
    }
  }

  public void addRelationshipsToGraph(Collection<Long> ids) {
    if (!ids.isEmpty()) {
      new AddRelationshipWorker(this, ids).execute();
    }
  }

  public void selectAll() {
    this.graphPane.selectAll();
  }

  private void showNodeAtSidePanel(Node node) {
    this.sidePanel.loadNode(Long.parseLong(node.getId()));
  }

  public void openHoveredEvidence() {
    Node hoveredNode = getHoveredNode();
    if (hoveredNode != null) {
      long id = Long.parseLong(hoveredNode.getId());
      new OpenEvidenceNodeWorker(id).execute();
    }
  }

  public void openEvidence(Node node) {
    long id = Long.parseLong(node.getId());
    new OpenEvidenceNodeWorker(id).execute();
  }

  public void showEvidence(int evidenceId) {
    ItemId itemId = new ItemId(0, evidenceId);
    int luceneId = App.get().appCase.getLuceneId(itemId);
    FileProcessor parsingTask = new FileProcessor(luceneId, false);
    parsingTask.execute();
  }

  public void expandSelected() {
    new ExpandNodeWorker(this.graphPane.getSelectedNodes()).execute();
  }

  public void expandSelectedWithLabels(Collection<String> labels) {
    new ExpandConfigurationNodeWorker(labels, Long.parseLong(this.getContextMenuNodeId())).execute();
    ;
  }

  public void removeSelected() {
    this.graphPane.removeSelectedNodes();
  }

  public void connectSelected() {
    Set<String> selectedIds = this.graphPane.getSelected();
    Set<Long> ids = selectedIds.stream().map(s -> Long.parseLong(s)).collect(Collectors.toSet());
    new FindConnectionsWorker(ids).execute();
  }

  public void findPathSelected() {
    Set<Node> nodes = this.graphPane.getSelectedNodes();
    new FindPathsWorker(nodes).execute();
  }

  public int getNumSelected() {
    return this.graphPane.getSelected().size();
  }

  public Node getHoveredNode() {
    return this.graphPane.getHoveredNode();
  }

  private class AppStageListerner extends StageAdapter {

    @Override
    public void stageDragStarted(MouseEvent e) {
      if (e.isControlDown() || e.isShiftDown()) {
        graphPane.setStageMode(StageMode.SELECTION);
      } else {
        graphPane.setStageMode(StageMode.PAN);
        graphPane.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
      }
    }

    @Override
    public void stageDragStopped(MouseEvent e) {
      graphPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      graphPane.setStageMode(StageMode.PAN);
    }

    @Override
    public void stageClicked(MouseEvent evt) {
      graphPane.deselectAll();
      if (SwingUtilities.isRightMouseButton(evt)) {
        stagePopup.show(graphPane, evt.getX(), evt.getY());
      }
    }

  }

  private class AppNodeListener extends NodeAdapter {

    @Override
    public void nodeClicked(Node node, MouseEvent evt) {
      int clickCount = evt.getClickCount();
      if (SwingUtilities.isRightMouseButton(evt) && clickCount == 1) {
        nodeRightClicked(node, evt);
      } else if (SwingUtilities.isLeftMouseButton(evt) && clickCount > 1) {
        nodeDblClicked(node);
      } else {
        nodeSingleClick(node, evt);
      }
    }

    private void nodeSingleClick(Node node, MouseEvent e) {
      boolean selected = graphPane.isNodeSelected(node);
      boolean keepSelection = e.isControlDown() || e.isShiftDown();
      if (!selected) {
        graphPane.selectNode(node.getId(), keepSelection);
        if (!keepSelection) {
          showNodeAtSidePanel(node);
        }
      } else if (keepSelection) {
        graphPane.deselectNode(node.getId());
      } else {
        graphPane.selectNode(node.getId());
        showNodeAtSidePanel(node);
      }
    }

    private void nodeDblClicked(Node node) {
      graphPane.selectNode(node.getId(), false);
      if (node.getType().equals("EVIDENCIA")) {
        openEvidence(node);
      } else {
        showNodeAtSidePanel(node);
      }
    }

    private void nodeRightClicked(Node node, MouseEvent evt) {
      boolean keepSelection = graphPane.isNodeSelected(node) || evt.isShiftDown() || evt.isControlDown();
      graphPane.selectNode(node.getId(), keepSelection);
      contextMenuNodeId = node.getId();
      nodePopup.show(graphPane, evt.getX(), evt.getY());
    }

    @Override
    public void nodeDragStarted(Collection<Node> nodes, MouseEvent e) {
      graphPane.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
      for (Node node : nodes) {
        if (graphPane.isNodeUnderMouse(node)) {
          boolean keepSelection = graphPane.isNodeSelected(node);
          graphPane.selectNode(node.getId(), keepSelection);
        }
      }
    }

    @Override
    public void nodeDragStopped(Collection<Node> nodes, MouseEvent e) {
      graphPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    @Override
    public void nodeHover(Node node, MouseEvent e) {
      graphPane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public void nodeOut(MouseEvent e) {
      graphPane.setCursor(Cursor.getDefaultCursor());
    }
  }

  public void setDatabaseLoaded(boolean b) {
    this.databaseLoaded = true;
  }

  public boolean isDatabaseLoaded() {
    return databaseLoaded;
  }

  private class OpenEvidenceNodeWorker extends SwingWorker<Void, Void> implements NodeQueryListener {

    private Long id;
    private String evidenceId;

    public OpenEvidenceNodeWorker(Long id) {
      super();
      this.id = id;
    }

    @Override
    public boolean nodeFound(org.neo4j.graphdb.Node node) {
      evidenceId = (String) node.getProperty("evidenceId");
      return true;
    }

    @Override
    protected Void doInBackground() throws Exception {
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Processing"));
      AppGraphAnalytics.this.graphStatusBar.setProgress(0);
      GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
      graphService.getNodes(Arrays.asList(id), this);
      return null;
    }

    @Override
    protected void done() {
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Done"));
      AppGraphAnalytics.this.graphStatusBar.setProgress(100);
      showEvidence(Integer.parseInt(evidenceId));
    }

  }

  private class AddEvidenceFileWorker extends SwingWorker<Void, Node> implements NodeQueryListener {

    private String[] ids;
    private Collection<String> added = new HashSet<>();
    private Collection<Node> nodes = new HashSet<>();
    private int found = 0;

    public AddEvidenceFileWorker(String... ids) {
      super();
      this.ids = ids;
    }

    @Override
    protected Void doInBackground() throws Exception {
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Processing"));
      AppGraphAnalytics.this.graphStatusBar.setProgress(0);
      GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
      graphService.getNodes(ids, this);
      AppGraphAnalytics.this.graph.addNodes(nodes);
      return null;
    }

    @Override
    public boolean nodeFound(org.neo4j.graphdb.Node neo4jNode) {
      String nodeId = Long.toString(neo4jNode.getId());
      found++;
      AppGraphAnalytics.this.graphStatusBar.increaseProgress((int) ((found / this.ids.length) * 100));
      if (!AppGraphAnalytics.this.graph.containsNode(nodeId)) {
        Node node = AppGraphAnalytics.this.graphModel.convert(neo4jNode);
        node.setX((int) (100 + Math.random() * 400));
        node.setY((int) (100 + Math.random() * 400));
        this.added.add(nodeId);
        this.nodes.add(node);
      }
      return true;
    }

    @Override
    protected void done() {
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Done"));
      AppGraphAnalytics.this.graphStatusBar.setProgress(100);
      AppGraphAnalytics.this.graphPane.selectNodes(this.added);
    }

  }

  private class ExpandConfigurationNodeWorker extends ExpandNodeWorker {

    private Collection<String> labels;
    private Long nodeId;

    public ExpandConfigurationNodeWorker(Collection<String> labels, Long nodeId) {
      super(null);
      this.labels = labels;
      this.nodeId = nodeId;
    }

    @Override
    protected Void doInBackground() throws Exception {
      GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Processing"));
      AppGraphAnalytics.this.graphStatusBar.setProgress(0);

      this.currentNode = graph.getNode(nodeId.toString());

      graphService.getNeighboursWithLabels(labels, nodeId, this);
      AppGraphAnalytics.this.graph.addElements(newNodes, newEdges);

      return null;
    }

  }

  private class ExpandNodeWorker extends SwingWorker<Void, Node> implements NodeEdgeQueryListener {

    private Collection<Node> nodes;
    Node currentNode;
    private int currentDegree = 0;

    Collection<Node> newNodes = new HashSet<>();
    Collection<Edge> newEdges = new HashSet<>();

    public ExpandNodeWorker(Collection<Node> nodes) {
      super();
      this.nodes = nodes;
    }

    @Override
    protected Void doInBackground() throws Exception {
      GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Processing"));
      AppGraphAnalytics.this.graphStatusBar.setProgress(0);

      for (Node node : nodes) {
        this.currentNode = node;
        graphService.getNeighbours(Long.parseLong(this.currentNode.getId()), this);
      }

      AppGraphAnalytics.this.graph.addElements(newNodes, newEdges);
      return null;
    }

    @Override
    public boolean nodeFound(org.neo4j.graphdb.Node neo4jNode) {
      String id = Long.toString(neo4jNode.getId());
      AppGraphAnalytics.this.graphStatusBar.increaseProgress(10);
      if (!AppGraphAnalytics.this.graph.containsNode(id)) {
        currentDegree++;
        Node newNode = AppGraphAnalytics.this.graphModel.convert(neo4jNode);
        // AppGraphAnalytics.this.graph.addNode(newNode);
        Point position = AppGraphAnalytics.this.graphModel.calculateRelativePosition(this.currentNode, newNode,
            currentDegree);
        newNode.setX(position.x);
        newNode.setY(position.y);
        newNodes.add(newNode);
      }
      return true;
    }

    @Override
    public boolean edgeFound(Relationship relationship) {
      String id = Long.toString(relationship.getId());
      if (!AppGraphAnalytics.this.graph.containsEdge(id)) {
        String startNodeId = Long.toString(relationship.getStartNodeId());
        String endNodeId = Long.toString(relationship.getEndNodeId());
        Edge edge = new Edge(id, startNodeId, endNodeId);
        edge.setLabel(relationship.getType().name());
        // AppGraphAnalytics.this.graph.addEdge(edge);
        newEdges.add(edge);
      }
      return true;
    }

    @Override
    protected void done() {
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Done"));
      AppGraphAnalytics.this.graphStatusBar.setProgress(100);
      AppGraphAnalytics.this.graphPane.repaint();
    }

  }

  private class FindConnectionsWorker extends SwingWorker<Void, Edge> implements EdgeQueryListener {

    private Set<Long> ids;
    private Collection<Edge> newEdges = new HashSet<>();

    public FindConnectionsWorker(Set<Long> ids) {
      super();
      this.ids = ids;
    }

    @Override
    protected Void doInBackground() throws Exception {
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Processing"));
      AppGraphAnalytics.this.graphStatusBar.setProgress(0);
      GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
      graphService.getConnections(ids, this);
      AppGraphAnalytics.this.graph.addEdges(newEdges);
      return null;
    }

    @Override
    public boolean edgeFound(Relationship relationship) {
      AppGraphAnalytics.this.graphStatusBar.increaseProgress(10);
      String id = Long.toString(relationship.getId());
      if (!AppGraphAnalytics.this.graph.containsEdge(id)) {
        String startNodeId = Long.toString(relationship.getStartNodeId());
        String endNodeId = Long.toString(relationship.getEndNodeId());
        Edge edge = new Edge(id, startNodeId, endNodeId);
        edge.setLabel(relationship.getType().name());
        this.newEdges.add(edge);
      }
      return true;
    }

    @Override
    protected void done() {
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Done"));
      AppGraphAnalytics.this.graphStatusBar.setProgress(100);
      AppGraphAnalytics.this.graphPane.repaint();
    }

  }

  private class FindPathsWorker extends SwingWorker<Void, Node> implements PathQueryListener {

    private Set<Node> nodes;

    private Node currentSource;
    private Node currentTarget;

    private Node leftmost;

    private Map<String, Integer> degreeMap = new HashMap<>();
    private Set<String> newlyAdded = new HashSet<>();

    private Collection<Node> newNodes = new HashSet<>();
    private Collection<Edge> newEdges = new HashSet<>();

    public FindPathsWorker(Set<Node> nodes) {
      super();
      this.nodes = nodes;
    }

    @Override
    protected Void doInBackground() throws Exception {
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Processing"));
      AppGraphAnalytics.this.graphStatusBar.setProgress(0);
      GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
      Set<Node> targets = new HashSet<>(this.nodes);
      for (Node source : nodes) {
        targets.remove(source);
        this.currentSource = source;
        for (Node target : targets) {
          this.currentTarget = target;
          long sourceId = Long.parseLong(source.getId());
          long targetId = Long.parseLong(target.getId());

          this.leftmost = leftmost(source, target);

          graphService.getPaths(sourceId, targetId, 5, this);
        }
      }
      AppGraphAnalytics.this.graph.addElements(newNodes, newEdges);
      return null;
    }

    private Node leftmost(Node node1, Node node2) {
      Node result;

      if (node1.getX() < node2.getX()) {
        result = node1;
      } else if (node1.getX() > node2.getX()) {
        result = node2;
      } else if (node1.getY() < node2.getX()) {
        result = node1;
      } else if (node1.getY() > node2.getX()) {
        result = node2;
      } else {
        result = node1;
      }

      return result;
    }

    @Override
    public boolean pathFound(Path path) {

      int length = path.length();
      int hop = 1;

      org.neo4j.graphdb.Node endNode = path.endNode();
      boolean inverted = this.leftmost.getId().equals(Long.toString(endNode.getId()));
      if (inverted) {
        hop = length - 1;
      }

      org.neo4j.graphdb.Node startNode = path.startNode();
      org.neo4j.graphdb.Node currentStart = startNode;
      for (PropertyContainer propertyContainer : path) {
        AppGraphAnalytics.this.graphStatusBar.increaseProgress(10);
        if (propertyContainer instanceof org.neo4j.graphdb.Relationship) {

          Relationship relationShip = (Relationship) propertyContainer;
          nodeFound(relationShip.getStartNode());
          nodeFound(relationShip.getEndNode());
          edgeFound(relationShip);

        } else if (propertyContainer instanceof org.neo4j.graphdb.Node) {
          org.neo4j.graphdb.Node neo4jNode = (org.neo4j.graphdb.Node) propertyContainer;
          if (startNode.getId() != neo4jNode.getId() && endNode.getId() != neo4jNode.getId()) {
            String startNodeId = Long.toString(currentStart.getId());
            String endNodeId = Long.toString(neo4jNode.getId());

            if (newlyAdded.contains(endNodeId)) {
              newlyAdded.remove(endNodeId);
              Node newNode = AppGraphAnalytics.this.graph.getNode(endNodeId);

              Integer degree = degreeMap.get(startNodeId);
              if (degree == null) {
                degree = 0;
              }
              degreeMap.put(startNodeId, degree + 1);

              Point position = AppGraphAnalytics.this.graphModel.calculateRelativePosition(currentSource, currentTarget,
                  newNode, hop, length, degree);
              newNode.setX(position.x);
              newNode.setY(position.y);
            }
            currentStart = neo4jNode;
            if (inverted) {
              hop--;
            } else {
              hop++;
            }
          }
        }
      }

      return true;
    }

    public void nodeFound(org.neo4j.graphdb.Node neo4jNode) {
      String id = Long.toString(neo4jNode.getId());
      AppGraphAnalytics.this.graphStatusBar.increaseProgress(10);
      if (!AppGraphAnalytics.this.graph.containsNode(id)) {
        Node newNode = AppGraphAnalytics.this.graphModel.convert(neo4jNode);
        AppGraphAnalytics.this.graph.addNode(newNode);
        newlyAdded.add(id);
        newNodes.add(newNode);
      }
    }

    private void edgeFound(Relationship relationship) {
      String id = Long.toString(relationship.getId());
      if (!AppGraphAnalytics.this.graph.containsEdge(id)) {
        String startNodeId = Long.toString(relationship.getStartNodeId());
        String endNodeId = Long.toString(relationship.getEndNodeId());
        Edge edge = new Edge(id, startNodeId, endNodeId);
        edge.setLabel(relationship.getType().name());
        AppGraphAnalytics.this.graph.addEdge(edge);
        newEdges.add(edge);
      }
    }

    @Override
    protected void done() {
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Done"));
      AppGraphAnalytics.this.graphStatusBar.setProgress(100);
      AppGraphAnalytics.this.graphPane.repaint();
    }

  }

  private class LoadGraphDatabaseWorker extends SwingWorker<Void, Void> {

    @Override
    protected Void doInBackground() throws Exception {
      initGraphService();
      return null;
    }

    private void initGraphService() {
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Preparing"));
      AppGraphAnalytics.this.graphStatusBar.setProgress(50);

      AppGraphAnalytics.this.graphPane.setEnabled(false);
      AppGraphAnalytics.this.toolBar.setEnabled(false);

      final ClassLoader classLoader = this.getClass().getClassLoader();
      Thread.currentThread().setContextClassLoader(classLoader);
      GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
      try {
        graphService.start(new File(Configuration.appRoot, GraphTask.DB_PATH));
      } catch (Throwable e) {
        LOGGER.error(e.getMessage(), e);
        throw new RuntimeException(e);
      }
    }

    @Override
    protected void done() {
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Ready"));
      AppGraphAnalytics.this.graphStatusBar.setProgress(100);

      AppGraphAnalytics.this.graphPane.setEnabled(true);
      AppGraphAnalytics.this.toolBar.setEnabled(true);

      AppGraphAnalytics.this.setDatabaseLoaded(true);
    }
  }

  public void exportImage(File selectedFile) {
    AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.ExportingImage"));
    BufferedImage image = this.graphPane.toImage();
    try {
      if (!selectedFile.getName().endsWith(".png")) {
        selectedFile = new File(selectedFile.getParentFile(), selectedFile.getName() + ".png");
      }
      ImageIO.write(image, "png", selectedFile);
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Done"));
    } catch (IOException e) {
      AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Error"));
      throw new RuntimeException(e);
    }
  }

  public boolean containsNode(String startNodeId) {
    return this.graph.containsNode(startNodeId);
  }

  public boolean containsEdge(String edgeId) {
    return this.graph.containsEdge(edgeId);
  }

  public void addEdges(Collection<Edge> newEdges) {
    this.graph.addEdges(newEdges);
  }

  public void addNodes(Collection<Node> nodes) {
    this.graph.addNodes(nodes);
  }

  public Node addNode(org.neo4j.graphdb.Node neo4jNode) {
    String nodeId = Long.toString(neo4jNode.getId());
    if (!this.graph.containsNode(nodeId)) {
      Node node = this.graphModel.convert(neo4jNode);
      node.setX((int) (100 + Math.random() * 400));
      node.setY((int) (100 + Math.random() * 400));
      this.graph.addNode(node);
      return node;
    }
    return null;
  }

  public Edge addRelationship(Relationship relationship) {
    String edgeId = Long.toString(relationship.getId());
    if (!this.containsEdge(edgeId)) {
      String startNodeId = Long.toString(relationship.getStartNodeId());
      String endNodeId = Long.toString(relationship.getEndNodeId());
      if (this.containsNode(startNodeId) && this.containsNode(endNodeId)) {
        Edge edge = new Edge(edgeId, startNodeId, endNodeId);
        edge.setLabel(relationship.getType().name());
        this.graph.addEdge(edge);
        return edge;
      }
    }
    return null;
  }

  public String getContextMenuNodeId() {
    return contextMenuNodeId;
  }

  public void undo() {
    this.graphPane.getHistory().undo();
    this.graphPane.repaint();
  }

  public void redo() {
    this.graphPane.getHistory().redo();
    this.graphPane.repaint();
  }

  public void fitToScreen() {
    this.graphPane.fitToScreen(1.2d);
    this.graphPane.repaint();
  }

  public GraphModel getGraphModel() {
    return graphModel;
  }

  public void applyLayout(Layout layout) {
    this.graphPane.applyLayout(layout);
    this.graphPane.repaint();
    this.sidePanel.getGraphPreviewPane().repaint();
  }

  public void searchLinks(List<String> origin, List<String> destiny, List<String> queries) {
    new SearchLinksWorker(this, origin, destiny, queries).execute();
  }

  public void setStatus(String status) {
    this.graphStatusBar.setStatus(status);
  }

  public void setProgress(int p) {
    this.graphStatusBar.setProgress(p);
  }

  public void increaseProgress(int p) {
    this.graphStatusBar.increaseProgress(p);
  }

  public void addPath(Path path) {
    for (org.neo4j.graphdb.Node node : path.nodes()) {
      addNode(node);
    }
    for (org.neo4j.graphdb.Relationship rel : path.relationships()) {
      addRelationship(rel);
    }
  }

}
