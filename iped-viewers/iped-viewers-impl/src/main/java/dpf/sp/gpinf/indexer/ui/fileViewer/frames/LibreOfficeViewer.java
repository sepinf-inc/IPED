package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

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

import ag.ion.bion.officelayer.NativeView;
import ag.ion.bion.officelayer.application.IOfficeApplication;
import ag.ion.bion.officelayer.application.OfficeApplicationException;
import ag.ion.bion.officelayer.application.OfficeApplicationRuntime;
import ag.ion.bion.officelayer.desktop.DesktopException;
import ag.ion.bion.officelayer.desktop.IFrame;
import ag.ion.bion.officelayer.document.DocumentDescriptor;
import ag.ion.bion.officelayer.document.IDocument;
import ag.ion.bion.officelayer.event.IDocumentEvent;
import ag.ion.bion.officelayer.event.IDocumentListener;
import ag.ion.bion.officelayer.event.IEvent;
import ag.ion.bion.officelayer.presentation.IPresentationDocument;
import ag.ion.bion.officelayer.spreadsheet.ISpreadsheetDocument;
import ag.ion.bion.officelayer.text.ITextDocument;
import ag.ion.bion.officelayer.text.ITextRange;
import ag.ion.noa.search.ISearchResult;
import ag.ion.noa.search.SearchDescriptor;
import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ProcessUtil;

import iped3.io.IStreamSource;

public class LibreOfficeViewer extends Viewer {

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

    private static String userProfileBase = "$SYSUSERCONFIG/.indexador/libreoffice6/profile"; //$NON-NLS-1$
    private static String RESTART_MSG = Messages.getString("LibreOfficeViewer.RestartingViewer"); //$NON-NLS-1$
    private static int XLS_LENGTH_TO_COPY = 1000000;

    @Override
    public boolean isSupportedType(String contentType) {

        return contentType.startsWith("application/msword") //$NON-NLS-1$
                || contentType.equals("application/rtf") //$NON-NLS-1$
                || contentType.startsWith("application/vnd.ms-word") //$NON-NLS-1$
                || contentType.startsWith("application/vnd.ms-powerpoint") //$NON-NLS-1$
                || contentType.startsWith("application/vnd.openxmlformats-officedocument") //$NON-NLS-1$
                || contentType.startsWith("application/vnd.oasis.opendocument") //$NON-NLS-1$
                || contentType.startsWith("application/vnd.sun.xml") //$NON-NLS-1$
                || contentType.startsWith("application/vnd.stardivision") //$NON-NLS-1$
                // || contentType.startsWith("image/")
                // contentType.startsWith("application/vnd.ms-works") ||
                // contentType.startsWith("application/x-tika-msoffice") ||
                // || contentType.startsWith("application/x-tika-ooxml")
                || contentType.startsWith("application/x-tika-ooxml-protected") //$NON-NLS-1$
                || contentType.equals("application/vnd.visio") //$NON-NLS-1$
                || contentType.equals("application/x-mspublisher") //$NON-NLS-1$
                || contentType.equals("application/postscript") //$NON-NLS-1$
                || contentType.equals("application/x-dbf") //$NON-NLS-1$
                || contentType.equals("text/csv") //$NON-NLS-1$
                || contentType.equals("image/emf") //$NON-NLS-1$
                || contentType.equals("image/wmf") //$NON-NLS-1$
                || contentType.equals("image/vnd.adobe.photoshop") //$NON-NLS-1$
                || contentType.equals("image/x-portable-bitmap") //$NON-NLS-1$
                || contentType.equals("image/svg+xml") //$NON-NLS-1$
                || contentType.equals("image/x-pcx") //$NON-NLS-1$
                || contentType.equals("image/vnd.dxf") //$NON-NLS-1$
                || contentType.equals("image/cdr") //$NON-NLS-1$
                || contentType.equals("application/coreldraw") //$NON-NLS-1$
                || contentType.equals("application/x-vnd.corel.zcf.draw.document+zip") //$NON-NLS-1$
                || isSpreadSheet(contentType);

    }

