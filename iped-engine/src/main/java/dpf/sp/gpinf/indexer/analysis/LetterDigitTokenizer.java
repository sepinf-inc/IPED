package dpf.sp.gpinf.indexer.analysis;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;

import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.util.Version;

/*
 * Tokenizador que divide o texto em caracteres diferentes de letras e números.
 * Também converte os caracteres para minúsculas.
 * Assim a indexação tem comportamento similar ao FTK
 */
public class LetterDigitTokenizer extends CharTokenizer {

    public static boolean convertCharsToLowerCase = true;

    private static int[] extraCodePoints;

    /**
     * Construct a new LetterTokenizer.
     */
    public LetterDigitTokenizer(Version version, Reader in) {
        super(version, in);
    }

    private static final boolean[] isChar = getCharMap();

    private static boolean[] getCharMap() {

        boolean[] isChar = new boolean[1114112];
        for (int c = 0; c < isChar.length; c++) {
            if (Character.isLetterOrDigit(c)) {
                isChar[c] = true;
            }
        }

        return isChar;

    }

    /**
     * Collects only characters which satisfy {@link Character#isLetter(char)}.
     */
    @Override
    protected boolean isTokenChar(int c) {
        // return Character.isLetterOrDigit(c) || (extraCodePoints != null &&
        // isExtraCodePoints(c));
        return isChar[c] || (extraCodePoints != null && isExtraCodePoints(c));
    }

    public static void load(String chars) throws FileNotFoundException, IOException {

        ArrayList<Integer> codePoints = new ArrayList<Integer>();

        for (String c : chars.split(" ")) { //$NON-NLS-1$
            codePoints.add(c.codePointAt(0));
        }

        if (codePoints.size() > 0) {
            extraCodePoints = new int[codePoints.size()];
            for (int i = 0; i < extraCodePoints.length; i++) {
                extraCodePoints[i] = codePoints.get(i);
            }
        }

    }

    private static final boolean isExtraCodePoints(int c) {

        for (int i = 0; i < extraCodePoints.length; i++) {
            if (c == extraCodePoints[i]) {
                return true;
            }
        }

        return false;
    }

    /**
     * Converts char to lower case {@link Character#toLowerCase(char)}.
     */
    @Override
    protected int normalize(int c) {
        if (convertCharsToLowerCase) {
            return Character.toLowerCase(c);
        } else {
            return c;
        }
    }

}
