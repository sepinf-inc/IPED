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
package dpf.sp.gpinf.indexer.parsers.util;

import java.io.Serializable;
import java.util.UUID;
import java.util.regex.Pattern;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX event handler that writes content up to an write limit out to a character
 * stream or other decorated handler.
 */
public class LimitedContentHandler extends DefaultHandler {

    /**
     * The unique tag associated with exceptions from stream.
     */
    private final Serializable tag = UUID.randomUUID();

    /**
     * The maximum number of characters to write to the character stream.
     */
    private final int writeLimit;

    /**
     * Number of characters written so far.
     */
    private int writeCount = 0;

    private StringBuilder writer;

    /**
     * Creates a content handler that writes character events to an internal string
     * buffer. Use the {@link #toString()} method to access the collected character
     * content.
     * <p>
     * The internal string buffer is bounded at the given number of characters. If
     * this write limit is reached, then a {@link SAXException} is thrown. The
     * {@link #isWriteLimitReached(Throwable)} method can be used to detect this
     * case.
     *
     * @since Apache Tika 0.7
     * @param writeLimit
     *            maximum number of characters to include in the string, or -1 to
     *            disable the write limit
     */
    public LimitedContentHandler(int writeLimit) {
        this.writeLimit = writeLimit;
        this.writer = new StringBuilder();
    }

    private static Pattern pattern = Pattern.compile("[\\xA0\\x00\\s]+"); //$NON-NLS-1$

    /**
     * Writes the given characters to the given character stream.
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {

        writer.append(ch, start, length);
        writeCount += length;

        if (writeCount >= writeLimit) {
            String str = writer.toString();
            str = pattern.matcher(str).replaceAll(" "); //$NON-NLS-1$

            if (str.length() < writeLimit) {
                writer = new StringBuilder();
                writer.append(str);
                writeCount = str.length();
                return;
            }
            if (str.length() > writeLimit) {
                writer = new StringBuilder();
                writer.append(str.substring(0, writeLimit));
                writeCount = writeLimit;
            }
            throw new WriteLimitReachedException("Your document contained more than " + writeLimit //$NON-NLS-1$
                    + " characters, and so your requested limit has been" //$NON-NLS-1$
                    + " reached. To receive the full text of the document," //$NON-NLS-1$
                    + " increase your limit. (Text up to the limit is" //$NON-NLS-1$
                    + " however available).", tag); //$NON-NLS-1$
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        this.characters(ch, start, length);
    }

    /**
     * Checks whether the given exception (or any of it's root causes) was thrown by
     * this handler as a signal of reaching the write limit.
     *
     * @since Apache Tika 0.7
     * @param t
     *            throwable
     * @return <code>true</code> if the write limit was reached, <code>false</code>
     *         otherwise
     */
    public boolean isWriteLimitReached(Throwable t) {
        if (t instanceof WriteLimitReachedException) {
            return tag.equals(((WriteLimitReachedException) t).tag);
        } else {
            return t.getCause() != null && isWriteLimitReached(t.getCause());
        }
    }

    /**
     * The exception used as a signal when the write limit has been reached.
     */
    private static class WriteLimitReachedException extends SAXException {

        /** Serial version UID */
        private static final long serialVersionUID = -1850581945459429943L;

        /** Serializable tag of the handler that caused this exception */
        private final Serializable tag;

        public WriteLimitReachedException(String message, Serializable tag) {
            super(message);
            this.tag = tag;
        }

    }

    @Override
    public String toString() {
        return writer.toString();
    }

}
