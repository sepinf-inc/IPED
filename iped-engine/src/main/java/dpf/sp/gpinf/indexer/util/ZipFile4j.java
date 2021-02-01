package dpf.sp.gpinf.indexer.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.List;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.ZipInputStream;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipModel;
import net.lingala.zip4j.unzip.Unzip;
import net.lingala.zip4j.util.InternalZipConstants;
import net.lingala.zip4j.util.Zip4jUtil;

/**
 * Creates a map to get FileHeader from fileName more fast.
 * 
 * @author Nassif
 *
 */
public class ZipFile4j extends ZipFile {

    private ZipModel zipModel;
    private HashMap<String, FileHeader> headersMap = new HashMap<>();
    private String file;
    private int mode;
    private String fileNameCharset;

    public ZipFile4j(File zipFile) throws ZipException {
        super(zipFile);
        this.file = zipFile.getPath();
        this.mode = InternalZipConstants.MODE_UNZIP;

        for (Object header : this.getFileHeaders())
            headersMap.put(((FileHeader) header).getFileName(), (FileHeader) header);
    }

    public FileHeader getFileHeader(String fileName) throws ZipException {
        return headersMap.get(fileName);
    }

    public List getFileHeaders() throws ZipException {
        readZipInfo();
        if (zipModel == null || zipModel.getCentralDirectory() == null) {
            return null;
        }
        return zipModel.getCentralDirectory().getFileHeaders();
    }

    public ZipInputStream getInputStream(FileHeader fileHeader) throws ZipException {
        if (fileHeader == null) {
            throw new ZipException("FileHeader is null, cannot get InputStream");
        }

        if (zipModel == null) {
            throw new ZipException("zip model is null, cannot get inputstream");
        }

        Unzip unzip = new Unzip(zipModel);
        return unzip.getInputStream(fileHeader);
    }

    private void readZipInfo() throws ZipException {

        if (!Zip4jUtil.checkFileExists(file)) {
            throw new ZipException("zip file does not exist");
        }

        if (!Zip4jUtil.checkFileReadAccess(this.file)) {
            throw new ZipException("no read access for the input zip file");
        }

        if (this.mode != InternalZipConstants.MODE_UNZIP) {
            throw new ZipException("Invalid mode");
        }

        RandomAccessFile raf = null;
        try {
            if (zipModel == null) {
                raf = new RandomAccessFile(new File(file), InternalZipConstants.READ_MODE);

                Zip4jFastHeaderReader headerReader = new Zip4jFastHeaderReader(raf);
                zipModel = headerReader.readAllHeaders(fileNameCharset);
                if (zipModel != null) {
                    zipModel.setZipFile(file);
                }
            }
        } catch (FileNotFoundException e) {
            throw new ZipException(e);

        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

}
