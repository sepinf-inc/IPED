/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.search.viewer;

import java.awt.Desktop;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Set;

import netscape.javascript.JSObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.sun.javafx.application.PlatformImpl;

import dpf.sp.gpinf.indexer.util.ProxySever;
import dpf.sp.gpinf.indexer.util.StreamSource;
import dpf.sp.gpinf.indexer.util.IOUtil;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

public class HtmlViewer extends AbstractViewer {

	private static Logger LOGGER = LoggerFactory.getLogger(HtmlViewer.class);
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JFXPanel jfxPanel;
	private WebView htmlViewer;
	private static int MAX_SIZE = 10000000;
	
	private static String LARGE_FILE_MSG = "<html><body>"
			+ "Arquivo muito grande para ser exibido internamente!<br><br>"
			+ "<a href=\"\" onclick=\"app.open()\">Abrir externamente</a>"
			+ "</body></html>";

	WebEngine webEngine;

	protected volatile File file;
	protected Set<String> highlightTerms;

	@Override
	public boolean isSupportedType(String contentType) {
		return contentType.equals("text/html") || contentType.equals("application/xhtml+xml") || contentType.equals("text/asp") || contentType.equals("text/aspdotnet")
				|| contentType.equals("message/outlook-pst") || contentType.equals("application/messenger-plus") || contentType.equals("application/outlook-contact")
				|| contentType.equals("message/x-whatsapp-msg") || contentType.equals("application/x-edb-table");
	}

	public HtmlViewer() {
		super(new GridLayout());
		// Instala proxy local que fecha conexões para Internet
		ProxySever.start();

		Platform.setImplicitExit(false);
		jfxPanel = new JFXPanel();

		PlatformImpl.startup(new Runnable() {
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
		// System.out.println("Viewer " + getName() + " ok");
	}

	private File tmpFile;

	@Override
	public void loadFile(final StreamSource content, final Set<String> terms) {

		if (tmpFile != null)
			tmpFile.delete();

		PlatformImpl.runLater(new Runnable() {
			@Override
			public void run() {

				webEngine.load(null);
				if (content != null)
					try {
						file = content.getFile();
						highlightTerms = terms;
						if (file.length() <= MAX_SIZE) {
							if (!file.getName().endsWith(".html") && !file.getName().endsWith(".htm")) {
								try {
									tmpFile = File.createTempFile("indexador", ".html");
									tmpFile.deleteOnExit();
									IOUtil.copiaArquivo(file, tmpFile);
									file = tmpFile;
								} catch (IOException e) {
									e.printStackTrace();
								}

							}
							webEngine.setJavaScriptEnabled(false);
							webEngine.load(file.toURI().toURL().toString());

						} else{
							webEngine.setJavaScriptEnabled(true);
							webEngine.loadContent(LARGE_FILE_MSG);
						}

					} catch (MalformedURLException e) {
						e.printStackTrace();
					}
			}
		});
	}
	
	public class JavaApplication {
		public void open() {
			try {
				Desktop.getDesktop().open(file.getCanonicalFile());
			} catch (Exception e) {
				try {
					// Windows Only
					Runtime.getRuntime().exec(new String[] { "rundll32", "SHELL32.DLL,ShellExec_RunDLL", "\"" + file.getCanonicalFile() + "\"" });
				} catch (Exception e2) {
					try {
						// Linux Only
						Runtime.getRuntime().exec(new String[] { "xdg-open", "\"" + file.toURI().toURL() + "\"" });
					} catch (Exception e3) {
						e3.printStackTrace();
					}
				}
			}

		}
	}

	volatile Document doc;
	volatile String[] queryTerms;
	int currTerm = -1;

	protected void addHighlighter() {
		PlatformImpl.runLater(new Runnable() {
			@Override
			public void run() {
				final WebEngine webEngine = htmlViewer.getEngine();
				webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
					@Override
					public void changed(ObservableValue ov, Worker.State oldState, Worker.State newState) {
						
						if (newState == Worker.State.SUCCEEDED || newState == Worker.State.FAILED) {

							if(webEngine.isJavaScriptEnabled()){
								JSObject window = (JSObject) webEngine.executeScript("window");
								window.setMember("app", new JavaApplication());
							}
							
							doc = webEngine.getDocument();
							// doc = null;

							// TODO destacar documeto nulo ou alterar
							// SecurityManager
							if (file != null)
								if (doc != null) {
									// System.out.println("Highlighting");
									currentHit = -1;
									totalHits = 0;
									hits = new ArrayList<Object>();
									if (highlightTerms != null && highlightTerms.size() > 0)
										highlightNode(doc, false);

								} else {
									LOGGER.info("Null DOM to highlight!");
									queryTerms = highlightTerms.toArray(new String[0]);
									currTerm = queryTerms.length > 0 ? 0 : -1;

									// highlight completo via javascript
									// (mais lento)
									/*
									 * webEngine.executeScript(
									 * "document.designMode = \"on\"");
									 * for(String term : queryTerms){ while
									 * ((Boolean)webEngine.executeScript
									 * ("window.find(\"" + term + "\")"))
									 * webEngine.executeScript(
									 * "document.execCommand(\"BackColor\", false, \"Yellow\")"
									 * ); webEngine.executeScript(
									 * "window.getSelection().collapse(document,0)"
									 * ); } webEngine.executeScript(
									 * "document.designMode = \"off\"");
									 * webEngine
									 * .executeScript("window.scrollTo(0,0)" );
									 */
									scrollToNextHit(true);

								}
						}
					}
				});

			}
		});

	}

	protected ArrayList<Object> hits;

