package iped.engine.rag;

import static org.junit.Assert.assertEquals;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Test;

import iped.engine.task.index.IndexTask;
import iped.properties.BasicProps;

public class RAGSearcherTest {

    @Test
    public void testFragmentFieldIsScoredWithTheStatisticsOfTheWholeCase() throws Exception {
        try (Directory dir = new ByteBuffersDirectory()) {
            try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
                // regular content of the case: "noah" is rare, "kind" is everywhere
                for (int i = 0; i < 10; i++) {
                    Document doc = new Document();
                    doc.add(new TextField(BasicProps.CONTENT, i == 0 ? "noah kind" : "kind words", Field.Store.NO));
                    writer.addDocument(doc);
                }
                // the embedded fragments: both mention noah
                for (int i = 0; i < 2; i++) {
                    Document doc = new Document();
                    doc.add(new TextField(IndexTask.RAG_CONTENT, "noah deal", Field.Store.NO));
                    writer.addDocument(doc);
                }
            }
            try (DirectoryReader reader = DirectoryReader.open(dir)) {
                RAGService.RAGSearcher searcher = new RAGService.RAGSearcher(reader);
                Term noah = new Term(IndexTask.RAG_CONTENT, "noah");
                TermStatistics stats = searcher.termStatistics(noah, reader.docFreq(noah), reader.totalTermFreq(noah));
                assertEquals(1, stats.docFreq());
                CollectionStatistics collection = searcher.collectionStatistics(IndexTask.RAG_CONTENT);
                assertEquals(10, collection.docCount());

                // a word missing from the regular content keeps the statistics of the fragments
                Term deal = new Term(IndexTask.RAG_CONTENT, "deal");
                assertEquals(2, searcher.termStatistics(deal, reader.docFreq(deal), reader.totalTermFreq(deal)).docFreq());
            }
        }
    }
}
