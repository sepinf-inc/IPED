package dpf.sp.gpinf.indexer.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

public class ZIPInputStreamFactory extends SeekableInputStreamFactory implements Closeable{

    private ZipFile zip;
    
    public ZIPInputStreamFactory(Path dataSource) {
        super(dataSource);
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String path) throws IOException {
        Path tmp = null;
        int tries = 0;
        while(tries < 2) {
            if(zip == null)
                zip = new ZipFile(this.dataSource.toFile());
            ZipArchiveEntry zae = zip.getEntry(path);
            tmp = Files.createTempFile("zip-stream", null);
            try(InputStream is = zip.getInputStream(zae)){
                Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
                break;
            }catch(IOException e) {
                if(zip != null) zip.close();
                zip = null;
                Files.delete(tmp);
                tries++;
            }
        }
        final Path finalTmp = tmp;
        SeekableInputStream sis = new SeekableFileInputStream(finalTmp.toFile()) {
            @Override
            public void close() throws IOException {
                super.close();
                Files.delete(finalTmp);
            }
        };
        return sis;
    }

    @Override
    public void close() throws IOException {
        if(zip != null) zip.close();
    }
    
}
