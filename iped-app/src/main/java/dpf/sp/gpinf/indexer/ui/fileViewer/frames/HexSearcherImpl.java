package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.JLabel;

import org.exbin.deltahex.highlight.swing.HighlightCodeAreaPainter;
import org.exbin.deltahex.highlight.swing.HighlightCodeAreaPainter.SearchMatch;
import org.exbin.deltahex.swing.CodeArea;

import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.desktop.Messages;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HexViewerPlus.HexSearcher;
import iped3.desktop.CancelableWorker;
import iped3.desktop.ProgressDialog;
import iped3.io.SeekableInputStream;

/**
 *
 * @author guilherme.dutra
 */

public class HexSearcherImpl implements HexSearcher {

    // Realiza busca simples com algoritmo KMP
    public void doSearch(CodeArea codeArea, HighlightCodeAreaPainter painter, Hits hits, SeekableInputStream data,
            Charset charset, Set<String> highlightTerms, long offset, boolean searchString, boolean ignoreCaseSearch,
            JLabel resultSearch, int max_hits) throws Exception {

        GetResultsSearch getSearch = new GetResultsSearch(codeArea, painter, hits, data, charset, highlightTerms,
                offset, searchString, ignoreCaseSearch, resultSearch, max_hits);
        getSearch.execute();

    }

    static class GetResultsSearch extends CancelableWorker<String, Integer> {

        private ProgressDialog progressMonitor;
        private int max_terms = 10000;
        SeekableInputStream data;
        Charset charset;
        Set<String> highlightTerms;
        long offset;
        boolean searchString;
        boolean ignoreCaseSearch;
        App app;
        CodeArea codeArea;
        HighlightCodeAreaPainter painter;
        Hits hits;
        JLabel resultSearch;
        private int max_hits;

        GetResultsSearch(CodeArea codeArea, HighlightCodeAreaPainter painter, Hits hits, SeekableInputStream data,
                Charset charset, Set<String> highlightTerms, long offset, boolean searchString,
                boolean ignoreCaseSearch, JLabel resultSearch, int max_hits) {

            this.codeArea = codeArea;
            this.painter = painter;
            this.hits = hits;
            this.data = data;
            this.charset = charset;
            this.highlightTerms = highlightTerms;
            this.offset = offset;
            this.searchString = searchString;
            this.ignoreCaseSearch = ignoreCaseSearch;
            this.app = App.get();
            this.resultSearch = resultSearch;
            this.max_hits = max_hits;

        }

        @Override
        public void done() {
            if (progressMonitor != null)
                progressMonitor.close();
        }

