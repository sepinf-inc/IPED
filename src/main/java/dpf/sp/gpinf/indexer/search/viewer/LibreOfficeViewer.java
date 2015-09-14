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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ag.ion.bion.officelayer.NativeView;
import ag.ion.bion.officelayer.application.IOfficeApplication;
import ag.ion.bion.officelayer.application.OfficeApplicationException;
import ag.ion.bion.officelayer.application.OfficeApplicationRuntime;
import ag.ion.bion.officelayer.desktop.DesktopException;
import ag.ion.bion.officelayer.desktop.IFrame;
import ag.ion.bion.officelayer.document.DocumentDescriptor;
import ag.ion.bion.officelayer.document.IDocument;
import ag.ion.bion.officelayer.presentation.IPresentationDocument;
import ag.ion.bion.officelayer.spreadsheet.ISpreadsheetDocument;
import ag.ion.bion.officelayer.text.ITextDocument;
import ag.ion.bion.officelayer.text.ITextRange;
import ag.ion.noa.search.ISearchResult;
import ag.ion.noa.search.SearchDescriptor;

import com.sun.star.awt.FontUnderline;
import com.sun.star.awt.FontWeight;
import com.sun.star.awt.XBitmap;
import com.sun.star.awt.XDevice;
import com.sun.star.awt.XDisplayBitmap;
import com.sun.star.awt.XGraphics;
import com.sun.star.awt.XWindow;
import com.sun.star.beans.XPropertySet;
import com.sun.star.container.XIndexAccess;
import com.sun.star.drawing.XDrawPage;
import com.sun.star.drawing.XDrawPages;
import com.sun.star.drawing.XDrawPagesSupplier;
import com.sun.star.drawing.XDrawView;
import com.sun.star.lib.uno.Proxy;
import com.sun.star.sheet.XSpreadsheet;
import com.sun.star.sheet.XSpreadsheetView;
import com.sun.star.sheet.XSpreadsheets;
import com.sun.star.table.XCell;
import com.sun.star.table.XCellRange;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextRange;
import com.sun.star.uno.Any;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.util.XProtectable;
import com.sun.star.util.XSearchDescriptor;
import com.sun.star.util.XSearchable;
import com.sun.star.view.DocumentZoomType;
import com.sun.star.view.XSelectionSupplier;

import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.util.StreamSource;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ProcessUtil;

public class LibreOfficeViewer extends AbstractViewer {

	private static Logger LOGGER = LoggerFactory.getLogger(LibreOfficeViewer.class);
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private IOfficeApplication officeApplication;
	private NativeView nat;
	private volatile IFrame officeFrame;
	private JPanel noaPanel;
	private String nativelib, pathLO;

	private static String userProfile = "$SYSUSERCONFIG/.indexador/" + Versao.APP_VERSION + "/libreoffice";
	private static String RESTART_MSG = "Reiniciando visualizador...";
	private static int PORT = 8100;

	private static int XLS_LENGTH_TO_COPY = 1000000;

	@Override
	public boolean isSupportedType(String contentType) {

		return contentType.startsWith("application/msword")
				|| contentType.equals("application/rtf")
				|| contentType.startsWith("application/vnd.ms-word")
				|| contentType.startsWith("application/vnd.ms-powerpoint")
				|| contentType.startsWith("application/vnd.openxmlformats-officedocument")
				|| contentType.startsWith("application/vnd.oasis.opendocument")
				|| contentType.startsWith("application/vnd.sun.xml")
				|| contentType.startsWith("application/vnd.stardivision")
				//|| contentType.startsWith("image/")
				// contentType.startsWith("application/vnd.ms-works") ||
				// contentType.startsWith("application/x-tika-msoffice") ||
				//|| contentType.startsWith("application/x-tika-ooxml")
				|| contentType.startsWith("application/x-tika-ooxml-protected")
				|| contentType.equals("application/vnd.visio") 
				|| contentType.equals("application/x-mspublisher")
				|| contentType.equals("application/postscript")
				|| contentType.equals("text/x-dbf")
				|| contentType.equals("text/csv")
				|| contentType.equals("application/x-emf")
				|| contentType.equals("application/x-msmetafile")
				|| contentType.equals("image/vnd.adobe.photoshop")
				|| contentType.equals("image/x-portable-bitmap")
				|| contentType.equals("image/svg+xml")
				|| contentType.equals("image/x-pcx")
				|| contentType.equals("image/vnd.dxf")
				|| contentType.equals("image/cdr")
				|| isSpreadSheet(contentType);

	}

