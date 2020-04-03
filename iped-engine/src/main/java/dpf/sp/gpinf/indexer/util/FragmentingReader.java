package dpf.sp.gpinf.indexer.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

import dpf.sp.gpinf.indexer.util.TextCache.KnownSizeReader;

public class FragmentingReader extends Reader {

    // Tamanho mínimo dos fragmentos de divisão do texto de arquivos grandes
    private static long textSplitSize = 10485760;

    // Tamanho de sobreposição de texto nas bordas dos fragmentos
    private static int textOverlapSize = 10000;

    private Reader reader;

    private long fragmentRead = 0;
    private long totalTextSize = 0;
    private long fragReadMark = 0;
    private int lastRead = 0;
    
    private long knownSize = -1;

    public static void setTextSplitSize(long textSplitSize) {
        FragmentingReader.textSplitSize = textSplitSize;
    }

    public static void setTextOverlapSize(int textOverlapSize) {
        FragmentingReader.textOverlapSize = textOverlapSize;
    }

    public FragmentingReader(Reader reader) {
        if(reader instanceof KnownSizeReader) {
            knownSize = ((KnownSizeReader)reader).getSize();
        }
        if (reader.markSupported())
            this.reader = reader;
        else
            this.reader = new BufferedReader(reader);
    }
    
    public int estimateNumberOfFrags() {
        if(knownSize > -1) {
            long size = knownSize;
            if(size <= textSplitSize + textOverlapSize) {
                return 1;
            }else {
                return (int)Math.ceil(((double)size - textOverlapSize) / textSplitSize);
            }
        }
        return -1;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {

        if (fragmentRead >= textSplitSize) {
            if (fragReadMark == 0) {
                reader.mark(textOverlapSize);
                fragReadMark = fragmentRead;
            } else if (fragmentRead - fragReadMark == textOverlapSize) {
                return -1;
            }
            if (fragmentRead + len > textSplitSize + textOverlapSize) {
                len = (int) (textSplitSize + textOverlapSize - fragmentRead);
            }
        } else if (fragmentRead + len > textSplitSize) {
            len = (int) (textSplitSize - fragmentRead);
        }

        lastRead = reader.read(cbuf, off, len);
        if (lastRead != -1) {
            fragmentRead += lastRead;
        }

        return lastRead;

    }

    public boolean nextFragment() throws IOException {
        totalTextSize += fragmentRead;
        if (lastRead == -1) {
            return false;
        }
        totalTextSize -= textOverlapSize;
        fragmentRead = 0;
        fragReadMark = 0;
        reader.reset();
        return true;
    }

    public long getTotalTextSize() {
        return totalTextSize;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

}