        @Override
        protected String doInBackground() throws Exception {

            long dataSize = data.size();

            boolean interromper = false;

            // Implementacao do ProgressDialog com duas linhas para mensagens
            // progressMonitor = new ProgressDialog(app, this);

            progressMonitor = new ProgressDialog(app, this, 2);

            List<SearchMatch> hitsEncontrados = new ArrayList();

            long posicao = offset;

            if (progressMonitor != null) {
                progressMonitor.setMaximum(dataSize);
            }

            byte[] bufferTexto = null;

            int maxTermLength = -1;
            int bufferLength = 4 * 1024;

            Set<String> palavras = new HashSet<String>();

            for (String texto : highlightTerms) {
                palavras.add(texto);
            }

            for (String texto : palavras) {
                if (maxTermLength < texto.length())
                    maxTermLength = texto.length();
            }

            if (maxTermLength > bufferLength) {
                bufferLength = maxTermLength;
            }

            byte[] buffer = new byte[bufferLength + maxTermLength];
            int tempLength = 0;

            List<Integer> hitsKMP = null;

            long start = System.currentTimeMillis();
            long seconds = 0;
            long bytesInOneSecond = 0;
            long timePassed = 0;
            String timeLeftString = "";
            long timeLeft = 0;

            SEARCH: while (posicao < dataSize - bufferLength) {

                tempLength = bufferLength + maxTermLength;

                data.seek(posicao);
                int n = data.read(buffer, 0, tempLength);

                for (String texto : palavras) {

                    if (n > 0) {

                        if (searchString) {
                            bufferTexto = texto.getBytes(charset);
                        } else {
                            bufferTexto = hexStringToByteArray(texto);
                        }

                        hitsKMP = searchBytes(bufferTexto, buffer, bufferLength + texto.length() - 1,
                                (searchString && ignoreCaseSearch));

                        for (int off : hitsKMP) {

                            HighlightCodeAreaPainter.SearchMatch match = new HighlightCodeAreaPainter.SearchMatch();
                            match.setPosition(posicao + off);

                            match.setLength((searchString) ? texto.length() : bufferTexto.length);
                            if ((hitsEncontrados.size() != max_terms)) {
                                hitsEncontrados.add(match);
                            }

                            if (hitsEncontrados.size() == max_hits) {
                                interromper = true;
                                break SEARCH;
                            }

                        }
                        hitsKMP = null;
                    }
                }
                posicao += bufferLength;

                seconds = (System.currentTimeMillis() - start);
                bytesInOneSecond += bufferLength;
                if (seconds >= 1000) {
                    timePassed += seconds;
                    timeLeft = (bytesInOneSecond != 0)
                            ? (long) (((double) dataSize - (double) posicao)
                                    / (((double) bytesInOneSecond / 1000) / ((double) seconds / 1000)))
                            : 359999000;
                    timeLeftString = Messages.getString("HexSearcherImpl.timeLeft") + ": " + formatarTempo(timeLeft);
                    bytesInOneSecond = 0;
                    start = System.currentTimeMillis();
                }

                if (progressMonitor != null) {
                    progressMonitor.setProgress(posicao + 1);
                    progressMonitor.setNote("<html><body>" + hitsEncontrados.size() + " "
                            + Messages.getString("HexSearcherImpl.hits") + "<br>" + timeLeftString + "</body></html>");
                    if (progressMonitor.isCanceled()) {
                        interromper = true;
                        break;
                    }
                }
            }

            // Busca no que sobrou do dos dados e passaria do buffer
            tempLength = (int) (dataSize - posicao);

            LEFT: if (!interromper && tempLength > 0) {

                data.seek(posicao);
                int n = data.read(buffer, 0, tempLength);

                if (n > 0) {

                    for (String texto : palavras) {

                        if (searchString) {
                            bufferTexto = texto.getBytes(charset);
                        } else {
                            bufferTexto = hexStringToByteArray(texto);
                        }

                        hitsKMP = searchBytes(bufferTexto, buffer, tempLength, (searchString && ignoreCaseSearch));

                        for (int off : hitsKMP) {

                            HighlightCodeAreaPainter.SearchMatch match = new HighlightCodeAreaPainter.SearchMatch();
                            match.setPosition(posicao + off);
                            match.setLength((searchString) ? texto.length() : bufferTexto.length);
                            if ((hitsEncontrados.size() != max_terms)) {
                                hitsEncontrados.add(match);
                            }
                            if (hitsEncontrados.size() == max_hits) {
                                interromper = true;
                                break LEFT;
                            }

                        }

                        hitsKMP = null;
                    }

                }

            }

            Collections.sort(hitsEncontrados, new SearchMatchComparator());

            painter.clearMatches();
            painter.setMatches(hitsEncontrados);
            hits.totalHits = hitsEncontrados.size();

            if (hits.totalHits > 0) {
                hits.currentHit = 0;
                painter.setCurrentMatchIndex(hits.currentHit);
                HighlightCodeAreaPainter.SearchMatch firstMatch = painter.getCurrentMatch();
                codeArea.revealPosition(firstMatch.getPosition(), codeArea.getActiveSection());
                codeArea.setCaretPosition(firstMatch.getPosition() + firstMatch.getLength());
                resultSearch.setText(Messages.getString("HexSearcherImpl.hit") + " " + (hits.currentHit + 1) + " "
                        + Messages.getString("HexSearcherImpl.of") + " " + hits.totalHits);
            } else {
                resultSearch.setText(Messages.getString("HexSearcherImpl.noHits"));
            }

            codeArea.repaint();

            return null;

        }

        public String formatarTempo(long tempo) {

            tempo /= 1000;
            long t1 = tempo / 3600;
            long t2 = ((tempo % 3600) / 60);
            long t3 = tempo % 60;
            String s1 = (t1 < 10) ? ("0" + t1) : t1 + "";
            String s2 = (t2 < 10) ? ("0" + t2) : t2 + "";
            String s3 = (t3 < 10) ? ("0" + t3) : t3 + "";
            return s1 + ":" + s2 + ":" + s3;

        }

        public void permuteCaseText(String input, Set<String> palavras, Locale locale) {
            int n = input.length();

            // Number of permutations is 2^n
            int max = 1 << n;

            // Converting string to lower case
            input = input.toLowerCase(locale);

            // Using all subsequences and permuting them
            for (int i = 0; i < max; i++) {
                char combination[] = input.toCharArray();

                // If j-th bit is set, we convert it to upper case
                for (int j = 0; j < n; j++) {
                    if (Character.isLetter(combination[j]) && ((i >> j) & 1) == 1)
                        combination[j] = (char) (combination[j] - 32);
                }

                // Printing current combination
                palavras.add(new String(combination));
            }
        }

