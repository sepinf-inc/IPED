package iped.parsers.bittorrent;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.PushbackInputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dampcake.bencode.Type;

/**
 * InputStream for reading bencoded data. Adaptation from BencodeInputStream
 * from Adam Peck to end without exception when parsing incomplete/broken stream
 * with the information already parsed.
 *
 * @author Adam Peck
 * @author Patrick Dalla Bernardina
 */

public class BencodedIncompleteInputStream extends FilterInputStream {

    // EOF Constant
    private static final int EOF = -1;

    boolean incomplete = false;

    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    private static final int SEPARATOR = ':';

    private static final int TERMINATOR = 'e';

    private final Charset charset;
    private final boolean useBytes;
    private final PushbackInputStream in;

    /**
     * Creates a new BencodeInputStream that reads from the {@link InputStream} passed and uses the {@link Charset} passed for decoding the data
     * and boolean passed to control String parsing.
     *
     * If useBytes is false, then dictionary values that contain byte string data will be coerced to a {@link String}.
     * if useBytes is true, then dictionary values that contain byte string data will be coerced to a {@link ByteBuffer}.
     *
     * @param in       the {@link InputStream} to read from
     * @param charset  the {@link Charset} to use
     * @param useBytes controls coercion of dictionary values
     *
     * @throws NullPointerException if the {@link Charset} passed is null
     * 
     * @since 1.3
     */
    public BencodedIncompleteInputStream(final InputStream in, final Charset charset, boolean useBytes) {
        super(new PushbackInputStream(in));
        this.in = (PushbackInputStream) super.in;

        if (charset == null) throw new NullPointerException("charset cannot be null");
        this.charset = charset;
        this.useBytes = useBytes;
    }

    /**
     * Creates a new BencodeInputStream that reads from the {@link InputStream} passed and uses the {@link Charset} passed for decoding the data
     * and coerces dictionary values to a {@link String}.
     *
     * @param in      the {@link InputStream} to read from
     * @param charset the {@link Charset} to use
     *
     * @throws NullPointerException if the {@link Charset} passed is null
     *
     * @see #BencodeInputStream(InputStream, Charset, boolean)
     */
    public BencodedIncompleteInputStream(final InputStream in, final Charset charset) {
        this(in, charset, false);
    }

    /**
     * Creates a new BencodeInputStream that reads from the {@link InputStream} passed and uses the UTF-8 {@link Charset} for decoding the data
     * and coerces dictionary values to a {@link String}.
     *
     * @param in the {@link InputStream} to read from
     *
     * @see #BencodeInputStream(InputStream, Charset, boolean)
     */
    public BencodedIncompleteInputStream(final InputStream in) {
        this(in, DEFAULT_CHARSET);
    }

    /**
     * Gets the {@link Charset} the stream was created with.
     *
     * @return the {@link Charset} of the stream
     */
    public Charset getCharset() {
        return charset;
    }

    private int peek() throws IOException {
        int b = in.read();
        in.unread(b);
        return b;
    }

    /**
     * Peeks at the next {@link Type}.
     *
     * @return the next {@link Type} available
     *
     * @throws IOException
     *             if the underlying stream throws
     * @throws EOFException
     *             if the end of the stream has been reached
     */
    public Type nextType() throws IOException {
        int token = peek();
        checkEOF(token);

        return typeForToken(token);
    }

    private Type typeForToken(int token) {
        switch (token) {
            case 'i':
                return Type.NUMBER;
            case 'l':
                return Type.LIST;
            case 'd':
                return Type.DICTIONARY;
            default:
                break;
        }

        if (Character.isDigit(token)) {
            return Type.STRING;
        }

        return Type.UNKNOWN;
    }

    /**
     * Reads a {@link String} from the stream.
     *
     * @return the {@link String} read from the stream
     *
     * @throws IOException
     *             if the underlying stream throws
     * @throws EOFException
     *             if the end of the stream has been reached
     * @throws InvalidObjectException
     *             if the next type in the stream is not a String
     */
    public String readString() throws IOException {
        return new String(readStringBytesInternal(), getCharset());
    }

    /**
     * Reads a Byte String from the stream.
     *
     * @return the {@link ByteBuffer} read from the stream
     *
     * @throws IOException
     *             if the underlying stream throws
     * @throws EOFException
     *             if the end of the stream has been reached
     * @throws InvalidObjectException
     *             if the next type in the stream is not a String
     * 
     * @since 1.3
     */
    public ByteBuffer readStringBytes() throws IOException {
        return ByteBuffer.wrap(readStringBytesInternal());
    }

