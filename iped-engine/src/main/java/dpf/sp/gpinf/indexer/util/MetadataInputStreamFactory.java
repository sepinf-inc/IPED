package dpf.sp.gpinf.indexer.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import dpf.sp.gpinf.indexer.parsers.util.MetadataUtil;
import iped3.io.SeekableInputStream;
import iped3.util.ExtraProperties;

public class MetadataInputStreamFactory extends SeekableInputStreamFactory {

    private Metadata metadata;
    private boolean fromMetadataPreview = false;

    public MetadataInputStreamFactory(Metadata metadata) {
        super(null);
        this.metadata = metadata;
    }
    
    public MetadataInputStreamFactory(Metadata metadata, boolean fromMetadataPreview) {
        super(null);
        this.metadata = metadata;
        this.fromMetadataPreview = fromMetadataPreview;
    }
    
    private boolean includeMeta(String meta) {
        if(fromMetadataPreview) {
            return !MetadataUtil.ignorePreviewMetas.contains(meta);
            
        }else {
            return meta.startsWith(ExtraProperties.UFED_META_PREFIX) ||
                   meta.startsWith(ExtraProperties.MESSAGE_PREFIX);
        }
        
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String identifier) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        final byte[] BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        baos.write(BOM);
        try (OutputStreamWriter osw = new OutputStreamWriter(baos, "UTF-8")) { //$NON-NLS-1$
            String[] metas = metadata.names();
            Arrays.sort(metas);
            for (String meta : metas) {
                if (includeMeta(meta)) {
                    osw.write(meta + ":"); //$NON-NLS-1$
                    for (String val : metadata.getValues(meta))
                        osw.write(" " + val); //$NON-NLS-1$
                    osw.write("\r\n"); //$NON-NLS-1$
                }
            }
        }
        return new SeekableFileInputStream(new SeekableInMemoryByteChannel(baos.toByteArray()));
    }

}
