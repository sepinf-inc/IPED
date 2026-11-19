package iped.utils;

import java.io.IOException;
import java.io.InputStream;

public abstract class SimpleInputStreamFactory {

    public abstract InputStream getInputStream() throws IOException;

}
