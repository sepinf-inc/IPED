package dpf.sp.gpinf.indexer.ui.fileViewer.util;

import dpf.sp.gpinf.indexer.search.FileProcessor;
import dpf.sp.gpinf.indexer.search.HitsTable;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.CompositeViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TextViewer;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;

/**
 * Classe que agrupa os parâmetros da classe principal que serão fornecidos para as classes
 * auxiliares
 *
 * @author Marcelo Silva
 */
public class AppSearchParams {

  public JFrame mainFrame = null;
  public CompositeViewer compositeViewer = null;
  public JDialog dialogBar = null;
  public String codePath = null;
  public Analyzer analyzer = null;
  public Query query = null;
  public String HIGHLIGHT_START_TAG = null;
  public String HIGHLIGHT_END_TAG = null;
  public Object autoParser = null;
  public TextViewer textViewer = null;
  public JTabbedPane tabbedHits = null;
  public HitsTable hitsTable = null;
  public int[] textSizes = null;
  public int[] ids = null;
  public int[] docs = null;
  public int TEXT_BREAK_SIZE = -1;
  public int FRAG_SIZE = -1;
  public int MAX_LINES = -1;
  public int MAX_HITS = -1;
  public int MAX_LINE_SIZE = -1;

}
