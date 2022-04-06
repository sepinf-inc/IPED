package dpf.sp.gpinf.indexer.lucene.analysis;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.RamUsageEstimator;

/**
 * This class converts alphabetic, numeric, and symbolic Unicode characters
 * which are not in the first 127 ASCII characters (the "Basic Latin" Unicode
 * block) into their ASCII equivalents, if one exists.
 *
 * Characters from the following Unicode blocks are converted; however, only
 * those characters with reasonable ASCII alternatives are converted:
 *
 * <ul>
 * <li>C1 Controls and Latin-1 Supplement: <a href=
 * "http://www.unicode.org/charts/PDF/U0080.pdf">http://www.unicode.org/charts/PDF/U0080.pdf</a>
 * <li>Latin Extended-A: <a href=
 * "http://www.unicode.org/charts/PDF/U0100.pdf">http://www.unicode.org/charts/PDF/U0100.pdf</a>
 * <li>Latin Extended-B: <a href=
 * "http://www.unicode.org/charts/PDF/U0180.pdf">http://www.unicode.org/charts/PDF/U0180.pdf</a>
 * <li>Latin Extended Additional: <a href=
 * "http://www.unicode.org/charts/PDF/U1E00.pdf">http://www.unicode.org/charts/PDF/U1E00.pdf</a>
 * <li>Latin Extended-C: <a href=
 * "http://www.unicode.org/charts/PDF/U2C60.pdf">http://www.unicode.org/charts/PDF/U2C60.pdf</a>
 * <li>Latin Extended-D: <a href=
 * "http://www.unicode.org/charts/PDF/UA720.pdf">http://www.unicode.org/charts/PDF/UA720.pdf</a>
 * <li>IPA Extensions: <a href=
 * "http://www.unicode.org/charts/PDF/U0250.pdf">http://www.unicode.org/charts/PDF/U0250.pdf</a>
 * <li>Phonetic Extensions: <a href=
 * "http://www.unicode.org/charts/PDF/U1D00.pdf">http://www.unicode.org/charts/PDF/U1D00.pdf</a>
 * <li>Phonetic Extensions Supplement: <a href=
 * "http://www.unicode.org/charts/PDF/U1D80.pdf">http://www.unicode.org/charts/PDF/U1D80.pdf</a>
 * <li>General Punctuation: <a href=
 * "http://www.unicode.org/charts/PDF/U2000.pdf">http://www.unicode.org/charts/PDF/U2000.pdf</a>
 * <li>Superscripts and Subscripts: <a href=
 * "http://www.unicode.org/charts/PDF/U2070.pdf">http://www.unicode.org/charts/PDF/U2070.pdf</a>
 * <li>Enclosed Alphanumerics: <a href=
 * "http://www.unicode.org/charts/PDF/U2460.pdf">http://www.unicode.org/charts/PDF/U2460.pdf</a>
 * <li>Dingbats: <a href=
 * "http://www.unicode.org/charts/PDF/U2700.pdf">http://www.unicode.org/charts/PDF/U2700.pdf</a>
 * <li>Supplemental Punctuation: <a href=
 * "http://www.unicode.org/charts/PDF/U2E00.pdf">http://www.unicode.org/charts/PDF/U2E00.pdf</a>
 * <li>Alphabetic Presentation Forms: <a href=
 * "http://www.unicode.org/charts/PDF/UFB00.pdf">http://www.unicode.org/charts/PDF/UFB00.pdf</a>
 * <li>Halfwidth and Fullwidth Forms: <a href=
 * "http://www.unicode.org/charts/PDF/UFF00.pdf">http://www.unicode.org/charts/PDF/UFF00.pdf</a>
 * </ul>
 *
 * See: <a href=
 * "http://en.wikipedia.org/wiki/Latin_characters_in_Unicode">http://en.wikipedia.org/wiki/Latin_characters_in_Unicode</a>
 *
 * For example, '&agrave;' will be replaced by 'a'.
 */
public final class FastASCIIFoldingFilter extends TokenFilter {

    private static final char[][] map = initializeMap();

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncAttr = addAttribute(PositionIncrementAttribute.class);
    private final boolean preserveOriginal;
    private char[] output = new char[512];
    private int outputPos;
    private State state;

    public FastASCIIFoldingFilter(TokenStream input) {
        this(input, false);
    }

    /**
     * Create a new {@link FastASCIIFoldingFilter}.
     *
     * @param input
     *            TokenStream to filter
     * @param preserveOriginal
     *            should the original tokens be kept on the input stream with a 0
     *            position increment from the folded tokens?
     *
     */
    public FastASCIIFoldingFilter(TokenStream input, boolean preserveOriginal) {
        super(input);
        this.preserveOriginal = preserveOriginal;
    }

    /**
     * @return true if the filter preserve the original tokens
     */
    public boolean isPreserveOriginal() {
        return preserveOriginal;
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (state != null) {
            assert preserveOriginal : "state should only be captured if preserveOriginal is true"; //$NON-NLS-1$
            restoreState(state);
            posIncAttr.setPositionIncrement(0);
            state = null;
            return true;
        }
        if (input.incrementToken()) {
            final char[] buffer = termAtt.buffer();
            final int length = termAtt.length();

            // If no characters actually require rewriting then we
            // just return token as-is:
            for (int i = 0; i < length; ++i) {
                final char c = buffer[i];
                if (c >= '\u0080') {
                    foldToASCII(buffer, length);
                    termAtt.copyBuffer(output, 0, outputPos);
                    break;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        state = null;
    }

    /**
     * Converts characters above ASCII to their ASCII equivalents. For example,
     * accents are removed from accented characters.
     *
     * @param input
     *            The string to fold
     * @param length
     *            The number of characters in the input string
     */
    public void foldToASCII(char[] input, int length) {
        if (preserveOriginal) {
            state = captureState();
        }
        // Worst-case length required:
        final int maxSizeNeeded = 4 * length;
        if (output.length < maxSizeNeeded) {
            output = new char[ArrayUtil.oversize(maxSizeNeeded, Character.BYTES)];
        }

        outputPos = foldToASCII(input, 0, output, 0, length);
    }

    private static char[][] initializeMap() {
        char[][] map = new char[256 * 256][];
        for (int i = 0; i < map.length; i++) {
            char[] c = { (char) i };
            char[] out = new char[4];
            int len = ASCIIFoldingFilter.foldToASCII(c, 0, out, 0, 1);
            map[i] = new char[len];
            System.arraycopy(out, 0, map[i], 0, len);
        }
        return map;
    }

    public static final int foldToASCII(char input[], int inputPos, char output[], int outputPos, int length) {
        final int end = inputPos + length;
        for (int pos = inputPos; pos < end; ++pos) {
            char[] chars = map[input[pos]];
            for (char c : chars) {
                output[outputPos++] = c;
            }
        }
        return outputPos;
    }

}
