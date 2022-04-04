package dpf.sp.gpinf.indexer.ui.fileViewer.util;

import java.util.Set;

import org.apache.lucene.search.Query;

/**
 * Classe que agrupa os parâmetros da classe principal que serão fornecidos para
 * as classes auxiliares
 *
 * @author Marcelo Silva
 */
public class AppSearchParams {

    public Query query = null;
    public String FONT_START_TAG = null;
    public Object autoParser = null;
    public int lastSelectedDoc;
    public Set<String> highlightTerms;
}
