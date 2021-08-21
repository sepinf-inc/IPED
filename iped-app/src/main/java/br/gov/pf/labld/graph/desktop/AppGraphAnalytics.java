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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import org.kharon.Edge;
import org.kharon.EdgeListener;
import org.kharon.Graph;
import org.kharon.GraphPane;
import org.kharon.Node;
import org.kharon.NodeAdapter;
import org.kharon.StageAdapter;
import org.kharon.StageMode;
import org.kharon.layout.HistoryEnabledLayout;
import org.kharon.layout.graphviz.GraphVizAlgorithm;
import org.kharon.renderers.Renderers;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.gov.pf.labld.graph.EdgeQueryListener;
import br.gov.pf.labld.graph.GraphConfiguration;
import br.gov.pf.labld.graph.GraphService;
import br.gov.pf.labld.graph.GraphServiceFactoryImpl;
import br.gov.pf.labld.graph.GraphTask;
import br.gov.pf.labld.graph.NodeEdgeQueryListener;
import br.gov.pf.labld.graph.PathQueryListener;
import br.gov.pf.labld.graph.desktop.renderers.CarNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.CompanyNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.DocumentNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.EmailNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.MoneyBagNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.MoneyTransferNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.PeopleNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.PersonNodeRenderer;
import br.gov.pf.labld.graph.desktop.renderers.PhoneNodeRenderer;
import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.desktop.ClearFilterListener;
import dpf.sp.gpinf.indexer.desktop.Messages;
import dpf.sp.gpinf.indexer.search.ItemId;

public class AppGraphAnalytics extends JPanel implements ClearFilterListener {

    static Logger LOGGER = LoggerFactory.getLogger(AppGraphAnalytics.class);

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

    private void updateThemeColors() {
        Color background = UIManager.getColor("Viewer.background");
        if (background == null)
            background = Color.WHITE;
        Color foreground = UIManager.getColor("Viewer.foreground");
        if (foreground == null)
            foreground = Color.BLACK;
        Color defaultEdgeColor = UIManager.getColor("Graph.defaultEdge");
        if (defaultEdgeColor == null)
            defaultEdgeColor = Color.BLUE;
        Color defaultNodeColor = UIManager.getColor("Graph.defaultNode");
        if (defaultNodeColor == null)
            defaultNodeColor = Color.BLACK;
        Color selectedNodeBoxColor = UIManager.getColor("Graph.selectedNodeBox");
        if (selectedNodeBoxColor == null)
            selectedNodeBoxColor = Color.BLUE;
        Color selectionBoxColor = UIManager.getColor("Graph.selectionBox");
        if (selectionBoxColor == null)
            selectionBoxColor = Color.BLUE;
        this.graphPane.setBackground(background);
        this.graphPane.setForeground(foreground);
        this.graph.getSettings().setDefaultEdgeColor(defaultEdgeColor);
        this.graph.getSettings().setDefaultLabelColor(defaultNodeColor);
        this.graph.getSettings().setDefaultNodeColor(defaultNodeColor);
        this.graph.getSettings().setDefaultSelectionColor(selectedNodeBoxColor);
        this.graph.getSettings().setSelectionColor(selectionBoxColor);
    }

    private void init() {
        this.graph = new Graph();

        this.graphPane = new GraphPane(graph) {
            @Override
            public void updateUI() {
                updateThemeColors();
                super.updateUI();
            }
        };

        this.graphPane.addNodeListener(new AppNodeListener());
        this.graphPane.addStageListener(new AppStageListerner());
        this.graphPane.addEdgeListener(new AppEdgeListener());
        this.graphPane.setHistoryEnabled(true);
        this.graphPane.updateUI();

        Renderers renderers = this.graphPane.getRenderers();
        renderers.registerNodeRenderer(GraphConfiguration.PERSON_LABEL, new PersonNodeRenderer());
        renderers.registerNodeRenderer(GraphConfiguration.ORGANIZATION_LABEL, new CompanyNodeRenderer());
        renderers.registerNodeRenderer(GraphConfiguration.PHONE_LABEL, new PhoneNodeRenderer());
        renderers.registerNodeRenderer(GraphConfiguration.CAR_LABEL, new CarNodeRenderer());
        renderers.registerNodeRenderer(GraphConfiguration.EMAIL_LABEL, new EmailNodeRenderer());
        renderers.registerNodeRenderer(GraphConfiguration.BANK_ACCOUNT_LABEL, new MoneyBagNodeRenderer());
        renderers.registerNodeRenderer(GraphConfiguration.MONEY_TRANSFER_LABEL, new MoneyTransferNodeRenderer());
        renderers.registerNodeRenderer(GraphConfiguration.DOCUMENT_LABEL, new DocumentNodeRenderer());

        renderers.registerNodeRenderer(GraphConfiguration.DATASOURCE_LABEL + "," + GraphConfiguration.DOCUMENT_LABEL,
                new DocumentNodeRenderer());
        renderers.registerNodeRenderer(GraphConfiguration.DATASOURCE_LABEL + "," + GraphConfiguration.PERSON_LABEL,
                new PersonNodeRenderer());
        renderers.registerNodeRenderer(
                GraphConfiguration.DATASOURCE_LABEL + "," + GraphConfiguration.ORGANIZATION_LABEL,
                new CompanyNodeRenderer());

        renderers.registerNodeRenderer(GraphConfiguration.CONTACT_GROUP_LABEL, new PeopleNodeRenderer());

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
    }