	public boolean isSpreadSheet(String contentType) {
		return contentType.startsWith("application/vnd.ms-excel") || contentType.startsWith("application/x-tika-msworks-spreadsheet")
				|| contentType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml") || contentType.startsWith("application/vnd.oasis.opendocument.spreadsheet");

	}

	@Override
	public String getName() {
		return "Office";
	}

	public LibreOfficeViewer(String nativelib, String pathLO) {
		super(new GridLayout());
		this.nativelib = nativelib;
		this.pathLO = pathLO;
		this.noaPanel = new JPanel();
		this.getPanel().add(noaPanel);
	}

	@Override
	public void init() {
		ProcessUtil.killProcess(PORT);
		startLO();
		constructLOFrame();
		edtMonitor = monitorEventThreadBlocking();

		try {
			blankDoc = File.createTempFile("indexador", ".doc");
			blankDoc.deleteOnExit();
			document = officeApplication.getDocumentService().constructNewHiddenDocument(IDocument.WRITER);
			document.getPersistenceService().store(blankDoc.getAbsolutePath());

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void startLO() {

		try {
			HashMap configuration = new HashMap();
			// if(System.getProperty("os.name").startsWith("Windows"))
			configuration.put(IOfficeApplication.APPLICATION_HOME_KEY, pathLO);
			/*
			 * else{ IApplicationAssistant ass = new
			 * ApplicationAssistant(libPath + "/lib"); ILazyApplicationInfo[]
			 * ila = ass.getLocalApplications(); if(ila.length > 0)
			 * configuration.put(IOfficeApplication.APPLICATION_HOME_KEY,
			 * ila[0].getHome()); }
			 */
			
			configuration.put(IOfficeApplication.APPLICATION_TYPE_KEY, IOfficeApplication.LOCAL_APPLICATION);
			
			ArrayList<String> options = new ArrayList<String>();
			options.add("-env:UserInstallation=" + userProfile);
			String prefix = "";
			if (pathLO.toLowerCase().contains("libre"))
				prefix = "-";
			options.add(prefix + "-invisible");
			options.add(prefix + "-nologo");
			options.add(prefix + "-nodefault");
			options.add(prefix + "-norestore");
			options.add(prefix + "-nocrashreport");
			options.add(prefix + "-nolockcheck");
			
			configuration.put(IOfficeApplication.APPLICATION_ARGUMENTS_KEY, options.toArray(new String[0]));

			officeApplication = OfficeApplicationRuntime.getApplication(configuration);
			officeApplication.activate();
			officeApplication.getDesktopService().activateTerminationPrevention();

			LOGGER.info("LibreOffice running with pid {}", ProcessUtil.getPid(PORT));

		} catch (Exception e1) {
			e1.printStackTrace();
		}

	}

	private void constructLOFrame() {
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				@Override
				public void run() {
					LOGGER.info("Constructing LibreOffice frame...");
					nat = new NativeView(nativelib);
					noaPanel.removeAll();
					noaPanel.add(nat);
					noaPanel.addComponentListener(new ComponentAdapter() {
						@Override
						public void componentResized(ComponentEvent e) {
							nat.setPreferredSize(new Dimension(noaPanel.getWidth(), noaPanel.getHeight() - 5));
							noaPanel.getLayout().layoutContainer(noaPanel);
							super.componentResized(e);
						}
					});
					nat.setPreferredSize(new Dimension(noaPanel.getWidth(), noaPanel.getHeight() - 5));
					noaPanel.validate();
					noaPanel.setVisible(false);
					// noaPanel.getLayout().layoutContainer(noaPanel);

					try {
						officeFrame = officeApplication.getDesktopService().constructNewOfficeFrame(nat);
						LOGGER.info("LibreOffice frame ok");

					} catch (DesktopException e1) {
						e1.printStackTrace();
					} catch (OfficeApplicationException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			});
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	private volatile boolean loading = false;
	private volatile boolean restartCalled = false;
	public volatile File lastFile = null;
	// private volatile Thread loadingThread;
	private Thread edtMonitor;

	private Object startLOLock = new Object();
	private IDocument document = null;
	private File blankDoc;

	@Override
	public void loadFile(StreamSource content, Set<String> highlightTerms) {
		loadFile(content, "", highlightTerms);
	}

	@Override
	public void loadFile(final StreamSource content, final String contentType, final Set<String> highlightTerms) {
		
		final File file = content != null ? content.getFile() : null;
		lastFile = file;

		new Thread() {
			@Override
			public void run() {

				synchronized (startLOLock) {
					if (loading && (lastFile == file)) {
						loading = false;
						restartLO();
					}
				}

				if (file != lastFile)
					return;

				try {
					// loadingThread = this;
					restartCalled = false;
					DocumentDescriptor descriptor = DocumentDescriptor.DEFAULT;
					descriptor.setReadOnly(true);

					if (file != null) {
						loading = true;
						setNoaPanelVisible(true);
						preventPPSPlay();

						if (isSpreadSheet(contentType) && file.length() < XLS_LENGTH_TO_COPY) {
							descriptor.setReadOnly(false);
							copySpreadsheetToHighlight();
						}

						document = officeApplication.getDocumentService().loadDocument(officeFrame, lastFile.getAbsolutePath(), descriptor);
						ajustLayout();

					} else {
						boolean isVisible = noaPanel.isVisible();
						setNoaPanelVisible(false);
						if (isVisible) {
							document = officeApplication.getDocumentService().loadDocument(officeFrame, blankDoc.getAbsolutePath(), descriptor);
							ajustLayout();
						}

					}

					loading = false;

					if (file != null && highlightTerms != null)
						highlightText(highlightTerms);

				} catch (Exception e) {
					loading = false;

					LOGGER.info(e.toString());
					// System.out.println("exception!");
					//e.printStackTrace();

					if (e.toString().contains("Document not found"))
						setNoaPanelVisible(false);
					else if (!restartCalled)
						synchronized (startLOLock) {
							restartLO();
						}

				}

			}
		}.start();

	}

	private Thread monitorEventThreadBlocking() {
		Thread edtMonitor = new Thread() {
			volatile boolean blocked;

			public void run() {
				while (true) {
					blocked = true;
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							blocked = false;
						}
					});
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
					}

					if (blocked && lastFile != null) {
						LOGGER.info("Congelamento da interface detectado! Recuperando...");
						synchronized (startLOLock) {
							restartLO();
						}
						// loadFile(lastFile, lastContentType,
						// lastHighlightTerms);
					}
				}
			}
		};