	protected void highlightNode(Node node, boolean parentVisible) {
		if (node == null)
			return;

		if (node.getNodeType() == Node.ELEMENT_NODE) {
			String nodeName = node.getNodeName();
			if (nodeName.equalsIgnoreCase("body"))
				parentVisible = true;
			else if (nodeName.equalsIgnoreCase("script") || nodeName.equalsIgnoreCase("style"))
				parentVisible = false;
		}

		Node subnode = node.getFirstChild();

		if (subnode != null)
			do {

				if (parentVisible && (subnode.getNodeType() == Node.TEXT_NODE /*
																			 * ||
																			 * subnode
																			 * .
																			 * getNodeType
																			 * (
																			 * )
																			 * ==
																			 * Node
																			 * .
																			 * CDATA_SECTION_NODE
																			 */)) {
					String term;
					do {
						String value = subnode.getNodeValue();

						// remove acentos, etc
						/*
						 * char[] input = value.toLowerCase().toCharArray();
						 * char[] output = new char[input.length * 4]; int
						 * outLen = ASCIIFoldingFilter.foldToASCII(input, 0,
						 * output, 0, input.length); String fValue = new
						 * String(output, 0, outLen);
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
									 * if( (i == 0 ||
									 * !Character.isLetterOrDigit(
									 * fValue.charAt(i - 1))) && (i ==
									 * fValue.length() - t.length() ||
									 * !Character
									 * .isLetterOrDigit(fValue.charAt(i +
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

							Element termNode = doc.createElement("b");
							termNode.setAttribute("style", "color:black; background-color:yellow");
							termNode.appendChild(doc.createTextNode(value.substring(idx, idx + term.length())));
							termNode.setAttribute("id", "indexerHit-" + totalHits);
							hits.add(termNode);
							totalHits++;
							node.insertBefore(termNode, subnode);

							subnode.setNodeValue(value.substring(idx + term.length()));
							if (totalHits == 1) {
								termNode.setAttribute("style", "color:white; background-color:blue");
								webEngine.executeScript("document.getElementById(\"indexerHit-" + ++currentHit + "\").scrollIntoView(false);");
							}

						}
					} while (term != null);

				}

				highlightNode(subnode, parentVisible);

			} while ((subnode = subnode.getNextSibling()) != null);
	}

	/*
	 * public void loadFile2(File file){ if(file!= null &&
	 * (file.getName().endsWith(".html") || file.getName().endsWith(".htm")))
	 * loadFile2(getHtmlVersion(file)); else loadFile2(file); }
	 * 
	 * private File getHtmlVersion(File file){
	 * 
	 * try { Metadata metadata = new Metadata();
	 * //metadata.set(Metadata.CONTENT_TYPE, contentType); TikaInputStream tis =
	 * TikaInputStream.get(file); ParseContext context = new ParseContext();
	 * //context.set(IndexerContext.class, new
	 * IndexerContext(file.getAbsolutePath(), null)); File outFile =
	 * File.createTempFile("indexador", ".html"); outFile.deleteOnExit();
	 * OutputStream outStream = new BufferedOutputStream(new
	 * FileOutputStream(outFile)); ToHTMLContentHandler handler = new
	 * ToHTMLContentHandler(outStream, "windows-1252");
	 * ((Parser)App.get().autoParser).parse(tis, handler, metadata, context);
	 * tis.close(); return outFile;
	 * 
	 * } catch (Exception e) { e.printStackTrace(); } return null;
	 * 
	 * }
	 */

	@Override
	public void init() {

	}

	@Override
	public void dispose() {

	}

	@Override
	public String getName() {
		return "Html";
	}

	@Override
	public void scrollToNextHit(final boolean forward) {

		PlatformImpl.runLater(new Runnable() {
			@Override
			public void run() {

				if (forward) {
					if (doc != null) {
						if (currentHit < totalHits - 1) {
							Element termNode = (Element) hits.get(currentHit);
							termNode.setAttribute("style", "color:black; background-color:yellow");
							termNode = (Element) hits.get(++currentHit);
							termNode.setAttribute("style", "color:white; background-color:blue");
							webEngine.executeScript("document.getElementById(\"indexerHit-" + (currentHit) + "\").scrollIntoView(false);");
						}
					} else {
						while (currTerm < queryTerms.length && queryTerms.length > 0) {
							if (currTerm == -1)
								currTerm = 0;
							if ((Boolean) webEngine.executeScript("window.find(\"" + queryTerms[currTerm] + "\")"))
								break;
							else {
								currTerm++;
								if (currTerm != queryTerms.length)
									webEngine.executeScript("window.getSelection().collapse(document.body,0)");
							}

						}
					}

				} else {
					if (doc != null) {
						if (currentHit > 0) {
							Element termNode = (Element) hits.get(currentHit);
							termNode.setAttribute("style", "color:black; background-color:yellow");
							termNode = (Element) hits.get(--currentHit);
							termNode.setAttribute("style", "color:white; background-color:blue");
							webEngine.executeScript("document.getElementById(\"indexerHit-" + (currentHit) + "\").scrollIntoView(false);");
						}
					} else {
						while (currTerm > -1) {
							if (currTerm == queryTerms.length)
								currTerm = queryTerms.length - 1;
							if ((Boolean) webEngine.executeScript("window.find(\"" + queryTerms[currTerm] + "\", false, true)"))
								break;
							else {
								currTerm--;
								if (currTerm != -1) {
									webEngine.executeScript("window.getSelection().selectAllChildren(document)");
									webEngine.executeScript("window.getSelection().collapseToEnd()");
								}

							}

						}
					}
				}

			}
		});

	}

}