        public byte[] hexStringToByteArray(String s) {

            s = s.replace(" ", "");

            if (s.length() % 2 != 0)
                s = "0" + s;

            int len = s.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
            }
            return data;
        }
    }

    /**
     * 
     * Source https://gist.github.com/anuvrat/2382245
     * 
     * @author: anuvrat
     * 
     * @guilherme.dutra: modified sentence length and Case Sensitive option
     * 
     */

    /**
     * Searches for all occurances of the word in the sentence. Runs in O(n+k) where
     * n is the word length and k is the sentence length.
     * 
     * @param word
     *            The word that is being searched
     * @param sentence
     *            The collections of word over which the search happens.
     * @param length
     *            The length of the sentence.
     * @param ignoreCaseSearch
     *            Case sensitive search
     * @return The list of starting indices of the matched word in the sentence.
     *         Empty list in case of no match.
     */
    public static List<Integer> searchBytes(final byte[] word, final byte[] sentence, int length,
            boolean ignoreCaseSearch) {
        final List<Integer> matchedIndices = new ArrayList<>();

        // final int sentenceLength = sentence.length;
        final int sentenceLength = length; // buffer length is variable in function of the word
        final int wordLength = word.length;
        int beginMatch = 0; // the starting position in sentence from which the match started
        int idxWord = 0; // the index of the character of the word that is being compared to a character
                         // in string
        final List<Integer> partialTable = createPartialMatchTable(word);
        byte wordChar = 0;
        byte sentenceChar = 0;

        while (beginMatch + idxWord < sentenceLength) {
            if (ignoreCaseSearch) {
                wordChar = (byte) Character.toUpperCase((char) word[idxWord]);
                sentenceChar = (byte) Character.toUpperCase((char) sentence[beginMatch + idxWord]);
            } else {
                wordChar = word[idxWord];
                sentenceChar = sentence[beginMatch + idxWord];
            }
            if (wordChar == sentenceChar) {
                // the characters have matched
                if (idxWord == wordLength - 1) {
                    // the word is complete. we have a match.
                    matchedIndices.add(beginMatch);
                    // restart the search
                    beginMatch = beginMatch + idxWord - partialTable.get(idxWord);
                    if (partialTable.get(idxWord) > -1)
                        idxWord = partialTable.get(idxWord);
                    else
                        idxWord = 0;
                } else
                    idxWord++;
            } else {
                // mismatch. restart the search.
                beginMatch = beginMatch + idxWord - partialTable.get(idxWord);
                if (partialTable.get(idxWord) > -1)
                    idxWord = partialTable.get(idxWord);
                else
                    idxWord = 0;
            }
        }

        return Collections.unmodifiableList(matchedIndices);
    }

    /**
     * Creates the Partial Match Table for the word. Runs in O(n) where n is the
     * length of the word.
     * 
     * @param word
     *            The word whose Partial Match Table is required.
     * @return The table as a list of integers.
     */
    public static List<Integer> createPartialMatchTable(final byte[] word) {
        if (isBlank(word))
            return Collections.EMPTY_LIST;

        final int length = word.length;
        final List<Integer> partialTable = new ArrayList<>(length + 1);
        partialTable.add(-1);
        partialTable.add(0);

        final byte firstChar = word[0];
        for (int idx = 1; idx < word.length; idx++) {
            final int prevVal = partialTable.get(idx);
            if (prevVal == 0) {
                if (word[idx] == firstChar)
                    partialTable.add(1);
                else
                    partialTable.add(0);
            } else if (word[idx] == word[prevVal])
                partialTable.add(prevVal + 1);
            else
                partialTable.add(0);
        }

        return Collections.unmodifiableList(partialTable);
    }

    public static boolean isBlank(final byte[] w) {
        boolean ret = false;
        if (w == null) {
            ret = true;
        } else if (w.length == 0) {
            ret = true;
        }

        return ret;
    }
}

class SearchMatchComparator implements Comparator<HighlightCodeAreaPainter.SearchMatch> {
    @Override
    public int compare(HighlightCodeAreaPainter.SearchMatch o1, HighlightCodeAreaPainter.SearchMatch o2) {
        return Long.valueOf(o1.getPosition()).compareTo(Long.valueOf(o2.getPosition()));
    }
}