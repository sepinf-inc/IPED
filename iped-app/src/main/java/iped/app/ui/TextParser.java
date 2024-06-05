/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.app.ui;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.TreeMap;

import org.apache.commons.io.input.CountingInputStream;
import org.apache.lucene.search.highlight.TextFragment;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.io.ParsingReader;
import iped.engine.task.ParsingTask;
import iped.engine.task.index.IndexItem;
import iped.engine.task.index.IndexTask;
import iped.io.IStreamSource;
import iped.parsers.util.MetadataUtil;
import iped.utils.LocalizedFormat;
import iped.viewers.ATextViewer;
import iped.viewers.api.CancelableWorker;
import iped.viewers.api.IProgressMonitor;
import iped.viewers.api.ITextParser;
import iped.viewers.util.ProgressDialog;

public class TextParser extends CancelableWorker implements ITextParser {

    public static final String TEXT_SIZE = IndexTask.TEXT_SIZE;

    private static TextParser parsingTask;
    private IStreamSource content;
    volatile int id;
    private IItem item;
    private IProgressMonitor progressMonitor;

    private static Object lock = new Object();
    private TemporaryResources tmp;
    private static FileChannel parsedFile;
    private boolean firstHitAutoSelected = false;

    // contém offset, tamanho, viewRow inicial e viewRow final dos fragemtos com
    // sortedHits
    private TreeMap<Long, int[]> sortedHits = new TreeMap<Long, int[]>();

    // contém offset dos hits
    private ArrayList<Long> hits = new ArrayList<Long>();

    // contém offset das quebras de linha do preview
    private ArrayList<Long> viewRows = new ArrayList<Long>();

