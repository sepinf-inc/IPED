package iped.viewers;

import java.awt.Desktop;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import iped.data.IItem;
import iped.io.IStreamSource;
import iped.parsers.util.Util;
import iped.utils.IOUtil;
import iped.utils.UiUtil;
import iped.viewers.api.AbstractViewer;
import iped.viewers.localization.Messages;
import iped.viewers.search.HitsUpdater;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

public class HtmlViewer extends AbstractViewer {

    private static Logger LOGGER = LoggerFactory.getLogger(HtmlViewer.class);

    private JFXPanel jfxPanel;
    private static int MAX_SIZE = 10000000;

    private static final String hitStyle = "color:#2F3603; background-color:#FDFE2D";
    private static final String selStyle = "color:#2F0201; background-color:#F79131";

    private static String LARGE_FILE_MSG = "<html><body>" //$NON-NLS-1$
            + Messages.getString("HtmlViewer.TooBigToOpen") //$NON-NLS-1$
            + "<br><br><a href=\"\" onclick=\"app.openExternal()\">" //$NON-NLS-1$
            + Messages.getString("HtmlViewer.OpenExternally") //$NON-NLS-1$
            + "</a></body></html>"; //$NON-NLS-1$

    private String idToScroll;
    private String nameToScroll;

    WebView htmlViewer;
    WebEngine webEngine;
    boolean enableJavascript = false;
    FileHandler fileHandler = new FileHandler();

    protected volatile File tmpFile;
    private volatile String fileName;
    protected Set<String> highlightTerms;
    private volatile boolean scrollToPositionDone = false;

    private volatile Document doc;
    private volatile String[] queryTerms;
    private volatile boolean isNavigableTree;
    private volatile int currTerm = -1;
    private volatile Object currExec;
    private volatile boolean isRunning;
    private final Object lock = new Object();

    // TODO change viewer api and move this to loadFile method
    public void setElementIDToScroll(String id) {
        idToScroll = id;
    }

    public void setElementNameToScroll(String name) {
        nameToScroll = name;
    }

    protected int getMaxHtmlSize() {
        return MAX_SIZE;
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.equals("text/html") //$NON-NLS-1$
                || contentType.equals("application/xhtml+xml") //$NON-NLS-1$
                || contentType.equals("text/asp") //$NON-NLS-1$
                || contentType.equals("text/aspdotnet"); //$NON-NLS-1$

    }

