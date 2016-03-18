package dpf.sp.gpinf.indexer.ui.fileViewer.util;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.TreeMap;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.lucene.search.highlight.TextFragment;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.io.ParsingReader;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.process.task.ParsingTask;
import dpf.sp.gpinf.indexer.search.HitsTableModel;
import dpf.sp.gpinf.indexer.util.CancelableWorker;
import dpf.sp.gpinf.indexer.util.ItemInfoFactory;
import dpf.sp.gpinf.indexer.util.ProgressDialog;
import dpf.sp.gpinf.indexer.util.StreamSource;

public class TextParser extends CancelableWorker {

    private static TextParser parsingTask;
    private StreamSource content;
    private String contentType;
    volatile int id;
    private EvidenceFile item;
    private volatile InputStream is;
    public ProgressDialog progressMonitor;

    private static Object lock = new Object();
    private TemporaryResources tmp;
    public static FileChannel parsedFile;
    public boolean firstHitAutoSelected = false;

    private AppSearchParams appSearchParams = null;

    // contém offset, tamanho, viewRow inicial e viewRow final dos fragemtos com
    // sortedHits
    public TreeMap<Long, int[]> sortedHits = new TreeMap<Long, int[]>();
    
    // contém offset dos hits
    public ArrayList<Long> hits = new ArrayList<Long>();

    // contém offset das quebras de linha do preview
    public ArrayList<Long> viewRows = new ArrayList<Long>();

