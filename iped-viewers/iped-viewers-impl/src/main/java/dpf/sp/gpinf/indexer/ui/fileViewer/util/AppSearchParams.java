package dpf.sp.gpinf.indexer.ui.fileViewer.util;

import dpf.sp.gpinf.indexer.ui.hitsViewer.HitsTable;
import iped3.IIPEDSource;

import java.util.Set;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;

/**
 * Classe que agrupa os parâmetros da classe principal que serão fornecidos para
 * as classes auxiliares
 *
 * @author Marcelo Silva
 */
public class AppSearchParams {

    //TODO:Remove before final commit //public IViewerControl viewerControl;
    public JFrame mainFrame = null;
    //TODO:Remove before final commit //public CompositeViewer compositeViewer = null;
    public JDialog dialogBar = null;
    public String codePath = null;
    public Analyzer analyzer = null;
    public Query query = null;
    public String HIGHLIGHT_START_TAG = null;
    public String HIGHLIGHT_END_TAG = null;
    public Object autoParser = null;
    //TODO:Remove before final commit //public ATextViewer textViewer = null;
    public JTabbedPane tabbedHits = null;
    public HitsTable hitsTable = null;
    public JScrollPane subItemScroll;
    public JScrollPane parentItemScroll;
    //TODO:Remove before final commit //public HitsTableModel hitsModel = new HitsTableModel(this);
    public int TEXT_BREAK_SIZE = -1;
    public int FRAG_SIZE = -1;
    public int MAX_LINES = -1;
    public int MAX_HITS = -1;
    public int MAX_LINE_SIZE = -1;
    public int lastSelectedDoc;
    public IIPEDSource lastSelectedSource;
    public JLabel status;
    public Set<String> highlightTerms;
}
