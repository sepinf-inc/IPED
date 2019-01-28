package dpf.sp.gpinf.indexer.util;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

public class CloseFilterReader extends FilterReader{

    public CloseFilterReader(Reader in) {
        super(in);
    }

    @Override
    public void close() throws IOException {
        //ignore
    }
    
    public void reallyClose() throws IOException {
        super.close();
    }

}
