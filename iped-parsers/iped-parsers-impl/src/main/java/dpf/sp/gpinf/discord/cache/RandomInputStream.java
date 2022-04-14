package dpf.sp.gpinf.discord.cache;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class RandomInputStream extends InputStream {

    private RandomAccessFile randomAccessFile;
    private long position = 0;

    public RandomInputStream(RandomAccessFile randomAccessFile, long startPosition) throws IOException {
        this.randomAccessFile = randomAccessFile;
        position = startPosition;
    }

    @Override
    public int read() throws IOException {
    	randomAccessFile.seek(position++);
        return randomAccessFile.read();
    }
}
