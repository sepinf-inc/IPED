package iped.engine.io;

import iped.utils.SeekableInputStreamFactory;
import iped.engine.task.dvr.wfs.WFSExtractor;
import iped.utils.SeekableFileInputStream;
import iped.io.SeekableInputStream;
import java.io.IOException;

/*

 * WFSExtractor.
 *
 * @author guilherme.dutra

*/

public class WFSInputStreamFactory extends SeekableInputStreamFactory {

    WFSExtractor wfs;
    SeekableInputStream is;

    public WFSInputStreamFactory(SeekableInputStream is) {

        super(null);
        this.is = is;
    }

    private synchronized void init() throws IOException {
        if (wfs == null){
            wfs = new WFSExtractor();
            wfs.init(is);
        }
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String identifier) throws IOException {
        if (wfs == null)
            init();
        return wfs.getDescriptorFromIdentifier(identifier);
    }

}