    public void initGraphService() {
        new LoadGraphDatabaseWorker(this).execute();
    }

    public void addEvidenceFilesToGraph(Collection<ItemId> items) {
        if (!items.isEmpty()) {
            String[] ids = items.stream().map(s -> Integer.toString(s.getId())).collect(Collectors.toList())
                    .toArray(new String[items.size()]);
            new AddEvidenceFileWorker(ids).execute();
        }
    }

    public AddNodeWorker addNodesToGraph(Collection<Long> ids) {
        if (!ids.isEmpty()) {
            AddNodeWorker worker = new AddNodeWorker(this, ids);
            worker.execute();
            return worker;
        }
        return null;
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
            new ShowEvidenceNodeWorker(this, id).execute();
        }
    }

    public void openEvidence(Node node) {
        long id = Long.parseLong(node.getId());
        new ShowEvidenceNodeWorker(this, id).execute();
    }

    public void expandSelected() {
        new ExpandNodeWorker(this.graphPane.getSelectedNodes()).execute();
    }

    public void expandSelectedWithLabelsOrTypes(Collection<String> labelsOrTypes, boolean isEdge, int maxNodes) {
        new ExpandConfigurationNodeWorker(labelsOrTypes, Long.parseLong(this.getContextMenuNodeId()), isEdge, maxNodes)
                .execute();
    }

    public void removeSelected() {
        FilterSelectedEdges.getInstance().unselecEdgesOfNodes(this.graphPane.getSelected());
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
            if (e.isControlDown() || e.isShiftDown() || SwingUtilities.isRightMouseButton(e)) {
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
            if (evt.isControlDown() || evt.isShiftDown())
                return;
            graphPane.deselectAll();
            FilterSelectedEdges.getInstance().clearSelection(true);
            if (SwingUtilities.isRightMouseButton(evt)) {
                stagePopup.show(graphPane, evt.getX(), evt.getY());
            }
        }

    }

    @Override
    public void clearFilter() {
        graphPane.deselectAll();
        FilterSelectedEdges.getInstance().clearSelection(false);
    }

    private class AppEdgeListener implements EdgeListener {

        @Override
        public void edgeClicked(Edge edge, MouseEvent e) {
            boolean selected = graphPane.isEdgeSelected(edge);
            boolean keepSelection = e.isControlDown() || e.isShiftDown();
            if (!selected) {
                graphPane.selectEdge(edge.getId(), keepSelection);
                FilterSelectedEdges.getInstance().addEdge(edge, keepSelection);

            } else if (keepSelection) {
                graphPane.deselectEdge(edge.getId());
                FilterSelectedEdges.getInstance().removeEdge(edge);
            } else {
                graphPane.selectEdge(edge.getId());
                FilterSelectedEdges.getInstance().setEdge(edge);
            }
        }

        @Override
        public void edgeHovered(Edge edge, MouseEvent e) {
            graphPane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        public void edgeOut(Edge edge, MouseEvent e) {
            graphPane.setCursor(Cursor.getDefaultCursor());
        }

        public void edgesSelected(Collection<Edge> edges, MouseEvent e) {
            boolean keepSelection = e.isControlDown() || e.isShiftDown();
            FilterSelectedEdges.getInstance().selectEdges(edges, keepSelection);
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

    public void setDatabaseLoaded(boolean loaded) {
        this.databaseLoaded = loaded;
        if (loaded) {
            new ShowMoreConnectedNode().execute();
        }
    }

    public boolean isDatabaseLoaded() {
        return databaseLoaded;
    }

    private class ShowMoreConnectedNode extends SwingWorker<Void, Void> {

        private static final int MAX_NEIGHBOURS = ExpandConfigurationDialog.MAX_NEIGHBOURS;
        private List<Long> ids;

        @Override
        protected Void doInBackground() {
            try {
                GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
                long t = System.currentTimeMillis();
                ids = graphService.getMoreConnectedNodes(10);
                LOGGER.info("Query {} most connected nodes took {}s", ids.size(),
                        (System.currentTimeMillis() - t) / 1000);
                for (Long id : ids) {
                    AddNodeWorker worker = new AddNodeWorker(AppGraphAnalytics.this, Collections.singleton(id));
                    worker.execute();
                    worker.get();
                    t = System.currentTimeMillis();
                    ExpandNodeWorker expandWorker = new ExpandNodeWorker(
                            Collections.singleton(graph.getNode(id.toString())), MAX_NEIGHBOURS);
                    expandWorker.execute();
                    expandWorker.get();
                    LOGGER.info("Expand node {} took {}s", id, (System.currentTimeMillis() - t) / 1000);
                }
                applyDefaultLayout();

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void done() {
            if (!ids.isEmpty()) {
                JOptionPane.showMessageDialog(App.get(), Messages.getString("GraphAnalysis.InitialGraphMsg"));
            }
        }

    }

    private class AddEvidenceFileWorker extends ExpandNodeWorker {

        private String[] ids;
        private int found = 0;

        public AddEvidenceFileWorker(String... ids) {
            super(null);
            this.ids = ids;
        }

        @Override
        protected Void doInBackground() {
            AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Processing"));
            AppGraphAnalytics.this.graphStatusBar.setProgress(0);
            GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
            graphService.getEdges(ids, this);
            AppGraphAnalytics.this.graph.addElements(newNodes, newEdges);
            return null;
        }

        @Override
        public boolean edgeFound(Relationship relationship) {
            found++;
            AppGraphAnalytics.this.graphStatusBar.increaseProgress((int) ((found / this.ids.length) * 100));
            super.nodeFound(relationship.getStartNode());
            super.nodeFound(relationship.getEndNode());
            return super.edgeFound(relationship);
        }

        @Override
        protected Point getPosition(Node newNode) {
            return new Point((int) (100 + Math.random() * 400), (int) (100 + Math.random() * 400));
        }

        @Override
        protected void done() {
            AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Done"));
            AppGraphAnalytics.this.graphStatusBar.setProgress(100);
        }

    }

    private class ExpandConfigurationNodeWorker extends ExpandNodeWorker {

        private Collection<String> labelsOrTypes;
        private Long nodeId;
        private boolean isEdge;
        private int maxNodes;

        public ExpandConfigurationNodeWorker(Collection<String> labelsOrTypes, Long nodeId, boolean isEdge,
                int maxNodes) {
            super(null);
            this.labelsOrTypes = labelsOrTypes;
            this.nodeId = nodeId;
            this.isEdge = isEdge;
            this.maxNodes = maxNodes;
        }

        @Override
        protected Void doInBackground() {
            GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
            AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Processing"));
            AppGraphAnalytics.this.graphStatusBar.setProgress(0);

            this.currentNode = graph.getNode(nodeId.toString());

            try {
                if (!isEdge) {
                    if (labelsOrTypes == null || labelsOrTypes.isEmpty())
                        graphService.getNeighbours(nodeId, this, maxNodes);
                    else
                        graphService.getNeighboursWithLabels(labelsOrTypes, nodeId, this, maxNodes);
                } else {
                    graphService.getNeighboursWithRelationships(labelsOrTypes, nodeId, this, maxNodes);
                }

            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
            AppGraphAnalytics.this.graph.addElements(newNodes, newEdges);

            return null;
        }

    }

    private class ExpandNodeWorker extends SwingWorker<Void, Node> implements NodeEdgeQueryListener {

        private Collection<Node> nodes;
        Node currentNode;
        private int currentDegree = 0;
        private int maxNeighbours;

        Collection<Node> newNodes = new HashSet<>();
        Collection<Edge> newEdges = new HashSet<>();

        public ExpandNodeWorker(Collection<Node> nodes) {
            this(nodes, -1);
        }

        public ExpandNodeWorker(Collection<Node> nodes, int maxNeighbours) {
            super();
            this.nodes = nodes;
            this.maxNeighbours = maxNeighbours;
        }

        @Override
        protected Void doInBackground() throws Exception {
            GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
            AppGraphAnalytics.this.graphStatusBar.setStatus(Messages.getString("GraphAnalysis.Processing"));
            AppGraphAnalytics.this.graphStatusBar.setProgress(0);

            for (Node node : nodes) {
                this.currentNode = node;
                graphService.getNeighbours(Long.parseLong(this.currentNode.getId()), this, maxNeighbours);
            }

            AppGraphAnalytics.this.graph.addElements(newNodes, newEdges);
            return null;
        }

        protected Point getPosition(Node newNode) {
            return AppGraphAnalytics.this.graphModel.calculateRelativePosition(this.currentNode, newNode,
                    currentDegree);
        }

        @Override
        public boolean nodeFound(org.neo4j.graphdb.Node neo4jNode) {
            String id = Long.toString(neo4jNode.getId());
            AppGraphAnalytics.this.graphStatusBar.increaseProgress(10);
            if (!AppGraphAnalytics.this.graph.containsNode(id)) {
                currentDegree++;
                Node newNode = AppGraphAnalytics.this.graphModel.convert(neo4jNode);
                // AppGraphAnalytics.this.graph.addNode(newNode);
                Point position = getPosition(newNode);
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
                setEdgeLabel(edge, relationship);
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
        protected Void doInBackground() {
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
                setEdgeLabel(edge, relationship);
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
        protected Void doInBackground() {
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

                            Point position = AppGraphAnalytics.this.graphModel.calculateRelativePosition(currentSource,
                                    currentTarget, newNode, hop, length, degree);
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
                setEdgeLabel(edge, relationship);
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

    private Node prepareDBNode(org.neo4j.graphdb.Node neo4jNode) {
        Node node = this.graphModel.convert(neo4jNode);
        node.setX((int) (100 + Math.random() * 400));
        node.setY((int) (100 + Math.random() * 400));
        return node;
    }

    public Node addNode(org.neo4j.graphdb.Node neo4jNode) {
        String nodeId = Long.toString(neo4jNode.getId());
        if (!this.graph.containsNode(nodeId)) {
            Node node = prepareDBNode(neo4jNode);
            this.graph.addNode(node);
            return node;
        }
        return null;
    }

    public void addGraphElements(GraphElements graphElements) {
        this.graph.addElements(graphElements.getNodes(), graphElements.getEdges());
    }

    public GraphElements addPaths(Collection<Path> paths, GraphElements graphElements) {
        for (Path path : paths) {
            for (org.neo4j.graphdb.Node neo4jNode : path.nodes()) {
                String nodeId = Long.toString(neo4jNode.getId());
                if (!this.graph.containsNode(nodeId)) {
                    Node node = prepareDBNode(neo4jNode);
                    graphElements.add(node);
                }

            }
            for (org.neo4j.graphdb.Relationship relationship : path.relationships()) {
                String edgeId = Long.toString(relationship.getId());
                boolean relationshipInGraph = this.containsEdge(edgeId);
                boolean relationshipInNewEdges = graphElements.containsEdge(edgeId);
                if (!relationshipInGraph && !relationshipInNewEdges) {
                    String startNodeId = Long.toString(relationship.getStartNodeId());
                    String endNodeId = Long.toString(relationship.getEndNodeId());
                    boolean inGraph = this.containsNode(startNodeId) && this.containsNode(endNodeId);
                    boolean inNewNodes = graphElements.containsNode(startNodeId)
                            && graphElements.containsNode(endNodeId);
                    if (inGraph || inNewNodes) {
                        Edge edge = new Edge(edgeId, startNodeId, endNodeId);
                        setEdgeLabel(edge, relationship);
                        graphElements.add(edge);
                    }
                }
            }
        }

        return graphElements;
    }

    public GraphElements addPaths(Collection<Path> paths) {
        return addPaths(paths, new GraphElements());
    }

    public void addPath(Path path) {
        addPaths(Arrays.asList(path));
    }

    public void addPath(Path path, GraphElements graphElements) {
        addPaths(Arrays.asList(path), graphElements);
    }

    public Edge addRelationship(Relationship relationship) {
        String edgeId = Long.toString(relationship.getId());
        if (!this.containsEdge(edgeId)) {
            String startNodeId = Long.toString(relationship.getStartNodeId());
            String endNodeId = Long.toString(relationship.getEndNodeId());
            if (this.containsNode(startNodeId) && this.containsNode(endNodeId)) {
                Edge edge = new Edge(edgeId, startNodeId, endNodeId);
                setEdgeLabel(edge, relationship);
                this.graph.addEdge(edge);
                return edge;
            }
        }
        return null;
    }

    private void setEdgeLabel(Edge edge, Relationship relationship) {
        String sourceUUID = (String) relationship.getProperty(GraphTask.RELATIONSHIP_SOURCE);
        String relId = (String) relationship.getProperty(GraphTask.RELATIONSHIP_ID);
        edge.setLabel(sourceUUID + "_" + relId);
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

    private void applyDefaultLayout() {
        if (!this.graph.isEmpty()) {
            HistoryEnabledLayout layout = new GraphVizLayoutUniqueEdges(GraphVizAlgorithm.NEATO,
                    new GraphVizIpedResolver());
            applyLayout(layout);
        }
    }

    public void applyLayout(HistoryEnabledLayout layout) {
        this.graphPane.applyLayout(layout);
        fitToScreen();
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

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        this.graphPane.setEnabled(enabled);
        this.toolBar.setEnabled(enabled);
    }

}
