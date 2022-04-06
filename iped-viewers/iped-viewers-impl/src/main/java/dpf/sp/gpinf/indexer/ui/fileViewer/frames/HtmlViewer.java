package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.Desktop;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import dpf.sp.gpinf.indexer.parsers.util.Util;
import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.util.UiUtil;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.io.IStreamSource;
import iped3.IItem;
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

@SuppressWarnings("restriction")
public class HtmlViewer extends AbstractViewer {

    private static Logger LOGGER = LoggerFactory.getLogger(HtmlViewer.class);

    private static final long serialVersionUID = 1L;
    private JFXPanel jfxPanel;
    private static int MAX_SIZE = 10000000;

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
        // System.out.println("AbstractViewer " + getName() + " ok");
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

    volatile Document doc;
    volatile String[] queryTerms;
    int currTerm = -1;

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

                            doc = webEngine.getDocument();

                            if (doc != null) {
                                // System.out.println("Highlighting");
                                currentHit = -1;
                                totalHits = 0;
                                hits = new ArrayList<Object>();
                                if (highlightTerms != null && highlightTerms.size() > 0) {
                                    highlightNode(doc, false);
                                }

                            } else if (tmpFile != null) {
                                LOGGER.info("Null DOM to highlight!"); //$NON-NLS-1$
                                queryTerms = highlightTerms.toArray(new String[0]);
                                currTerm = queryTerms.length > 0 ? 0 : -1;
                                if (shouldScrollToHit()) {
                                    scrollToNextHit(true);
                                }
                            }
                            if (doc != null) {
                                scrollToPosition();
                            }
                        }
                    }
                });
            }
        });

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

    protected void scrollToPosition() {
        boolean done = false;
        boolean exception = false;
        try {
            if (idToScroll != null) {
                done = (Boolean) webEngine.executeScript(""
                        + "function find(){"
                        + "  x = document.getElementById(\"" + idToScroll + "\");"
                        + "  if(x != null){"
                        + "    x.scrollIntoView(false);"
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
                        + "    x[0].scrollIntoView({block: \"center\", inline: \"nearest\"});"
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

    protected void highlightNode(Node node, boolean parentVisible) {
        if (node == null) {
            return;
        }

        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String nodeName = node.getNodeName();
            if (nodeName.equalsIgnoreCase("body")) { //$NON-NLS-1$
                parentVisible = true;
            } else if (nodeName.equalsIgnoreCase("script") || nodeName.equalsIgnoreCase("style")) { //$NON-NLS-1$ //$NON-NLS-2$
                parentVisible = false;
            }
        }

        Node subnode = node.getFirstChild();

        if (subnode != null) {
            do {

                if (parentVisible && (subnode
                        .getNodeType() == Node.TEXT_NODE /*
                                                          * || subnode . getNodeType ( ) == Node . CDATA_SECTION_NODE
                                                          */)) {
                    String term;
                    do {
                        String value = subnode.getNodeValue();

                        // remove acentos, etc
                        /*
                         * char[] input = value.toLowerCase().toCharArray(); char[] output = new
                         * char[input.length * 4]; int outLen = ASCIIFoldingFilter.foldToASCII(input, 0,
                         * output, 0, input.length); String fValue = new String(output, 0, outLen);
                         */
                        String fValue = value.toLowerCase();

                        int idx = Integer.MAX_VALUE;
                        term = null;
                        for (String t : highlightTerms) {
                            int j = 0, i;
                            do {
                                i = fValue.indexOf(t, j);
                                if (i != -1 && i < idx) {
                                    /*
                                     * if( (i == 0 || !Character.isLetterOrDigit( fValue.charAt(i - 1))) && (i ==
                                     * fValue.length() - t.length() || !Character .isLetterOrDigit(fValue.charAt(i +
                                     * t.length()))) )
                                     */
                                    {
                                        idx = i;
                                        term = t;
                                        break;
                                    }
                                    // j = i + 1;
                                }
                            } while (i != -1 && i < idx);

                        }
                        if (term != null) {
                            Node preNode = subnode.cloneNode(false);
                            preNode.setNodeValue(value.substring(0, idx));
                            node.insertBefore(preNode, subnode);

                            Element termNode = doc.createElement("b"); //$NON-NLS-1$
                            termNode.setAttribute("style", "color:black; background-color:yellow"); //$NON-NLS-1$ //$NON-NLS-2$
                            termNode.appendChild(doc.createTextNode(value.substring(idx, idx + term.length())));
                            termNode.setAttribute("id", "indexerHit-" + totalHits); //$NON-NLS-1$ //$NON-NLS-2$
                            hits.add(termNode);
                            totalHits++;
                            node.insertBefore(termNode, subnode);

                            subnode.setNodeValue(value.substring(idx + term.length()));
                            if (totalHits == 1) {
                                termNode.setAttribute("style", "color:white; background-color:blue"); //$NON-NLS-1$ //$NON-NLS-2$
                                if (shouldScrollToHit()) {
                                    webEngine.executeScript("document.getElementById(\"indexerHit-" + ++currentHit //$NON-NLS-1$
                                            + "\").scrollIntoView(false);"); //$NON-NLS-1$
                                }
                            }

                        }
                    } while (term != null);

                }

                highlightNode(subnode, parentVisible);

            } while ((subnode = subnode.getNextSibling()) != null);
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

        Platform.runLater(new Runnable() {
            @Override
            public void run() {

                if (forward) {
                    if (doc != null) {
                        if (currentHit < totalHits - 1) {
                            if (currentHit >= 0) {
                                Element termNode = (Element) hits.get(currentHit);
                                termNode.setAttribute("style", "color:black; background-color:yellow"); //$NON-NLS-1$ //$NON-NLS-2$
                            }
                            Element termNode = (Element) hits.get(++currentHit);
                            termNode.setAttribute("style", "color:white; background-color:blue"); //$NON-NLS-1$ //$NON-NLS-2$
                            webEngine.executeScript("document.getElementById(\"indexerHit-" + (currentHit) //$NON-NLS-1$
                                    + "\").scrollIntoView(false);"); //$NON-NLS-1$
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
                        if (currentHit > 0) {
                            Element termNode = (Element) hits.get(currentHit);
                            termNode.setAttribute("style", "color:black; background-color:yellow"); //$NON-NLS-1$ //$NON-NLS-2$
                            termNode = (Element) hits.get(--currentHit);
                            termNode.setAttribute("style", "color:white; background-color:blue"); //$NON-NLS-1$ //$NON-NLS-2$
                            webEngine.executeScript("document.getElementById(\"indexerHit-" + (currentHit) //$NON-NLS-1$
                                    + "\").scrollIntoView(false);"); //$NON-NLS-1$
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

            }
        });

    }

}