		edtMonitor.setDaemon(true);
		edtMonitor.start();
		return edtMonitor;

	}

	public void restartLO() {
		LOGGER.info("Restarting LibreOffice...");
		restartCalled = true;

		// loadingThread.interrupt();
		ProcessUtil.killProcess(PORT);
		// process.destroy();

		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				@Override
				public void run() {
					noaPanel.removeAll();
					noaPanel.add(new JLabel(RESTART_MSG));
					noaPanel.validate();
				}
			});
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		startLO();
		constructLOFrame();

		LOGGER.info("LibreOffice restarted.");
	}

	private int delta = 1;

	public void releaseFocus() {
		if (officeFrame != null) {
			try {
				officeFrame.getXFrame().getContainerWindow().setVisible(false);
				officeFrame.getXFrame().getContainerWindow().setVisible(true);
				noaPanel.setSize(noaPanel.getWidth() + delta, noaPanel.getHeight());
				delta *= -1;
			} catch (Exception e) {
			}
		}
	}

	private volatile File tempFile = null;

	private void preventPPSPlay() {
		if (lastFile.getName().toLowerCase().contains(".pps"))
			copyToTempFile(".ppt");
	}

	private void copySpreadsheetToHighlight() {
		copyToTempFile(".tmp");
	}

	private void copyToTempFile(String ext) {
		if (tempFile != lastFile)
			try {
				if (tempFile != null)
					tempFile.delete();
				tempFile = File.createTempFile("indexador-", ext);
				tempFile.deleteOnExit();
				IOUtil.copiaArquivo(lastFile, tempFile);
				lastFile = tempFile;

			} catch (IOException e) {
				e.printStackTrace();
			}
	}

	private void ajustLayout() {
		if (document != null)
			try {
				// officeFrame.getLayoutManager().showElement(LayoutManager.URL_STATUSBAR);
				officeFrame.getLayoutManager().hideAll();

				if (document instanceof ITextDocument)
					((ITextDocument) document).zoom(DocumentZoomType.PAGE_WIDTH, (short) 100);

				if (document instanceof IPresentationDocument)
					((IPresentationDocument) document).getPresentationSupplier().getPresentation().end();

			} catch (Exception e) {
				// e.printStackTrace();
			}
	}

	public void setNoaPanelVisible(final boolean visible) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				noaPanel.setVisible(visible);
				noaPanel.setSize(noaPanel.getWidth() + delta, noaPanel.getHeight());
				delta *= -1;
			}
		});
	}

	@Override
	public void dispose() {
		edtMonitor.interrupt();

		if (officeApplication != null) {
			try {
				officeApplication.deactivate();
				officeApplication.dispose();
				ProcessUtil.killProcess(PORT);

			} catch (OfficeApplicationException e1) {
				e1.printStackTrace();
			}
		}
	}

	@Override
	public void copyScreen(Component comp) {
		XWindow xWindow = officeFrame.getXFrame().getContainerWindow();

		XDevice xDevice = UnoRuntime.queryInterface(XDevice.class, xWindow);
		XBitmap xBitmap = xDevice.createBitmap(0, 0, this.getPanel().getWidth(), this.getPanel().getHeight());

		XGraphics xGraphics = xDevice.createGraphics();

		if (xBitmap != null) {
			XDisplayBitmap xDisplayBitmap = xDevice.createDisplayBitmap(xBitmap);

			com.sun.star.awt.Size aSize = xBitmap.getSize();

			xGraphics.draw(xDisplayBitmap, 0, 0, aSize.Width, aSize.Height, 0, 0, this.getPanel().getWidth(), this.getPanel().getHeight());

			byte array[] = xBitmap.getDIB();

			InputStream in = new ByteArrayInputStream(array);
			BufferedImage image = null;
			try {
				image = ImageIO.read(in);
			} catch (IOException e) {
				e.printStackTrace();
			}
			TransferableImage trans = new TransferableImage(image);
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(trans, trans);
		}
	}

	private ArrayList<Object> hits;

	private void highlightText(Set<String> terms) {
		currentHit = -1;
		totalHits = 0;
		hits = new ArrayList<Object>();
		if (terms.size() == 0)
			return;

		try {
			if (document instanceof ITextDocument) {
				ITextDocument textDocument = (ITextDocument) document;

				for (String term : terms) {
					SearchDescriptor searchDescriptor = new SearchDescriptor(term);
					searchDescriptor.setIsCaseSensitive(false);
					searchDescriptor.setUseCompleteWords(false);
					ISearchResult searchResult = ((ITextDocument) document).getSearchService().findAll(searchDescriptor);
					ITextRange[] textRanges = searchResult.getTextRanges();
					for (ITextRange range : textRanges)
						if (range != null) {
							XPropertySet xPropertySet = UnoRuntime.queryInterface(XPropertySet.class, range.getXTextRange());
							xPropertySet.setPropertyValue("CharBackColor", 0xFFFF00);
							xPropertySet.setPropertyValue("CharColor", 0x000000);
							hits.add(range);
							totalHits++;
							if (totalHits == 1) {
								textDocument.getViewCursorService().getViewCursor().goToRange(range, false);
								currentHit = 0;
							}
						}
				}

			} else if (document instanceof ISpreadsheetDocument) {
				for (String term : terms) {
					ISpreadsheetDocument spreadsheetDocument = (ISpreadsheetDocument) document;
					XSpreadsheets spreadsheets = spreadsheetDocument.getSpreadsheetDocument().getSheets();
					for (String sheetName : spreadsheets.getElementNames()) {
						XSpreadsheet sheet = UnoRuntime.queryInterface(XSpreadsheet.class, spreadsheets.getByName(sheetName));
						XProtectable protectable = UnoRuntime.queryInterface(XProtectable.class, sheet);
						if (protectable.isProtected())
							LOGGER.info("Protected sheet: {}", sheetName);
						// protectable.unprotect("");
						XSearchable xSearchable = UnoRuntime.queryInterface(XSearchable.class, sheet);
						XSearchDescriptor xSearchDesc = xSearchable.createSearchDescriptor();
						xSearchDesc.setSearchString(term);
						xSearchDesc.setPropertyValue("SearchCaseSensitive", Boolean.FALSE);
						xSearchDesc.setPropertyValue("SearchWords", Boolean.FALSE);

						XIndexAccess xIndexAccess = xSearchable.findAll(xSearchDesc);
						if (xIndexAccess != null)
							for (int i = 0; i < xIndexAccess.getCount(); i++) {

								Any any = (Any) xIndexAccess.getByIndex(i);
								XCellRange xCellRange = UnoRuntime.queryInterface(XCellRange.class, any);
								XPropertySet xPropertySet = UnoRuntime.queryInterface(XPropertySet.class, xCellRange);
								xPropertySet.setPropertyValue("CellBackColor", 0xFFFF00);

								for (int ri = 0; true; ri++) {
									boolean riOutBound = false;
									for (int rj = 0; true; rj++) {
										XCell xCell;
										try {
											xCell = xCellRange.getCellByPosition(ri, rj);
										} catch (com.sun.star.lang.IndexOutOfBoundsException e) {
											if (rj == 0)
												riOutBound = true;
											break;
										}

										// CellProtection cellProtection =
										// (CellProtection)xPropertySet.getPropertyValue("CellProtection");
										// cellProtection.IsLocked = false;
										// xPropertySet.setPropertyValue("CellProtection",
										// cellProtection);

										XTextRange textRange = UnoRuntime.queryInterface(XTextRange.class, xCell);
										XTextCursor xTextCursor = textRange.getText().createTextCursor();
										String cellText = textRange.getString().toLowerCase();
										short start = -1, off = 0;
										do {
											off = (short) (start + 1);
											start = (short) cellText.indexOf(term, off);
											if (start != -1) {
												xTextCursor.gotoRange(textRange.getStart(), false);
												xTextCursor.goRight(start, false);
												xTextCursor.goRight((short) term.length(), true);

												xPropertySet = UnoRuntime.queryInterface(XPropertySet.class, xTextCursor);
												if (xPropertySet != null) {
													// for(Property prop :
													// xPropertySet.getPropertySetInfo().getProperties())
													// System.out.println(prop.Name
													// + " " + prop.toString());
													xPropertySet.setPropertyValue("CharColor", 0xFF0000);
													xPropertySet.setPropertyValue("CharWeight", FontWeight.ULTRABOLD);
													xPropertySet.setPropertyValue("CharUnderline", FontUnderline.BOLD);
												}
											}

										} while (start != -1);

										Object[] sheetHit = new Object[2];
										sheetHit[0] = sheet;
										sheetHit[1] = xCell;
										hits.add(sheetHit);
										totalHits++;
										if (totalHits == 1) {
											XSpreadsheetView spreadsheetView = UnoRuntime.queryInterface(XSpreadsheetView.class, officeFrame.getXFrame().getController());
											spreadsheetView.setActiveSheet(sheet);
											XSelectionSupplier xSel = UnoRuntime.queryInterface(XSelectionSupplier.class, spreadsheetView);
											xSel.select(xCell);
											currentHit = 0;
										}

									}
									if (riOutBound)
										break;
								}

							}
					}
				}

			} else if (document instanceof IPresentationDocument)
				for (String term : terms) {
					XDrawPagesSupplier supplier = UnoRuntime.queryInterface(XDrawPagesSupplier.class, document.getXComponent());
					XDrawPages xDrawPages = supplier.getDrawPages();
					int numPages = xDrawPages.getCount();
					for (int k = 0; k < numPages; k++) {

						XDrawPage xDrawPage = UnoRuntime.queryInterface(XDrawPage.class, xDrawPages.getByIndex(k));
						boolean addedPage = false;

						XSearchable xSearchable = UnoRuntime.queryInterface(XSearchable.class, xDrawPage);
						if (xSearchable == null)
							continue;
						XSearchDescriptor xSearchDesc = xSearchable.createSearchDescriptor();
						xSearchDesc.setSearchString(term);
						xSearchDesc.setPropertyValue("SearchCaseSensitive", Boolean.FALSE);
						xSearchDesc.setPropertyValue("SearchWords", Boolean.FALSE);
						xSearchDesc.setPropertyValue("SearchBackwards", Boolean.FALSE);

						XIndexAccess xIndexAccess = xSearchable.findAll(xSearchDesc);

						String preText = "";
						if (xIndexAccess != null)
							for (int i = 0; i < xIndexAccess.getCount(); i++) {
								Proxy any = (Proxy) xIndexAccess.getByIndex(i);

								XTextRange textRange = UnoRuntime.queryInterface(XTextRange.class, any);
								String text = textRange.getText().getString().toLowerCase();
								if (text.equals(preText))
									continue;

								XTextCursor xTextCursor = textRange.getText().createTextCursor();
								short start = -1, off = 0;
								do {
									off = (short) (start + 1);
									start = (short) text.indexOf(term, off);
									if (start != -1) {
										xTextCursor.gotoRange(textRange.getText().getStart(), false);
										xTextCursor.goRight(start, false);
										xTextCursor.goRight((short) term.length(), true);

										XPropertySet xPropertySet = UnoRuntime.queryInterface(XPropertySet.class, xTextCursor);
										if (xPropertySet != null) {
											xPropertySet.setPropertyValue("CharColor", 0xFF0000);
											xPropertySet.setPropertyValue("CharWeight", FontWeight.ULTRABOLD);
											xPropertySet.setPropertyValue("CharUnderline", FontUnderline.BOLD);
										}

									}
								} while (start != -1);

								if (!addedPage) {
									hits.add(xDrawPage);
									totalHits++;
									addedPage = true;
								}

								if (totalHits == 1) {
									XDrawView drawView = UnoRuntime.queryInterface(XDrawView.class, officeFrame.getXFrame().getController());
									drawView.setCurrentPage(xDrawPage);
									currentHit = 0;
								}

								preText = text;
							}
					}

				}

		} catch (Exception e) {
			LOGGER.info("Erro/Interrupção do highlight:");
			// e.printStackTrace();
		}
	}

	@Override
	public void scrollToNextHit(final boolean forward) {

		new Thread() {
			@Override
			public void run() {

				try {

					if (forward) {
						if (currentHit < totalHits - 1)
							if (document instanceof ITextDocument) {
								ITextDocument textDocument = (ITextDocument) document;
								textDocument.getViewCursorService().getViewCursor().goToRange((ITextRange) hits.get(++currentHit), false);

							} else if (document instanceof ISpreadsheetDocument) {
								Object[] sheetHit = (Object[]) hits.get(++currentHit);
								XSpreadsheetView spreadsheetView = UnoRuntime.queryInterface(XSpreadsheetView.class, officeFrame.getXFrame().getController());
								spreadsheetView.setActiveSheet((XSpreadsheet) sheetHit[0]);
								XSelectionSupplier xSel = UnoRuntime.queryInterface(XSelectionSupplier.class, spreadsheetView);
								xSel.select(sheetHit[1]);

							} else if (document instanceof IPresentationDocument) {

								XDrawView drawView = UnoRuntime.queryInterface(XDrawView.class, officeFrame.getXFrame().getController());
								drawView.setCurrentPage((XDrawPage) hits.get(++currentHit));

							}

					} else {
						if (currentHit > 0)
							if (document instanceof ITextDocument) {
								ITextDocument textDocument = (ITextDocument) document;
								textDocument.getViewCursorService().getViewCursor().goToRange((ITextRange) hits.get(--currentHit), false);

							} else if (document instanceof ISpreadsheetDocument) {
								Object[] sheetHit = (Object[]) hits.get(--currentHit);
								XSpreadsheetView spreadsheetView = UnoRuntime.queryInterface(XSpreadsheetView.class, officeFrame.getXFrame().getController());
								spreadsheetView.setActiveSheet((XSpreadsheet) sheetHit[0]);
								XSelectionSupplier xSel = UnoRuntime.queryInterface(XSelectionSupplier.class, spreadsheetView);
								xSel.select(sheetHit[1]);

							} else if (document instanceof IPresentationDocument) {
								XDrawView drawView = UnoRuntime.queryInterface(XDrawView.class, officeFrame.getXFrame().getController());
								drawView.setCurrentPage((XDrawPage) hits.get(--currentHit));

							}

					}

				} catch (Exception e) {
					// e.printStackTrace();
					LOGGER.info("Erro ao rolar para hit");
				}

			}
		}.start();

	}

	/*
	 * private class SearchTimer extends Thread{
	 * 
	 * Thread searchThread; volatile boolean searchEnded = false;
	 * 
	 * @Override public void run() { try { Thread.sleep(2000); if(searchEnded ==
	 * false){ searchThread.interrupt(); }
	 * 
	 * } catch (InterruptedException e) { } } }
	 */

}