    public boolean isSpreadSheet(String contentType) {
        return contentType.startsWith("application/vnd.ms-excel") //$NON-NLS-1$
                || contentType.startsWith("application/x-tika-msworks-spreadsheet") //$NON-NLS-1$
                || contentType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml") //$NON-NLS-1$
                || contentType.startsWith("application/vnd.oasis.opendocument.spreadsheet"); //$NON-NLS-1$

    }

    @Override
    public String getName() {
        return "Office"; //$NON-NLS-1$
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
        startLO();
        constructLOFrame();
        edtMonitor = monitorEventThreadBlocking();

        try {
            blankDoc = File.createTempFile("indexador", ".doc"); //$NON-NLS-1$ //$NON-NLS-2$
            blankDoc.deleteOnExit();
            document = officeApplication.getDocumentService().constructNewHiddenDocument(IDocument.WRITER);
            document.getPersistenceService().store(blankDoc.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private String getUserProfile() {
        String profile = userProfileBase + "-" + System.getProperty("user.name").replaceAll("\\W", "");
        return profile;
    }

    private void startLO() {

        try {
            HashMap<String, Object> configuration = new HashMap<>();
            // if(System.getProperty("os.name").startsWith("Windows"))
            configuration.put(IOfficeApplication.APPLICATION_HOME_KEY, pathLO);
            /*
             * else{ IApplicationAssistant ass = new ApplicationAssistant(libPath + "/lib");
             * ILazyApplicationInfo[] ila = ass.getLocalApplications(); if(ila.length > 0)
             * configuration.put(IOfficeApplication.APPLICATION_HOME_KEY, ila[0].getHome());
             * }
             */

            configuration.put(IOfficeApplication.APPLICATION_TYPE_KEY, IOfficeApplication.LOCAL_APPLICATION);

            ArrayList<String> options = new ArrayList<String>();
            options.add("-env:UserInstallation=" + getUserProfile()); //$NON-NLS-1$
            String prefix = ""; //$NON-NLS-1$
            if (pathLO.toLowerCase().contains("libre")) { //$NON-NLS-1$
                prefix = "-"; //$NON-NLS-1$
            }
            options.add(prefix + "-invisible"); //$NON-NLS-1$
            options.add(prefix + "-nologo"); //$NON-NLS-1$
            options.add(prefix + "-nodefault"); //$NON-NLS-1$
            options.add(prefix + "-norestore"); //$NON-NLS-1$
            options.add(prefix + "-nocrashreport"); //$NON-NLS-1$
            options.add(prefix + "-nolockcheck"); //$NON-NLS-1$

            configuration.put(IOfficeApplication.APPLICATION_ARGUMENTS_KEY, options.toArray(new String[0]));

            officeApplication = OfficeApplicationRuntime.getApplication(configuration);
            officeApplication.setConfiguration(configuration);
            officeApplication.activate();
            officeApplication.getDesktopService().addDocumentListener(new DocumentListener());

            LOGGER.info("LibreOffice running."); //$NON-NLS-1$

        } catch (Exception e1) {
            String msg = e1.toString().toLowerCase();
            if (msg.contains(".dll") && msg.contains("32-bit") && msg.contains("64-bit")
                    && !System.getProperty("os.arch").contains("64"))
                throw new NotSupported32BitPlatformExcepion();

            e1.printStackTrace();
        }

    }

    public static class NotSupported32BitPlatformExcepion extends RuntimeException {

        private static final long serialVersionUID = 1L;

    }

    public void constructLOFrame() {
        try {
            if (!System.getProperty("os.name").startsWith("Windows")
                    && !"gtk".equals(System.getenv("SAL_USE_VCLPLUGIN")))
                LOGGER.error(
                        "LibreOffice viewer may not work properly. Install libreoffice-gtk2 and set environment var SAL_USE_VCLPLUGIN='gtk'");

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    LOGGER.info("Constructing LibreOffice frame..."); //$NON-NLS-1$
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
                        LOGGER.info("LibreOffice frame ok"); //$NON-NLS-1$

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
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {
        loadFile(content, "", highlightTerms); //$NON-NLS-1$
    }

    @Override
    public void loadFile(final IStreamSource content, final String contentType, final Set<String> highlightTerms) {

        final File file = content != null ? content.getFile() : null;
        lastFile = file;

        new Thread() {
            @Override
            public void run() {

                synchronized (startLOLock) {
                    /**
                     * Código de proteção/reinicialização para casos de documentos com carregamento
                     * demorado ou infinito, utilizado quando usuário desiste de esperar e clica em
                     * outro documento.
                     */
                    if (loading && (lastFile == file)) {
                        loading = false;
                        restartLO();
                    }
                }

                if (file != lastFile) {
                    return;
                }

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

                        document = officeApplication.getDocumentService().loadDocument(officeFrame,
                                lastFile.getAbsolutePath(), descriptor);
                        ajustLayout();

                    } else {
                        boolean isVisible = noaPanel.isVisible();
                        setNoaPanelVisible(false);
                        if (isVisible) {
                            document = officeApplication.getDocumentService().loadDocument(officeFrame,
                                    blankDoc.getAbsolutePath(), descriptor);
                            ajustLayout();
                        }

                    }

                    loading = false;

                    if (file != null && highlightTerms != null) {
                        highlightText(highlightTerms);
                    }

                } catch (Throwable e) {

                    loading = false;

                    LOGGER.info(e.toString());
                    // System.out.println("exception!");
                    // e.printStackTrace();

                    if (e.toString().contains("Document not found")) { //$NON-NLS-1$
                        setNoaPanelVisible(false);

                    } else if (!restartCalled && file == lastFile) {
                        synchronized (startLOLock) {
                            restartLO();
                        }
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
                        LOGGER.info("UI freeze detected! Restarting viewer..."); //$NON-NLS-1$
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
        LOGGER.info("Restarting LibreOffice..."); //$NON-NLS-1$
        restartCalled = true;

        ProcessUtil.killProcess("soffice.bin", "--accept=pipe");
        /*
         * try { officeApplication.dispose();
         * officeApplication.getDesktopService().terminate();
         * 
         * } catch (NOAException | OfficeApplicationException e1) {
         * e1.printStackTrace(); }
         */
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

        LOGGER.info("LibreOffice restarted."); //$NON-NLS-1$
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
        if (lastFile.getName().toLowerCase().contains(".pps")) { //$NON-NLS-1$
            copyToTempFile(".ppt"); //$NON-NLS-1$
        }
    }

    private void copySpreadsheetToHighlight() {
        copyToTempFile(".tmp"); //$NON-NLS-1$
    }

    private void copyToTempFile(String ext) {
        if (tempFile != lastFile) {
            try {
                if (tempFile != null) {
                    tempFile.delete();
                }
                tempFile = File.createTempFile("indexador-", ext); //$NON-NLS-1$
                tempFile.deleteOnExit();
                IOUtil.copiaArquivo(lastFile, tempFile);
                lastFile = tempFile;

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void ajustLayout() {
        if (document != null) {
            try {
                // officeFrame.getLayoutManager().showElement(LayoutManager.URL_STATUSBAR);
                officeFrame.getLayoutManager().hideAll();

                if (document instanceof ITextDocument) {
                    ((ITextDocument) document).zoom(DocumentZoomType.PAGE_WIDTH, (short) 100);
                }

                if (document instanceof IPresentationDocument) {
                    ((IPresentationDocument) document).getPresentationSupplier().getPresentation().end();
                }

            } catch (Exception e) {
                // e.printStackTrace();
            }
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
        if (edtMonitor != null)
            edtMonitor.interrupt();
        if (officeApplication != null) {
            try {
                // officeApplication.dispose();
                // officeApplication.getDesktopService().terminate();
                ProcessUtil.killProcess("soffice.bin", "--accept=pipe");

            } catch (Exception e1) {
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

            xGraphics.draw(xDisplayBitmap, 0, 0, aSize.Width, aSize.Height, 0, 0, this.getPanel().getWidth(),
                    this.getPanel().getHeight());

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
        if (terms.size() == 0) {
            return;
        }

        try {
            if (document instanceof ITextDocument) {
                ITextDocument textDocument = (ITextDocument) document;

                for (String term : terms) {
                    SearchDescriptor searchDescriptor = new SearchDescriptor(term);
                    searchDescriptor.setIsCaseSensitive(false);
                    searchDescriptor.setUseCompleteWords(false);
                    ISearchResult searchResult = ((ITextDocument) document).getSearchService()
                            .findAll(searchDescriptor);
                    ITextRange[] textRanges = searchResult.getTextRanges();
                    for (ITextRange range : textRanges) {
                        if (range != null) {
                            XPropertySet xPropertySet = UnoRuntime.queryInterface(XPropertySet.class,
                                    range.getXTextRange());
                            xPropertySet.setPropertyValue("CharBackColor", 0xFFFF00); //$NON-NLS-1$
                            xPropertySet.setPropertyValue("CharColor", 0x000000); //$NON-NLS-1$
                            hits.add(range);
                            totalHits++;
                            if (totalHits == 1) {
                                textDocument.getViewCursorService().getViewCursor().goToRange(range, false);
                                currentHit = 0;
                            }
                        }
                    }
                }

            } else if (document instanceof ISpreadsheetDocument) {
                for (String term : terms) {
                    ISpreadsheetDocument spreadsheetDocument = (ISpreadsheetDocument) document;
                    XSpreadsheets spreadsheets = spreadsheetDocument.getSpreadsheetDocument().getSheets();
                    for (String sheetName : spreadsheets.getElementNames()) {
                        XSpreadsheet sheet = UnoRuntime.queryInterface(XSpreadsheet.class,
                                spreadsheets.getByName(sheetName));
                        XProtectable protectable = UnoRuntime.queryInterface(XProtectable.class, sheet);
                        if (protectable.isProtected()) {
                            LOGGER.info("Protected sheet: {}", sheetName); //$NON-NLS-1$
                        }
                        // protectable.unprotect("");
                        XSearchable xSearchable = UnoRuntime.queryInterface(XSearchable.class, sheet);
                        XSearchDescriptor xSearchDesc = xSearchable.createSearchDescriptor();
                        xSearchDesc.setSearchString(term);
                        xSearchDesc.setPropertyValue("SearchCaseSensitive", Boolean.FALSE); //$NON-NLS-1$
                        xSearchDesc.setPropertyValue("SearchWords", Boolean.FALSE); //$NON-NLS-1$

                        XIndexAccess xIndexAccess = xSearchable.findAll(xSearchDesc);
                        if (xIndexAccess != null) {
                            for (int i = 0; i < xIndexAccess.getCount(); i++) {

                                Any any = (Any) xIndexAccess.getByIndex(i);
                                XCellRange xCellRange = UnoRuntime.queryInterface(XCellRange.class, any);
                                XPropertySet xPropertySet = UnoRuntime.queryInterface(XPropertySet.class, xCellRange);
                                xPropertySet.setPropertyValue("CellBackColor", 0xFFFF00); //$NON-NLS-1$

                                for (int ri = 0; true; ri++) {
                                    boolean riOutBound = false;
                                    for (int rj = 0; true; rj++) {
                                        XCell xCell;
                                        try {
                                            xCell = xCellRange.getCellByPosition(ri, rj);
                                        } catch (com.sun.star.lang.IndexOutOfBoundsException e) {
                                            if (rj == 0) {
                                                riOutBound = true;
                                            }
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

                                                xPropertySet = UnoRuntime.queryInterface(XPropertySet.class,
                                                        xTextCursor);
                                                if (xPropertySet != null) {
                                                    // for(Property prop :
                                                    // xPropertySet.getPropertySetInfo().getProperties())
                                                    // System.out.println(prop.Name
                                                    // + " " + prop.toString());
                                                    xPropertySet.setPropertyValue("CharColor", 0xFF0000); //$NON-NLS-1$
                                                    xPropertySet.setPropertyValue("CharWeight", FontWeight.ULTRABOLD); //$NON-NLS-1$
                                                    xPropertySet.setPropertyValue("CharUnderline", FontUnderline.BOLD); //$NON-NLS-1$
                                                }
                                            }

                                        } while (start != -1);

                                        Object[] sheetHit = new Object[2];
                                        sheetHit[0] = sheet;
                                        sheetHit[1] = xCell;
                                        hits.add(sheetHit);
                                        totalHits++;
                                        if (totalHits == 1) {
                                            XSpreadsheetView spreadsheetView = UnoRuntime.queryInterface(
                                                    XSpreadsheetView.class, officeFrame.getXFrame().getController());
                                            spreadsheetView.setActiveSheet(sheet);
                                            XSelectionSupplier xSel = UnoRuntime
                                                    .queryInterface(XSelectionSupplier.class, spreadsheetView);
                                            xSel.select(xCell);
                                            currentHit = 0;
                                        }

                                    }
                                    if (riOutBound) {
                                        break;
                                    }
                                }

                            }
                        }
                    }
                }

            } else if (document instanceof IPresentationDocument) {
                for (String term : terms) {
                    XDrawPagesSupplier supplier = UnoRuntime.queryInterface(XDrawPagesSupplier.class,
                            document.getXComponent());
                    XDrawPages xDrawPages = supplier.getDrawPages();
                    int numPages = xDrawPages.getCount();
                    for (int k = 0; k < numPages; k++) {

                        XDrawPage xDrawPage = UnoRuntime.queryInterface(XDrawPage.class, xDrawPages.getByIndex(k));
                        boolean addedPage = false;

                        XSearchable xSearchable = UnoRuntime.queryInterface(XSearchable.class, xDrawPage);
                        if (xSearchable == null) {
                            continue;
                        }
                        XSearchDescriptor xSearchDesc = xSearchable.createSearchDescriptor();
                        xSearchDesc.setSearchString(term);
                        xSearchDesc.setPropertyValue("SearchCaseSensitive", Boolean.FALSE); //$NON-NLS-1$
                        xSearchDesc.setPropertyValue("SearchWords", Boolean.FALSE); //$NON-NLS-1$
                        xSearchDesc.setPropertyValue("SearchBackwards", Boolean.FALSE); //$NON-NLS-1$

                        XIndexAccess xIndexAccess = xSearchable.findAll(xSearchDesc);

                        String preText = ""; //$NON-NLS-1$
                        if (xIndexAccess != null) {
                            for (int i = 0; i < xIndexAccess.getCount(); i++) {
                                Proxy any = (Proxy) xIndexAccess.getByIndex(i);

                                XTextRange textRange = UnoRuntime.queryInterface(XTextRange.class, any);
                                String text = textRange.getText().getString().toLowerCase();
                                if (text.equals(preText)) {
                                    continue;
                                }

                                XTextCursor xTextCursor = textRange.getText().createTextCursor();
                                short start = -1, off = 0;
                                do {
                                    off = (short) (start + 1);
                                    start = (short) text.indexOf(term, off);
                                    if (start != -1) {
                                        xTextCursor.gotoRange(textRange.getText().getStart(), false);
                                        xTextCursor.goRight(start, false);
                                        xTextCursor.goRight((short) term.length(), true);

                                        XPropertySet xPropertySet = UnoRuntime.queryInterface(XPropertySet.class,
                                                xTextCursor);
                                        if (xPropertySet != null) {
                                            xPropertySet.setPropertyValue("CharColor", 0xFF0000); //$NON-NLS-1$
                                            xPropertySet.setPropertyValue("CharWeight", FontWeight.ULTRABOLD); //$NON-NLS-1$
                                            xPropertySet.setPropertyValue("CharUnderline", FontUnderline.BOLD); //$NON-NLS-1$
                                        }

                                    }
                                } while (start != -1);

                                if (!addedPage) {
                                    hits.add(xDrawPage);
                                    totalHits++;
                                    addedPage = true;
                                }

                                if (totalHits == 1) {
                                    XDrawView drawView = UnoRuntime.queryInterface(XDrawView.class,
                                            officeFrame.getXFrame().getController());
                                    drawView.setCurrentPage(xDrawPage);
                                    currentHit = 0;
                                }

                                preText = text;
                            }
                        }
                    }

                }
            }

        } catch (Exception e) {
            LOGGER.info("Error/Highlight interrupted"); //$NON-NLS-1$
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
                        if (currentHit < totalHits - 1) {
                            if (document instanceof ITextDocument) {
                                ITextDocument textDocument = (ITextDocument) document;
                                textDocument.getViewCursorService().getViewCursor()
                                        .goToRange((ITextRange) hits.get(++currentHit), false);

                            } else if (document instanceof ISpreadsheetDocument) {
                                Object[] sheetHit = (Object[]) hits.get(++currentHit);
                                XSpreadsheetView spreadsheetView = UnoRuntime.queryInterface(XSpreadsheetView.class,
                                        officeFrame.getXFrame().getController());
                                spreadsheetView.setActiveSheet((XSpreadsheet) sheetHit[0]);
                                XSelectionSupplier xSel = UnoRuntime.queryInterface(XSelectionSupplier.class,
                                        spreadsheetView);
                                xSel.select(sheetHit[1]);

                            } else if (document instanceof IPresentationDocument) {

                                XDrawView drawView = UnoRuntime.queryInterface(XDrawView.class,
                                        officeFrame.getXFrame().getController());
                                drawView.setCurrentPage((XDrawPage) hits.get(++currentHit));

                            }
                        }

                    } else {
                        if (currentHit > 0) {
                            if (document instanceof ITextDocument) {
                                ITextDocument textDocument = (ITextDocument) document;
                                textDocument.getViewCursorService().getViewCursor()
                                        .goToRange((ITextRange) hits.get(--currentHit), false);

                            } else if (document instanceof ISpreadsheetDocument) {
                                Object[] sheetHit = (Object[]) hits.get(--currentHit);
                                XSpreadsheetView spreadsheetView = UnoRuntime.queryInterface(XSpreadsheetView.class,
                                        officeFrame.getXFrame().getController());
                                spreadsheetView.setActiveSheet((XSpreadsheet) sheetHit[0]);
                                XSelectionSupplier xSel = UnoRuntime.queryInterface(XSelectionSupplier.class,
                                        spreadsheetView);
                                xSel.select(sheetHit[1]);

                            } else if (document instanceof IPresentationDocument) {
                                XDrawView drawView = UnoRuntime.queryInterface(XDrawView.class,
                                        officeFrame.getXFrame().getController());
                                drawView.setCurrentPage((XDrawPage) hits.get(--currentHit));

                            }
                        }

                    }

                } catch (Exception e) {
                    // e.printStackTrace();
                    LOGGER.info("Error scrolling to hit"); //$NON-NLS-1$
                }

            }
        }.start();

    }

    private class DocumentListener implements IDocumentListener {

        @Override
        public void disposing(IEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onAlphaCharInput(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onFocus(IDocumentEvent arg0) {
            releaseFocus();
        }

        @Override
        public void onInsertDone(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onInsertStart(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onLoad(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onLoadDone(IDocumentEvent arg0) {
            // TODO Auto-generated method stub
        }

        @Override
        public void onLoadFinished(IDocumentEvent arg0) {
            // TODO Auto-generated method stub
        }

        @Override
        public void onModifyChanged(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onMouseOut(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onMouseOver(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onNew(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onNonAlphaCharInput(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onSave(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onSaveAs(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onSaveAsDone(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onSaveDone(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onSaveFinished(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onUnload(IDocumentEvent arg0) {
            // TODO Auto-generated method stub

        }

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