    public TextParser(AppSearchParams params,
            StreamSource content,
            String contentType,
            TemporaryResources tmp) {

        TextParserListener textParserListener = null;
        this.appSearchParams = params;

        try {
            this.content = content;
            this.contentType = contentType;
            this.tmp = tmp;
            if (content instanceof EvidenceFile) {
                item = (EvidenceFile) content;
            }

            if (parsingTask != null) {
                parsingTask.cancel(true);
            }
            parsingTask = this;

            id = item.getId();

            textParserListener = new TextParserListener(appSearchParams.textViewer, 
                appSearchParams.hitsTable, appSearchParams.tabbedHits);

            this.addPropertyChangeListener(textParserListener);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void done() {
        try {
            if (is != null) {
                is.close();
            }
        } catch (Exception e) {
        }

        appSearchParams.tabbedHits.setTitleAt(0, hits.size() + " Ocorrências");
        if (progressMonitor != null) {
            progressMonitor.close();
        }
    }

    @Override
    public Void doInBackground() {

        synchronized (lock) {

            if (this.isCancelled()) {
                return null;
            }

            progressMonitor = new ProgressDialog(appSearchParams.mainFrame, parsingTask);
            if (appSearchParams.textSizes.length > id) {
                progressMonitor.setMaximum(appSearchParams.textSizes[id] * 1000L);
            }

            sortedHits = new TreeMap<Long, int[]>();
            hits = new ArrayList<Long>();
            viewRows = new ArrayList<Long>();

            ((HitsTableModel) this.appSearchParams.hitsTable.getModel()).fireTableDataChanged();

            appSearchParams.textViewer.textViewerModel.fireTableDataChanged();

            parseText();
        }

        return null;
    }

    private ParseContext getTikaContext() throws Exception {
        ParseContext context = new ParseContext();
        context.set(Parser.class, (Parser) appSearchParams.autoParser);
        context.set(ItemInfo.class, ItemInfoFactory.getItemInfo(item));

        ParsingTask expander = new ParsingTask(context);
        expander.init(Configuration.properties, new File(Configuration.configPath, "conf"));
        context.set(EmbeddedDocumentExtractor.class, expander);

        // Tratamento p/ acentos de subitens de ZIP
        ArchiveStreamFactory factory = new ArchiveStreamFactory();
        factory.setEntryEncoding("Cp850");
        context.set(ArchiveStreamFactory.class, factory);

        context.set(StreamSource.class, content);

        /*PDFParserConfig config = new PDFParserConfig();
         config.setExtractInlineImages(true);
         context.set(PDFParserConfig.class, config);
         */
        return context;
    }

    public void parseText() {
        ParsingReader textReader = null;
        try {

            Metadata metadata = new Metadata();
            metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, contentType);
            metadata.set(Metadata.RESOURCE_NAME_KEY, item.getName());
            Long size = item.getLength() == null ? 1 : item.getLength();
            metadata.set(Metadata.CONTENT_LENGTH, size.toString());
            if (item.isTimedOut()) {
                metadata.set(IndexerDefaultParser.INDEXER_TIMEOUT, "true");
            }

            ParseContext context = getTikaContext();
            is = item.getTikaStream();

            textReader = new ParsingReader((Parser) appSearchParams.autoParser, is, metadata, context);
            textReader.startBackgroundParsing();

            tmp.dispose();
            File tmpFile = tmp.createTemporaryFile();
            parsedFile = new RandomAccessFile(tmpFile, "rw").getChannel();
            tmp.addResource(parsedFile);

            String contents, fieldName = "conteudo";
            int read = 0, lastRowInserted = -1;
            long totalRead = 0, lastNewLinePos = 0;
            boolean lineBreak = false;
            viewRows.add(0L);

            while (!this.isCancelled()) {
                if (read == -1) {
                    break;
                }

                char[] buf = new char[appSearchParams.TEXT_BREAK_SIZE];
                int off = 0;
                while (!this.isCancelled() && off != buf.length && (read = textReader.read(buf, off, buf.length - off)) != -1) {
                    off += read;
                    totalRead += read;
                    this.firePropertyChange("progress", 0, totalRead);
                }

                if (this.isCancelled()) {
                    break;
                }

                contents = new String(buf, 0, off);

                // remove "vazio" do início do texto
                if (lastRowInserted == -1) {
                    int lastIndex = contents.length() - 1;
                    if (lastIndex > 0) {
                        contents = contents.substring(0, lastIndex).trim() + contents.charAt(lastIndex);
                    }
                }

                TextFragment[] fragments = TextHighlighter.getHighlightedFrags(
                        appSearchParams,
                        lastRowInserted == -1,
                        contents,
                        fieldName);

                TreeMap<Integer, TextFragment> sortedFrags = new TreeMap<Integer, TextFragment>();
                for (int i = 0; i < fragments.length; i++) {
                    sortedFrags.put(fragments[i].getFragNum(), fragments[i]);
                }

                if (this.isCancelled()) {
                    break;
                }

                // TODO reduzir código, caracteres nas bordas, codificação, nao
                // juntar linhas
                for (TextFragment frag : sortedFrags.values()) {

                    // grava texto em disco
                    String fragment = frag.toString();
                    byte data[] = fragment.getBytes("windows-1252");
                    long startPos = parsedFile.position();
                    ByteBuffer out = ByteBuffer.wrap(data);
                    while (out.hasRemaining()) {
                        parsedFile.write(out);
                    }

                    // adiciona linhas adicionais no viewer para cada \n dentro
                    // do fragmento
                    lineBreak = false;
                    int startRow = viewRows.size() - 1;
                    if (viewRows.size() - 1 < appSearchParams.MAX_LINES) {
                        for (int i = 0; i < data.length - 1; i++) {
                            if (data[i] == 0x0A) {
                                viewRows.add(startPos + i + 1);
                                lineBreak = true;
                                if (viewRows.size() - 1 == appSearchParams.MAX_LINES) {
                                    break;
                                }
                                // lastNewLinePos = startPos + i;
                            }
                            /*
                             * else if((startPos + i) - lastNewLinePos >=
                             * App.MAX_LINE_SIZE){ int k = i; while(k >= 0 &&
                             * Character
                             * .isLetterOrDigit(fragment.codePointAt(k))) k--;
                             * lastNewLinePos = startPos + k;
                             * App.get().viewRows.add(lastNewLinePos + 1);
                             * if(App.get().viewRows.size() - 1 ==
                             * App.MAX_LINES) break; }
                             */
                        }
                    }

                    // adiciona hit
                    int numHits = hits.size();
                    if (numHits < appSearchParams.MAX_HITS && frag.getScore() > 0) {
                        int[] hit = new int[3];
                        hit[0] = data.length;
                        hit[1] = startRow;
                        hit[2] = viewRows.size() - 1;
                        hits.add(startPos);
                        sortedHits.put(startPos, hit);

                        // atualiza viewer permitindo rolar para o hit
                        if (viewRows.size() - 1 < appSearchParams.MAX_LINES) {
                            appSearchParams.textViewer.textViewerModel.fireTableRowsInserted(lastRowInserted + 1, viewRows.size() - 2);
                            lastRowInserted = viewRows.size() - 2;
                        } else {
                            int line = appSearchParams.MAX_LINES + (int) ((parsedFile.size() - viewRows.get(appSearchParams.MAX_LINES)) / appSearchParams.MAX_LINE_SIZE);
                            appSearchParams.textViewer.textViewerModel.fireTableRowsInserted(lastRowInserted + 1, line);
                            lastRowInserted = line;
                        }

                        // atualiza lista de hits
                        ((HitsTableModel) appSearchParams.hitsTable.getModel()).
                                fireTableRowsInserted(numHits, numHits);
                        this.firePropertyChange("hits", numHits, numHits + 1);
                    }

                    // adiciona linha no viewer para o fragmento
                    if (!lineBreak && viewRows.size() - 1 < appSearchParams.MAX_LINES) {
                        viewRows.add(parsedFile.position());
                    }

                }
                // atualiza viewer
                if (viewRows.size() - 1 < appSearchParams.MAX_LINES) {
                    appSearchParams.textViewer.textViewerModel.fireTableRowsInserted(lastRowInserted + 1, viewRows.size() - 2);
                    lastRowInserted = viewRows.size() - 2;
                } else {
                    int line = appSearchParams.MAX_LINES + (int) ((parsedFile.size() - viewRows.get(appSearchParams.MAX_LINES)) / appSearchParams.MAX_LINE_SIZE);
                    appSearchParams.textViewer.textViewerModel.fireTableRowsInserted(lastRowInserted + 1, line);
                    lastRowInserted = line;
                }
            }
            if (lineBreak && viewRows.size() - 1 < appSearchParams.MAX_LINES) {
                viewRows.add(parsedFile.size());
                lastRowInserted++;
                appSearchParams.textViewer.textViewerModel.fireTableRowsInserted(lastRowInserted, lastRowInserted);
            }

            textReader.reallyClose();

        } catch (InterruptedIOException | ClosedByInterruptException e1) {
            //e1.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (this.isCancelled() && textReader != null) {
            textReader.closeAndInterruptParsingTask();
        }

    }

}