    public TextParser(IStreamSource content, String contentType, TemporaryResources tmp) {
        try {
            this.content = content;
            this.tmp = tmp;
            if (content instanceof IItem) {
                item = (IItem) content;
            }

            if (parsingTask != null) {
                parsingTask.cancel(true);
            }
            parsingTask = this;

            id = item.getId();

            this.addPropertyChangeListener(new TextParserListener(this));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public FileChannel getParsedFile() {
        return parsedFile;
    }

    @Override
    public void setParsedFile(FileChannel file) {
        parsedFile = file;
    }

    @Override
    public TreeMap<Long, int[]> getSortedHits() {
        return this.sortedHits;
    }

    @Override
    public void setSortedHits(TreeMap<Long, int[]> hits) {
        this.sortedHits = hits;
    }

    @Override
    public ArrayList<Long> getHits() {
        return this.hits;
    }

    @Override
    public void setHits(ArrayList<Long> hits) {
        this.hits = hits;
    }

    @Override
    public ArrayList<Long> getViewRows() {
        return this.viewRows;
    }

    @Override
    public void setViewRows(ArrayList<Long> viewRows) {
        this.viewRows = viewRows;
    }

    @Override
    public IProgressMonitor getProgressMonitor() {
        return this.progressMonitor;
    }

    @Override
    public void setProgressMonitor(IProgressMonitor monitor) {
        this.progressMonitor = monitor;
    }

    @Override
    public boolean getFirstHitAutoSelected() {
        return this.firstHitAutoSelected;
    }

    @Override
    public void setFirstHitAutoSelected(boolean val) {
        this.firstHitAutoSelected = val;
    }

    @Override
    public void done() {

        App.get().hitsDock.setTitleText(LocalizedFormat.format(hits.size()) + Messages.getString("TextParserListener.hits")); //$NON-NLS-1$
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

            progressMonitor = new ProgressDialog(App.get(), parsingTask);
            progressMonitor.setMaximum((Long) item.getExtraAttribute(TEXT_SIZE));

            sortedHits = new TreeMap<Long, int[]>();
            hits = new ArrayList<Long>();
            viewRows = new ArrayList<Long>();
            App.get().getTextViewer().getHitsModel().fireTableDataChanged();
            App.get().getTextViewer().textViewerModel.fireTableDataChanged();

            parseText();
        }

        return null;
    }

    private ParseContext getTikaContext(IItem item) throws Exception {
        ParsingTask expander = new ParsingTask(item, App.get().getAutoParser());
        expander.init(ConfigurationManager.get());
        ParseContext context = expander.getTikaContext(App.get().getLastSelectedSource());
        expander.setExtractEmbedded(false);
        return context;
    }

    private class CountInputStream extends CountingInputStream {

        public CountInputStream(InputStream in) {
            super(in);
        }

        @Override
        protected synchronized void afterRead(final int n) {
            super.afterRead(n);
            progressMonitor.setProgress(this.getByteCount());
        }
    }

    @SuppressWarnings("resource")
    private FileChannel getFileChannel(File tmpFile) throws FileNotFoundException {
        return new RandomAccessFile(tmpFile, "rw").getChannel();
    }

    public void parseText() {
        ParsingReader textReader = null;
        try {

            // this can cause ConcurrentModificationException if another viewer access
            // metadata at same time
            // Metadata metadata = item.getMetadata();
            Metadata metadata = MetadataUtil.clone(item.getMetadata());

            ParsingTask.fillMetadata(item, metadata);

            ParseContext context = getTikaContext(item);
            InputStream is = item.getTikaStream();

            CountInputStream cis = null;
            if (item.getLength() != null && !App.get().getAutoParser().hasSpecificParser(metadata)) {
                progressMonitor.setMaximum(item.getLength());
                cis = new CountInputStream(is);
                is = cis;
            }

            textReader = new ParsingReader((Parser) App.get().getAutoParser(), is, metadata, context);
            textReader.startBackgroundParsing();

            tmp.dispose();
            File tmpFile = tmp.createTemporaryFile();
            parsedFile = getFileChannel(tmpFile);
            tmp.addResource(parsedFile);

            String contents, fieldName = IndexItem.CONTENT;
            int read = 0, lastRowInserted = -1;
            long totalRead = 0, lastNewLinePos = 0;
            boolean lineBreak = false;
            viewRows.add(0L);

            while (!this.isCancelled()) {
                if (read == -1) {
                    break;
                }

                char[] buf = new char[App.TEXT_BREAK_SIZE];
                int off = 0;
                while (!this.isCancelled() && off != buf.length && (read = textReader.read(buf, off, buf.length - off)) != -1) {
                    off += read;
                    totalRead += read;
                    if (cis == null)
                        this.firePropertyChange("progress", 0, totalRead); //$NON-NLS-1$
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

                TextFragment[] fragments = TextHighlighter.getHighlightedFrags(lastRowInserted == -1, contents, fieldName, App.FRAG_SIZE);

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
                    byte data[] = fragment.getBytes(ATextViewer.TEXT_ENCODING);
                    long startPos = parsedFile.position();
                    ByteBuffer out = ByteBuffer.wrap(data);
                    while (out.hasRemaining()) {
                        parsedFile.write(out);
                    }

                    // adiciona linhas adicionais no viewer para cada \n dentro
                    // do fragmento
                    lineBreak = false;
                    int startRow = viewRows.size() - 1;
                    if (viewRows.size() - 1 < ATextViewer.MAX_LINES) {
                        for (int i = 0; i < data.length - 1; i++) {
                            if (data[i] == 0x0A) {
                                viewRows.add(startPos + i + 1);
                                lineBreak = true;
                                if (viewRows.size() - 1 == ATextViewer.MAX_LINES) {
                                    break;
                                }
                                // lastNewLinePos = startPos + i;
                            }
                            /*
                             * else if((startPos + i) - lastNewLinePos >= App.MAX_LINE_SIZE){ int k = i;
                             * while(k >= 0 && Character .isLetterOrDigit(fragment.codePointAt(k))) k--;
                             * lastNewLinePos = startPos + k; App.get().viewRows.add(lastNewLinePos + 1);
                             * if(App.get().viewRows.size() - 1 == App.MAX_LINES) break; }
                             */
                        }
                    }

                    // adiciona hit
                    int numHits = hits.size();
                    if (numHits < App.MAX_HITS && frag.getScore() > 0) {
                        int[] hit = new int[3];
                        hit[0] = data.length;
                        hit[1] = startRow;
                        hit[2] = viewRows.size() - 1;
                        hits.add(startPos);
                        sortedHits.put(startPos, hit);

                        // atualiza viewer permitindo rolar para o hit
                        if (viewRows.size() - 1 < ATextViewer.MAX_LINES) {
                            App.get().getTextViewer().textViewerModel.fireTableRowsInserted(lastRowInserted + 1, viewRows.size() - 2);
                            lastRowInserted = viewRows.size() - 2;
                        } else {
                            int line_disk_size = ATextViewer.MAX_LINE_SIZE * ATextViewer.CHAR_BYTE_COUNT;
                            int line = ATextViewer.MAX_LINES + (int) ((parsedFile.size() - viewRows.get(ATextViewer.MAX_LINES)) / line_disk_size);
                            App.get().getTextViewer().textViewerModel.fireTableRowsInserted(lastRowInserted + 1, line);
                            lastRowInserted = line;
                        }

                        // atualiza lista de hits
                        App.get().getTextViewer().getHitsModel().fireTableRowsInserted(numHits, numHits);
                        this.firePropertyChange("hits", numHits, numHits + 1); //$NON-NLS-1$
                    }

                    // adiciona linha no viewer para o fragmento
                    if (!lineBreak && viewRows.size() - 1 < ATextViewer.MAX_LINES) {
                        viewRows.add(parsedFile.position());
                    }

                }
                // atualiza viewer
                if (viewRows.size() - 1 < ATextViewer.MAX_LINES) {
                    App.get().getTextViewer().textViewerModel.fireTableRowsInserted(lastRowInserted + 1, viewRows.size() - 2);
                    lastRowInserted = viewRows.size() - 2;
                } else {
                    int line_disk_size = ATextViewer.MAX_LINE_SIZE * ATextViewer.CHAR_BYTE_COUNT;
                    int line = ATextViewer.MAX_LINES + (int) ((parsedFile.size() - viewRows.get(ATextViewer.MAX_LINES)) / line_disk_size);
                    App.get().getTextViewer().textViewerModel.fireTableRowsInserted(lastRowInserted + 1, line);
                    lastRowInserted = line;
                }
            }
            if (lineBreak && viewRows.size() - 1 < ATextViewer.MAX_LINES) {
                viewRows.add(parsedFile.size());
                lastRowInserted++;
                App.get().getTextViewer().textViewerModel.fireTableRowsInserted(lastRowInserted, lastRowInserted);
            }

            textReader.close();

        } catch (InterruptedIOException | ClosedByInterruptException e1) {
            // e1.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (this.isCancelled() && textReader != null) {
            textReader.closeAndInterruptParsingTask(false);
        }

    }

}
