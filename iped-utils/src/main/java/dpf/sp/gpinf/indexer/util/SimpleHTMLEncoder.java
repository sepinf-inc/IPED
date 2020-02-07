package dpf.sp.gpinf.indexer.util;

/**
 * Copyright 2005 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Simple {@link Encoder} implementation to escape text for HTML output
 * 
 */
public class SimpleHTMLEncoder {

    public SimpleHTMLEncoder() {
    }

    public String encodeText(String originalText) {
        return htmlEncode(originalText);
    }

    public final static String htmlDecode(String html) {
        if (html == null || html.length() == 0)
            return ""; //$NON-NLS-1$

        String result = html.replaceAll("&quot;", "\""); //$NON-NLS-1$ //$NON-NLS-2$
        result = result.replaceAll("&amp;", "&"); //$NON-NLS-1$ //$NON-NLS-2$
        result = result.replaceAll("&lt;", "<"); //$NON-NLS-1$ //$NON-NLS-2$
        result = result.replaceAll("&gt;", ">"); //$NON-NLS-1$ //$NON-NLS-2$
        result = result.replaceAll("&#39;", "'"); //$NON-NLS-1$ //$NON-NLS-2$

        return result;

    }

    /**
     * Encode string into HTML
     */
    public final static String htmlEncode(String plainText) {
        if (plainText == null || plainText.length() == 0) {
            return ""; //$NON-NLS-1$
        }

        StringBuilder result = new StringBuilder(plainText.length());

        for (int index = 0; index < plainText.length(); index++) {

            char ch = plainText.charAt(index);

            switch (ch) {
                case '"':
                    result.append("&quot;"); //$NON-NLS-1$
                    break;

                case '\'':
                    result.append("&#39;"); //$NON-NLS-1$
                    break;

                case '&':
                    result.append("&amp;"); //$NON-NLS-1$
                    break;

                case '<':
                    result.append("&lt;"); //$NON-NLS-1$
                    break;

                case '>':
                    result.append("&gt;"); //$NON-NLS-1$
                    break;

                default:
                    result.append(ch);

            }
        }

        return result.toString();
    }
}