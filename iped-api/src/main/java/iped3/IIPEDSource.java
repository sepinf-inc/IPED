/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.search.IndexSearcher;
import org.sleuthkit.datamodel.SleuthkitCase;

import iped3.search.IBookmarks;
import iped3.search.IMultiBookmarks;

/**
 *
 * @author WERNECK
 */
public interface IIPEDSource extends Closeable {

    String INDEX_DIR = "index"; //$NON-NLS-1$
    String MODULE_DIR = "indexador"; //$NON-NLS-1$
    String SLEUTH_DB = "sleuth.db"; //$NON-NLS-1$

    void checkImagePaths() throws IOException;

    @Override
    void close();

    Analyzer getAnalyzer();

    LeafReader getLeafReader();

    LeafReader getAtomicReader();

    File getCaseDir();

    List<String> getCategories();

    Set<String> getExtraAttributes();

    int getId(int luceneId);

    File getIndex();

    IItem getItemByID(int id);

    IItem getItemByLuceneID(int docID);

    Set<String> getKeywords();

    int getLastId();

    int getParentId(int id);

    int getLuceneId(IItemId itemId);

    int getLuceneId(int id);

    IBookmarks getMarcadores();

    File getModuleDir();

    IMultiBookmarks getMultiMarcadores();

    IndexReader getReader();

    IndexSearcher getSearcher();

    SleuthkitCase getSleuthCase();

    int getSourceId();

    long getTextSize(int id);

    int getTotalItens();

    Set<String> getEvidenceUUIDs();

    boolean isFTKReport();

    void populateLuceneIdToIdMap() throws IOException;

    void reopen() throws IOException;

    /**
     * Substitui caminhos absolutos para imagens por relativos
     *
     */
    void updateImagePathsToRelative();

}