    public HtmlViewer() {
        super(new GridLayout());

        Platform.setImplicitExit(false);
        jfxPanel = new JFXPanel();

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                htmlViewer = new WebView();
                webEngine = htmlViewer.getEngine();
                addHighlighter();

                StackPane root = new StackPane();
                root.getChildren().add(htmlViewer);

                Scene scene = new Scene(root);
                jfxPanel.setScene(scene);
            }
        });

        this.getPanel().add(jfxPanel);
    }

    @Override
    public void loadFile(final IStreamSource content, final Set<String> terms) {

        if (tmpFile != null) {
            tmpFile.delete();
        }

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                scrollToPositionDone = false;
                webEngine.load(null);
                webEngine.loadContent(UiUtil.getUIEmptyHtml());

                if (content != null) {
                    try {
                        tmpFile = getTempFile(content);
                        if (content instanceof IItem) {
                            fileName = ((IItem) content).getName();
                        } else {
                            fileName = tmpFile.getName();
                        }
                        highlightTerms = terms;

                        if (tmpFile.length() <= getMaxHtmlSize()) {
                            webEngine.setJavaScriptEnabled(enableJavascript);
                            webEngine.setUserStyleSheetLocation(UiUtil.getUIHtmlStyle());
                            webEngine.load(tmpFile.toURI().toURL().toString());

                        } else {
                            webEngine.setJavaScriptEnabled(true);
                            webEngine.loadContent(LARGE_FILE_MSG);
                        }

                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private File getTempFile(IStreamSource content) {
        try (InputStream in = content.getSeekableInputStream()) {
            File tmpFile = File.createTempFile("iped", ".html");
            Files.copy(in, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            tmpFile.deleteOnExit();
            return tmpFile;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isSearchSupported() {
        return true;
    }

    public class FileHandler {

        public void openExternal() {
            if (IOUtil.isToOpenExternally(fileName, Util.getTrueExtension(tmpFile))) {
                openFile(tmpFile);
            }
        }

        protected void openFile(final File file) {
            new Thread() {
                public void run() {
                    try {
                        Desktop.getDesktop().open(file.getCanonicalFile());

                    } catch (Throwable e) {
                        try {
                            if (System.getProperty("os.name").startsWith("Windows")) { //$NON-NLS-1$ //$NON-NLS-2$
                                Runtime.getRuntime().exec(new String[] { "rundll32", "SHELL32.DLL,ShellExec_RunDLL", //$NON-NLS-1$ //$NON-NLS-2$
                                        "\"" + file.getCanonicalFile() + "\"" }); //$NON-NLS-1$ //$NON-NLS-2$
                            } else {
                                Runtime.getRuntime()
                                        .exec(new String[] { "xdg-open", file.toURI().toURL().toString() }); //$NON-NLS-1$
                            }

                        } catch (Exception e2) {
                            e2.printStackTrace();
                        }
                    }
                }
            }.start();
        }
    }

    protected void addHighlighter() {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                final WebEngine webEngine = htmlViewer.getEngine();
                webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
                    @Override
                    public void changed(ObservableValue<? extends Worker.State> ov, Worker.State oldState,
                            Worker.State newState) {

                        addJavascriptListener(webEngine);

                        if (newState == Worker.State.SUCCEEDED || newState == Worker.State.FAILED) {

                            if (tmpFile != null && !webEngine.getLocation().endsWith(tmpFile.getName()))
                                return;

                            try {
                                Object exec = currExec = new Object();
                                synchronized (lock) {
                                    while (isRunning) {
                                        lock.wait();
                                    }
                                    isRunning = true;
                                }
                                doc = webEngine.getDocument();
                                isNavigableTree = false;
                                if (doc != null) {
                                    try {
                                        Boolean nt = (Boolean) webEngine.executeScript("document.querySelector('details') != null");
                                        isNavigableTree = nt.booleanValue();
                                    } catch (Exception e) {
                                    }
    
                                    currentHit = -1;
                                    totalHits = 0;
                                    hits = new ArrayList<Object>();
                                    if (highlightTerms != null && highlightTerms.size() > 0) {
                                        highlightNode(doc, false, exec);
                                    }
    
                                } else if (tmpFile != null) {
                                    LOGGER.info("Null DOM to highlight!");
                                    queryTerms = highlightTerms.toArray(new String[0]);
                                    currTerm = queryTerms.length > 0 ? 0 : -1;
                                    if (shouldScrollToHit()) {
                                        scrollToNextHit(true);
                                    }
                                }
                                if (doc != null) {
                                    scrollToPosition();
                                }
                            } catch(Exception e) {
                                e.printStackTrace();
                            } finally{
                                synchronized (lock) {
                                    isRunning = false;
                                    lock.notifyAll();
                                }
                            }
                        }
                    }
                });
            }
        });
    }

    @Override
    public void searchInViewer(String term, HitsUpdater hitsUpdater) {
        if (doc != null) {
            Object exec = currExec = new Object();
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    if (currExec != exec) {
                        return;
                    }
                    try {
                        synchronized (lock) {
                            while (isRunning) {
                                lock.wait();
                            }
                            if (currExec != exec) {
                                return;
                            }
                            isRunning = true;
                        }
                        currentHit = -1;
                        totalHits = 0;
                        hits = new ArrayList<Object>();
                        highlightTerms = new HashSet<String>();
                        if (term != null && !term.isBlank()) {
                            highlightTerms.add(term.toLowerCase());
                        }
                        clearNode(doc, exec);
                        if (doc != null && !highlightTerms.isEmpty()) {
                            highlightNode(doc, false, exec);
                            scrollToPosition();
                        }
                        if (hitsUpdater != null) {                    
                            hitsUpdater.updateHits(currentHit + 1, totalHits);
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                    } finally{
                        synchronized (lock) {
                            isRunning = false;
                            lock.notifyAll();
                        }
                    }
                }
            });
        }
    }

    protected void addJavascriptListener(WebEngine webEngine) {
        if (webEngine.isJavaScriptEnabled()) {
            JSObject window = (JSObject) webEngine.executeScript("window"); //$NON-NLS-1$
            window.setMember("app", fileHandler); //$NON-NLS-1$
        }
    }

    private boolean shouldScrollToHit() {
        return idToScroll == null && nameToScroll == null && !scrollToPositionDone;
    }

    private String scrollIntoView(String var) {
        StringBuilder sb = new StringBuilder();

        sb.append("margin = 32;");
        sb.append("mTop = 96;");
        sb.append("r = ").append(var).append(".getBoundingClientRect();");

        sb.append("y = window.scrollY;");
        sb.append("if (r.top < mTop) ");
        sb.append("    y += r.top - mTop;");
        sb.append("else if (r.bottom > window.innerHeight - margin) ");
        sb.append("    y += r.bottom - window.innerHeight + margin;");

        sb.append("x = window.scrollX;");
        sb.append("if (r.left < margin) ");
        sb.append("    x += r.left - margin;");
        sb.append("else if (r.right > window.innerWidth - margin) ");
        sb.append("    x += r.right - window.innerWidth + margin;");

        sb.append("window.scrollTo(x, y);");

        return sb.toString();
    }

    protected void scrollToPosition() {
        boolean done = false;
        boolean exception = false;
        try {
            if (idToScroll != null) {
                done = (Boolean) webEngine.executeScript(""
                        + "function find(){"
                        + "  x = document.getElementById(\"" + idToScroll + "\");"
                        + "  if(x != null){"
                        + "    " + scrollIntoView("x")
                        + "    return true;"
                        + "  }"
                        + "  return false;"
                        + "}"
                        + "find();");

            } else if (nameToScroll != null) {
                done = (Boolean) webEngine.executeScript(""
                        + "function find(){"
                        + "  var x = document.getElementsByName(\"" + nameToScroll + "\");"
                        + "  if(x != null && x.length > 0){"
                        + "    " + scrollIntoView("x[0]")
                        + "    return true;"
                        + "  }"
                        + "  return false;"
                        + "}"
                        + "find();");
            }
            if (done) {
                scrollToPositionDone = true;
            }

        } catch (Exception e) {
            e.printStackTrace();
            exception = true;
        }
        if(done || exception) {
            idToScroll = null;
            nameToScroll = null;
        }
    }

    protected ArrayList<Object> hits;

    protected void highlightNode(Node node, boolean parentVisible, Object exec) {
        if (node == null || exec != currExec) {
            return;
        }

        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String nodeName = node.getNodeName();
            if (nodeName.equalsIgnoreCase("body")) {
                parentVisible = true;
            } else if (nodeName.equalsIgnoreCase("script") || nodeName.equalsIgnoreCase("style")) {
                parentVisible = false;
            }
        }

        Node subnode = node.getFirstChild();

        if (subnode != null) {
            do {

                if (parentVisible && (subnode.getNodeType() == Node.TEXT_NODE)) {
                    String term;
                    do {
                        String value = subnode.getNodeValue();
                        String fValue = value.toLowerCase();

                        int idx = Integer.MAX_VALUE;
                        term = null;
                        for (String t : highlightTerms) {
                            int j = 0, i;
                            do {
                                i = fValue.indexOf(t, j);
                                if (i != -1 && i < idx) {
                                    idx = i;
                                    term = t;
                                    break;
                                }
                            } while (i != -1 && i < idx);

                        }
                        if (term != null) {
                            Node preNode = subnode.cloneNode(false);
                            preNode.setNodeValue(value.substring(0, idx));
                            node.insertBefore(preNode, subnode);

                            Element termNode = doc.createElement("span");
                            termNode.setAttribute("style", hitStyle);
                            termNode.appendChild(doc.createTextNode(value.substring(idx, idx + term.length())));
                            termNode.setAttribute("id", "ipedHit-" + totalHits);
                            hits.add(termNode);
                            totalHits++;
                            node.insertBefore(termNode, subnode);
                            subnode.setNodeValue(value.substring(idx + term.length()));
                            if (isNavigableTree) {
                                // expands all parent elements of a hit
                                try {
                                    webEngine.executeScript("for (let el = document.getElementById('ipedHit-"
                                            + (totalHits - 1)
                                            + "'); el; el = el.parentElement) if (el.tagName === 'DETAILS') el.open = true;");
                                } catch (Exception e) {
                                }
                            }
                            if (totalHits == 1) {
                                termNode.setAttribute("style", selStyle);
                                if (shouldScrollToHit()) {
                                    webEngine.executeScript("x = document.getElementById(\"ipedHit-" + ++currentHit + "\"); " + scrollIntoView("x"));
                                }
                            }
                        }
                    } while (term != null);

                }

                highlightNode(subnode, parentVisible, exec);

            } while ((subnode = subnode.getNextSibling()) != null && exec == currExec);
        }
    }

    protected void clearNode(Node node, Object exec) {
        if (node == null || exec != currExec) {
            return;
        }
        Node subnode = node.getFirstChild();
        if (subnode != null) {
            StringBuilder sb = new StringBuilder();
            do {
                if (subnode instanceof Element) {
                    String attr = ((Element) subnode).getAttribute("id");
                    if (attr != null && attr.startsWith("ipedHit")) {
                        Node prevSubnode = subnode.getPreviousSibling();
                        sb.setLength(0);
                        sb.append(prevSubnode.getNodeValue());
                        sb.append(subnode.getFirstChild().getNodeValue());
                        Node nextSubnode = subnode.getNextSibling();
                        node.removeChild(prevSubnode);
                        node.removeChild(subnode);
                        sb.append(nextSubnode.getNodeValue());
                        nextSubnode.setNodeValue(sb.toString());
                        subnode = nextSubnode;
                    }
                }
                clearNode(subnode, exec);
            } while ((subnode = subnode.getNextSibling()) != null && exec == currExec);
        }
    }

    @Override
    public void init() {

    }

    @Override
    public void dispose() {

    }

    @Override
    public String getName() {
        return "Html"; //$NON-NLS-1$
    }

    @Override
    public void scrollToNextHit(final boolean forward) {
        scrollToNextHit(forward, false, null);
    }

    @Override
    public void scrollToNextHit(final boolean forward, final boolean wrap, final HitsUpdater hitsUpdater) {
        if (totalHits <= 0) {
            return;
        }

        Platform.runLater(new Runnable() {
            @Override
            public void run() {

                if (forward) {
                    if (doc != null) {
                        if (currentHit < totalHits - 1 || (currentHit == totalHits - 1 && wrap)) {
                            if (currentHit >= 0) {
                                Element termNode = (Element) hits.get(currentHit);
                                termNode.setAttribute("style", hitStyle);
                            }
                            if (++currentHit >= totalHits) {
                                currentHit = 0;
                            }
                            Element termNode = (Element) hits.get(currentHit);
                            termNode.setAttribute("style", selStyle);
                            webEngine.executeScript("x = document.getElementById(\"ipedHit-" + currentHit + "\"); " + scrollIntoView("x"));
                        }
                    } else {
                        while (currTerm < queryTerms.length && queryTerms.length > 0) {
                            if (currTerm == -1) {
                                currTerm = 0;
                            }
                            if ((Boolean) webEngine.executeScript("window.find(\"" + queryTerms[currTerm] + "\")")) { //$NON-NLS-1$ //$NON-NLS-2$
                                break;
                            } else {
                                currTerm++;
                                if (currTerm != queryTerms.length) {
                                    webEngine.executeScript("window.getSelection().collapse(document.body,0)"); //$NON-NLS-1$
                                }
                            }

                        }
                    }

                } else {
                    if (doc != null) {
                        if (currentHit > 0 || (currentHit == 0 && wrap)) {
                            Element termNode = (Element) hits.get(currentHit);
                            termNode.setAttribute("style", hitStyle);
                            if (--currentHit < 0) {
                                currentHit = totalHits - 1;
                            }
                            termNode = (Element) hits.get(currentHit);
                            termNode.setAttribute("style", selStyle);
                            webEngine.executeScript("x = document.getElementById(\"ipedHit-" + currentHit + "\"); " + scrollIntoView("x"));
                        }
                    } else {
                        while (currTerm > -1) {
                            if (currTerm == queryTerms.length) {
                                currTerm = queryTerms.length - 1;
                            }
                            if ((Boolean) webEngine
                                    .executeScript("window.find(\"" + queryTerms[currTerm] + "\", false, true)")) { //$NON-NLS-1$ //$NON-NLS-2$
                                break;
                            } else {
                                currTerm--;
                                if (currTerm != -1) {
                                    webEngine.executeScript("window.getSelection().selectAllChildren(document)"); //$NON-NLS-1$
                                    webEngine.executeScript("window.getSelection().collapseToEnd()"); //$NON-NLS-1$
                                }

                            }

                        }
                    }
                }
                if (hitsUpdater != null) {
                    hitsUpdater.updateHits(currentHit + 1, totalHits);
                }
            }
        });

    }

}
