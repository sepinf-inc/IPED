package iped.viewers.api;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Set;

import javax.swing.JPanel;

import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.io.IStreamSource;
import iped.properties.MediaTypes;
import iped.viewers.search.HitsUpdater;

/**
 * Classe base para todas as interfaces gráficas de visualizadores.
 *
 * @author Luis Filipe Nassif
 */
public abstract class AbstractViewer {

    private static Logger LOGGER = LoggerFactory.getLogger(AbstractViewer.class);

    protected static final String resPath = "/iped/viewers/res/";

    private JPanel panel;
    
    private Window owner;

    protected int currentHit, totalHits;

    protected boolean isToolbarVisible;

    public AbstractViewer() {
        panel = new JPanel();
    }

    public AbstractViewer(LayoutManager layout) {
        panel = new JPanel(layout);
    }

    public AbstractViewer(Window owner) {
        this.owner = owner; 
        panel = new JPanel();
    }

    public AbstractViewer(Window owner, LayoutManager layout) {
        this.owner = owner; 
        panel = new JPanel(layout);
    }

    public JPanel getPanel() {
        return panel;
    }

    abstract public String getName();
    
    public Window getOwner() {
        return owner;
    }

    /*
     * Retorna se o visualizador suporta o tipo de arquivo informado
     */
    abstract public boolean isSupportedType(String contentType);

    // Testa se o viewer suporta o mimetype ou algum de seus pais
    public boolean isSupportedType(String contentType, boolean testParentTypes) {
        if (!testParentTypes)
            return this.isSupportedType(contentType);

        MediaType type = MediaType.parse(contentType);
        while (type != null) {
            if (this.isSupportedType(type.toString()))
                return true;
            type = getParentType(type);
        }
        return false;

    }

    public MediaType getParentType(MediaType mediaType) {
        return MediaTypes.getParentType(mediaType);
    }

    /**
     * AbstractViewer lazy initialization method. Can be used to start heavy objects or
     * components. Must be called outside EDT to not block the UI.
     */
    abstract public void init();

    /*
     * Libera os recursos utilizados pelo visualizador
     */
    abstract public void dispose();

    /*
     * Renderiza o arquivo. Valor nulo deve indicar limpeza da visualização
     */
    public void loadFile(IStreamSource content, String contentType, Set<String> highlightTerms) {
        loadFile(content, highlightTerms);
    }

    public void loadFile(IStreamSource content) {
        loadFile(content, null);
    }

    abstract public void loadFile(IStreamSource content, Set<String> highlightTerms);

    abstract public void scrollToNextHit(boolean forward);

    public void scrollToNextHit(boolean forward, boolean wrap, HitsUpdater hitsUpdater) {
        // Default behavior ignores wrapping and the updater
        scrollToNextHit(forward, false, null);
    }
    
    /**
     * May be overridden when hits navigation is not supported.
     * 
     * @return -1: no support 0: default, has support, external control when there
     *         are hits 1: has support, always enable as hits control is internal in
     *         the viewer
     */
    public int getHitsSupported() {
        return 0;
    }

    /**
     * May be overridden when the viewer has a tool bar (that may be hidden).
     * 
     * @return -1 (never), 0 (currently no tool bar), 1 (has tool bar)
     */
    public int getToolbarSupported() {
        return -1;
    }

    public void setToolbarVisible(boolean isVisible) {
        this.isToolbarVisible = isVisible;
    }

    public boolean isToolbarVisible() {
        return isToolbarVisible;
    }

    public boolean isSearchSupported() {
        return false;
    }

    public void searchInViewer(String term, HitsUpdater hitsUpdater) {
    }

    public void copyScreen() {
        copyScreen(panel);
    }

    protected void copyScreen(Component comp) {
        BufferedImage image = new BufferedImage(comp.getWidth(), comp.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        comp.paint(g);
        g.dispose();
        TransferableImage trans = new TransferableImage(image);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(trans, trans);
    }

    public class TransferableImage implements Transferable, ClipboardOwner {

        Image i;

        public TransferableImage(Image i) {
            this.i = i;
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (flavor.equals(DataFlavor.imageFlavor) && i != null) {
                return i;
            } else {
                throw new UnsupportedFlavorException(flavor);
            }
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            DataFlavor[] flavors = new DataFlavor[1];
            flavors[0] = DataFlavor.imageFlavor;
            return flavors;
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            DataFlavor[] flavors = getTransferDataFlavors();
            for (int i = 0; i < flavors.length; i++) {
                if (flavor.equals(flavors[i])) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void lostOwnership(Clipboard arg0, Transferable arg1) {
            LOGGER.info("Lost Clipboard Ownership"); //$NON-NLS-1$

        }
    }

}
