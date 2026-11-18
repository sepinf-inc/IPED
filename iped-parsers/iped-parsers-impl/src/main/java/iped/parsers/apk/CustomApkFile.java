package iped.parsers.apk;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import iped.utils.IOUtil;
import net.dongliu.apk.parser.AbstractApkFile;
import net.dongliu.apk.parser.bean.ApkSignStatus;
import net.dongliu.apk.parser.bean.ApkV2Signer;
import net.dongliu.apk.parser.bean.CertificateMeta;
import net.dongliu.apk.parser.parser.CertificateMetas;
import net.dongliu.apk.parser.struct.signingv2.ApkSigningBlock;
import net.dongliu.apk.parser.struct.signingv2.Digest;
import net.dongliu.apk.parser.struct.signingv2.Signature;
import net.dongliu.apk.parser.struct.signingv2.SignerBlock;
import net.dongliu.apk.parser.struct.zip.EOCD;
import net.dongliu.apk.parser.utils.Buffers;
import net.dongliu.apk.parser.utils.Inputs;
import net.dongliu.apk.parser.utils.Unsigned;

/**
 * This class replaces the library net.dongliu.apk.parser.ApkFile, that uses
 * MappedByteBuffer which may hold a reference to the file being parsed, until
 * it is garbage collected, preventing the temporary file to be deleted.
 */
public class CustomApkFile extends AbstractApkFile implements Closeable {

    private final ZipFile zf;
    private final File apkFile;
    private List<ApkV2Signer> apkV2Signers;

    public CustomApkFile(File apkFile) throws IOException {
        this.apkFile = apkFile;
        this.zf = new ZipFile(apkFile);
    }

    @Override
    protected List<CertificateFile> getAllCertificateData() throws IOException {
        Enumeration<? extends ZipEntry> enu = zf.entries();
        List<CertificateFile> list = new ArrayList<>();
        while (enu.hasMoreElements()) {
            ZipEntry ne = enu.nextElement();
            if (ne.isDirectory()) {
                continue;
            }
            String name = ne.getName().toUpperCase();
            if (name.endsWith(".RSA") || name.endsWith(".DSA")) {
                list.add(new CertificateFile(name, Inputs.readAllAndClose(zf.getInputStream(ne))));
            }
        }
        return list;
    }

    @Override
    public byte[] getFileData(String path) throws IOException {
        ZipEntry entry = zf.getEntry(path);
        if (entry == null) {
            return null;
        }

        InputStream inputStream = zf.getInputStream(entry);
        return Inputs.readAllAndClose(inputStream);
    }

    @Override
    protected ByteBuffer fileData() throws IOException {
        return null;
    }

    @Override
    @Deprecated
    public ApkSignStatus verifyApk() throws IOException {
        return null;
    }

    @Override
    public void close() throws IOException {
        super.close();
        IOUtil.closeQuietly(zf);
    }

