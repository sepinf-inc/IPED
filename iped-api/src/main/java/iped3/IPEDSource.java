/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3;

import iped3.exception.IPEDException;
import iped3.search.Marcadores;
import iped3.search.MultiMarcadores;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author WERNECK
 */
public interface IPEDSource extends Closeable {

    String INDEX_DIR = "index"; //$NON-NLS-1$
    String MODULE_DIR = "indexador"; //$NON-NLS-1$
    String SLEUTH_DB = "sleuth.db"; //$NON-NLS-1$

    void checkImagePaths() throws IPEDException, TskCoreException;

    @Override
    void close();

    Analyzer getAnalyzer();

    AtomicReader getAtomicReader();

    File getCaseDir();

    List<String> getCategories();

    Set<String> getExtraAttributes();

    int getId(int luceneId);

    File getIndex();

    Item getItemByID(int id);

    Item getItemByLuceneID(int docID);

    Set<String> getKeywords();

    int getLastId();

    int getLuceneId(ItemId itemId);

    int getLuceneId(int id);

    Marcadores getMarcadores();

    File getModuleDir();

    MultiMarcadores getMultiMarcadores();

    IndexReader getReader();

    IndexSearcher getSearcher();

    SleuthkitCase getSleuthCase();

    int getSourceId();

    long getTextSize(int id);

    int getTotalItens();

    VersionsMap getViewToRawMap();

    boolean isFTKReport();

    void populateLuceneIdToIdMap() throws IOException;

    void reopen() throws IOException;

    /**
     * Substitui caminhos absolutos para imagens por relativos
     *
     */
    void updateImagePathsToRelative();

}