    private byte[] readStringBytesInternal() throws IOException {
        int token = in.read();
        validateToken(token, Type.STRING);

        StringBuilder buffer = new StringBuilder();
        buffer.append((char) token);
        while ((token = in.read()) != SEPARATOR) {
            try {
                validateToken(token, Type.STRING);
            } catch (Exception e) {
                incomplete = true;
                return new byte[0];
            }

            buffer.append((char) token);
        }

        int length = Integer.parseInt(buffer.toString());
        byte[] bytes = new byte[length];
        int lenRead = read(bytes);
        if (lenRead < length) {
            bytes = Arrays.copyOfRange(bytes, 0, lenRead);
            incomplete = true;
        }
        return bytes;
    }

    /**
     * Reads a Number from the stream.
     *
     * @return the Number read from the stream
     *
     * @throws IOException
     *             if the underlying stream throws
     * @throws EOFException
     *             if the end of the stream has been reached
     * @throws InvalidObjectException
     *             if the next type in the stream is not a Number
     */
    public Long readNumber() throws IOException {
        int token = in.read();
        validateToken(token, Type.NUMBER);

        StringBuilder buffer = new StringBuilder();
        while ((token = in.read()) != TERMINATOR) {
            checkEOF(token);

            buffer.append((char) token);
        }

        return new BigDecimal(buffer.toString()).longValue();
    }

    /**
     * Reads a List from the stream.
     *
     * @return the List read from the stream
     *
     * @throws IOException
     *             if the underlying stream throws
     * @throws EOFException
     *             if the end of the stream has been reached
     * @throws InvalidObjectException
     *             if the next type in the stream is not a List, or the list
     *             contains invalid types
     */
    public List<Object> readList() throws IOException {
        int token = in.read();
        validateToken(token, Type.LIST);

        List<Object> list = new ArrayList<Object>();
        while ((token = in.read()) != TERMINATOR) {
            checkEOF(token);

            list.add(readObject(token));
        }

        return list;
    }

    /**
     * Reads a Dictionary from the stream.
     *
     * @return the Dictionary read from the stream
     *
     * @throws IOException
     *             if the underlying stream throws
     * @throws EOFException
     *             if the end of the stream has been reached
     * @throws InvalidObjectException
     *             if the next type in the stream is not a Dictionary, or the list
     *             contains invalid types
     */
    public Map<String, Object> readDictionary() throws IOException {
        int token = in.read();
        validateToken(token, Type.DICTIONARY);

        Map<String, Object> map = new LinkedHashMap<String, Object>();
        while ((token = in.read()) != TERMINATOR) {
            if (token == EOF || incomplete) {
                break;
            }

            in.unread(token);
            String key = readString();
            if (!incomplete) {
                try {
                    map.put(key, readObject(in.read()));
                } catch (Exception e) {
                    break;
                }
            }
        }

        return map;
    }

    private Object readObject(final int token) throws IOException {
        in.unread(token);

        Type type = typeForToken(token);

        if (type == Type.STRING && !useBytes)
            return readString();
        if (type == Type.STRING && useBytes)
            return readStringBytes();
        if (type == Type.NUMBER)
            return readNumber();
        if (type == Type.LIST)
            return readList();
        if (type == Type.DICTIONARY)
            return readDictionary();

        throw new InvalidObjectException("Unexpected token '" + new String(Character.toChars(token)) + "'");
    }


    private void validateToken(final int token, final Type type) throws IOException {
        checkEOF(token);

        switch (token) {
            case 'i':
                if (type != Type.NUMBER)
                    throw new InvalidObjectException("Unexpected token '" + new String(Character.toChars(token)) + "'");
                break;
            case 'l':
                if (type != Type.LIST)
                    throw new InvalidObjectException("Unexpected token '" + new String(Character.toChars(token)) + "'");
                break;
            case 'd':
                if (type != Type.DICTIONARY)
                    throw new InvalidObjectException("Unexpected token '" + new String(Character.toChars(token)) + "'");
                break;
            default:
                break;
        }

        if (Character.isDigit(token)) {
            if (type != Type.STRING)
                throw new InvalidObjectException("Unexpected token '" + new String(Character.toChars(token)) + "'");
        }
    }

    private void checkEOF(final int b) throws EOFException {
        if (b == EOF)
            throw new EOFException();
    }

    public boolean isIncomplete() {
        return incomplete;
    }
}

