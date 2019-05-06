package dpf.sp.gpinf.indexer.util;

import java.io.IOException;

import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;

import iped3.io.SeekableInputStream;

public class SleuthkitInputStream extends SeekableInputStream {

  ReadContentInputStream rcis;
  volatile boolean closed = false;

  public SleuthkitInputStream(Content file) {
    rcis = new ReadContentInputStream(file);
  }

  @Override
  public int read(byte b[]) throws IOException {
    if (closed) {
      throw new IOException("Stream was closed."); //$NON-NLS-1$
    }
    return read(b, 0, b.length);
  }

  @Override
  public int read(byte b[], int off, int len) throws IOException {
    if (closed) {
      throw new IOException("Stream was closed."); //$NON-NLS-1$
    }
    return rcis.read(b, off, len);
  }

  @Override
  public int read() throws IOException {
    if (closed) {
      throw new IOException("Stream was closed."); //$NON-NLS-1$
    }
    return rcis.read();
  }

  @Override
  public int available() throws IOException {
    return rcis.available();
  }

  @Override
  public long skip(long n) throws IOException {
    if (closed) {
      throw new IOException("Stream was closed."); //$NON-NLS-1$
    }
    return rcis.skip(n);

  }

  public void seek(long pos) throws IOException {
    if (closed) {
      throw new IOException("Stream was closed."); //$NON-NLS-1$
    }
    long newPos = rcis.seek(pos);
    if (newPos != pos) {
      throw new IOException("Seek to " + pos + " failed"); //$NON-NLS-1$ //$NON-NLS-2$
    }
  }

  @Override
  public boolean markSupported() {
    return rcis.markSupported();
  }

  @Override
  public void mark(int mark) {
    rcis.mark(mark);
  }

  @Override
  public void reset() throws IOException {
    if (closed) {
      throw new IOException("Stream was closed."); //$NON-NLS-1$
    }
    rcis.reset();
  }

  @Override
  public void close() throws IOException {
    closed = true;
    rcis.close();
  }

  @Override
  public long position() {
    return rcis.getCurPosition();
  }

  @Override
  public long size() {
    return rcis.getLength();
  }

}
