package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.Component;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.Toolkit;
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

import iped3.io.IStreamSource;
import iped3.util.MediaTypes;

/**
 * Classe base para todas as interfaces gráficas de visualizadores.
 *
 * @author Luis Filipe Nassif
 */
public abstract class Viewer {

    private static Logger LOGGER = LoggerFactory.getLogger(Viewer.class);

    private static final long serialVersionUID = 1L;

    private JPanel panel;

    protected int currentHit, totalHits;

    public Viewer() {
        panel = new JPanel();
    }

    public Viewer(LayoutManager layout) {
        panel = new JPanel(layout);
    }

    public JPanel getPanel() {
        return panel;
    }

    abstract public String getName();

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

    /*
     * Método de inicialização do visualizador, possivelmente lenta, para ser
     * chamado fora da thread de eventos.
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

    public void copyScreen() {
        copyScreen(panel);
    }

    protected void copyScreen(Component comp) {
        BufferedImage image = new BufferedImage(comp.getWidth(), comp.getHeight(), BufferedImage.TYPE_INT_RGB);

        comp.paint(image.getGraphics());
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
