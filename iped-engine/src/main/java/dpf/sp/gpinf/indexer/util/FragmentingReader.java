package dpf.sp.gpinf.indexer.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

public class FragmentingReader extends Reader {

    // Tamanho mínimo dos fragmentos de divisão do texto de arquivos grandes
    private static long textSplitSize = 10000000;

    // Tamanho de sobreposição de texto nas bordas dos fragmentos
    private static int textOverlapSize = 10000;

    private Reader reader;

    private long fragmentRead = 0;
    private long totalTextSize = 0;
    private long fragReadMark = 0;
    private int lastRead = 0;

    public static void setTextSplitSize(long textSplitSize) {
        FragmentingReader.textSplitSize = textSplitSize;
    }

    public static void setTextOverlapSize(int textOverlapSize) {
        FragmentingReader.textOverlapSize = textOverlapSize;
    }

    public FragmentingReader(Reader reader) {
        if (reader.markSupported())
            this.reader = reader;
        else
            this.reader = new BufferedReader(reader);
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