    /**
     * Create ApkSignBlockParser for this apk file.
     *
     * @return null if do not have sign block
     */
    protected ByteBuffer findApkSignBlock() throws IOException {
        long len = apkFile.length();
        if (len < 22) {
            throw new RuntimeException("Not zip file");
        }
        int maxEOCDSize = 1 << 18;
        byte[] buf = new byte[(int) Math.min(len, maxEOCDSize)];
        try (RandomAccessFile raf = new RandomAccessFile(apkFile, "r")) {
            raf.seek(len - buf.length);
            raf.readFully(buf);
            ByteBuffer buffer = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            long cdStart = -1;
            for (int i = buf.length - 22; i >= 0; i--) {
                int v = buffer.getInt(i);
                if (v == EOCD.SIGNATURE) {
                    Buffers.position(buffer, i + 16);
                    cdStart = Buffers.readUInt(buffer);
                    break;
                }
            }
            if (cdStart == -1) {
                return null;
            }
            int magicStrLen = 16;
            buf = new byte[magicStrLen];
            raf.seek(cdStart - magicStrLen);
            raf.readFully(buf);
            buffer = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            String magic = Buffers.readAsciiString(buffer, magicStrLen);
            if (!magic.equals(ApkSigningBlock.MAGIC)) {
                return null;
            }
            buf = new byte[8];
            raf.seek(cdStart - 24);
            raf.readFully(buf);
            buffer = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            int blockSize = Unsigned.ensureUInt(buffer.getLong());
            raf.seek(cdStart - blockSize - 8);
            raf.readFully(buf);
            buffer = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            int blockSize2 = Unsigned.ensureUInt(buffer.getLong());
            if (blockSize != blockSize2) {
                return null;
            }
            buf = new byte[blockSize - magicStrLen];
            raf.readFully(buf);
            return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    public List<ApkV2Signer> getApkV2Singers() throws IOException, CertificateException {
        if (apkV2Signers == null) {
            parseApkSigningBlock();
        }
        return this.apkV2Signers;
    }

    private void parseApkSigningBlock() throws IOException, CertificateException {
        List<ApkV2Signer> list = new ArrayList<>();
        ByteBuffer apkSignBlockBuf = findApkSignBlock();
        if (apkSignBlockBuf != null) {
            CustomApkSignBlockParser parser = new CustomApkSignBlockParser(apkSignBlockBuf);
            ApkSigningBlock apkSigningBlock = parser.parse();
            for (SignerBlock signerBlock : apkSigningBlock.getSignerBlocks()) {
                List<X509Certificate> certificates = signerBlock.getCertificates();
                List<CertificateMeta> certificateMetas = CertificateMetas.from(certificates);
                ApkV2Signer apkV2Signer = new ApkV2Signer(certificateMetas);
                list.add(apkV2Signer);
            }
        }
        this.apkV2Signers = list;
    }
}

/**
 * This class replace ApkSignBlockParser to read more V2 signers.
 */
class CustomApkSignBlockParser {
    private ByteBuffer data;

    public CustomApkSignBlockParser(ByteBuffer data) {
        this.data = data.order(ByteOrder.LITTLE_ENDIAN);
    }

    public ApkSigningBlock parse() throws CertificateException {
        // sign block found, read pairs
        List<SignerBlock> signerBlocks = new ArrayList<>();
        while (data.remaining() >= 8) {
            int id = data.getInt();
            int size = Unsigned.ensureUInt(data.getInt());
            if (id == ApkSigningBlock.SIGNING_V2_ID) {
                ByteBuffer signingV2Buffer = Buffers.sliceAndSkip(data, size);
                // now only care about apk signing v2 entry
                while (signingV2Buffer.hasRemaining()) {
                    SignerBlock signerBlock = readSigningV2(signingV2Buffer);
                    signerBlocks.add(signerBlock);
                }
            } else {
                if (data.position() + size >= data.limit()) {
                    break;
                }
                Buffers.position(data, data.position() + size);
            }
        }
        return new ApkSigningBlock(signerBlocks);
    }

    private SignerBlock readSigningV2(ByteBuffer buffer) throws CertificateException {
        buffer = readLenPrefixData(buffer);

        ByteBuffer signedData = readLenPrefixData(buffer);
        ByteBuffer digestsData = readLenPrefixData(signedData);
        List<Digest> digests = readDigests(digestsData);
        ByteBuffer certificateData = readLenPrefixData(signedData);
        List<X509Certificate> certificates = readCertificates(certificateData);
        ByteBuffer attributesData = readLenPrefixData(signedData);
        readAttributes(attributesData);

        ByteBuffer signaturesData = readLenPrefixData(buffer);
        List<Signature> signatures = readSignatures(signaturesData);

        readLenPrefixData(buffer);
        return new SignerBlock(digests, certificates, signatures);
    }

    private List<Digest> readDigests(ByteBuffer buffer) {
        List<Digest> list = new ArrayList<>();
        while (buffer.hasRemaining()) {
            ByteBuffer digestData = readLenPrefixData(buffer);
            int algorithmID = digestData.getInt();
            byte[] digest = Buffers.readBytes(digestData);
            list.add(new Digest(algorithmID, digest));
        }
        return list;
    }

    private List<X509Certificate> readCertificates(ByteBuffer buffer) throws CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certificates = new ArrayList<>();
        while (buffer.hasRemaining()) {
            ByteBuffer certificateData = readLenPrefixData(buffer);
            Certificate certificate = certificateFactory
                    .generateCertificate(new ByteArrayInputStream(Buffers.readBytes(certificateData)));
            certificates.add((X509Certificate) certificate);
        }
        return certificates;
    }

    private void readAttributes(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            readLenPrefixData(buffer);
        }
    }

    private List<Signature> readSignatures(ByteBuffer buffer) {
        List<Signature> signatures = new ArrayList<>();
        while (buffer.hasRemaining()) {
            ByteBuffer signatureData = readLenPrefixData(buffer);
            int algorithmID = signatureData.getInt();
            int signatureDataLen = Unsigned.ensureUInt(signatureData.getInt());
            byte[] signature = Buffers.readBytes(signatureData, signatureDataLen);
            signatures.add(new Signature(algorithmID, signature));
        }
        return signatures;
    }

    private ByteBuffer readLenPrefixData(ByteBuffer buffer) {
        int len = Unsigned.ensureUInt(buffer.getInt());
        return Buffers.sliceAndSkip(buffer, len);
    }
}
