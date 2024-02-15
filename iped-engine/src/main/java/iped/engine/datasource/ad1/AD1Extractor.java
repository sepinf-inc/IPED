package iped.engine.datasource.ad1;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import iped.io.SeekableInputStream;

/**
 *
 * @author guilherme.dutra
 */
public class AD1Extractor implements Closeable {

    private static final long SIGNATURE_SIZE = 512; // 0x200
    private static final String charset = "UTF-8";

    private static Object lock = new Object();

    private File file;
    private Map<Integer, List<ByteBuffer>> fcMap = new HashMap<>();
    private List<FileChannel> channels = new ArrayList<>();

    long file_block_size = 0L;
    long file_number = 0L;
    long file_count = 0L;

    private FileHeader rootHeader = null;

    byte vector_15[] = new byte[15];
    byte vector_17[] = new byte[17];
    byte vector_20[] = new byte[20];
    byte vector_512[] = new byte[512];
    byte vector_48[] = new byte[48];
    byte vector_dynamic[];

    long PC = 0L;
    long aux = 0L;
    long PC_first_file = 0L;
    long PC_root = 0L;

    String signature = "";
    String ad1_name = "";
    long local_ad1_len = 48L; // 0x30
    long name_ad1_len = 0L;
    long root_info_len = 20L; // 0x14
    long root_name_len = 0L;

    public AD1Extractor(File arquivo) throws IOException {
        if (!arquivo.exists()) {
            throw new FileNotFoundException("Arquivo AD1 nao encontrado");
        }
        file = arquivo;
        FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.READ);
        channels.add(fc);
        List<ByteBuffer> bbList = new ArrayList<>();
        for (long pos = 0; pos < fc.size(); pos += Integer.MAX_VALUE) {
            int size = (int) Math.min(fc.size() - pos, Integer.MAX_VALUE);
            bbList.add(fc.map(MapMode.READ_ONLY, pos, size));
        }
        fcMap.put(1, bbList);

        headerInit();
    }

    public void headerInit() throws IOException {

        // Procura signature AD1
        readBytesFromAbsoluteFilePos(vector_15, PC, 15);
        signature = new String(vector_15, charset);

        if (!signature.equals("ADSEGMENTEDFILE")) {
            throw new AD1ExtractorException("Expected header not found in AD1: " + signature);
        }

        readBytesFromAbsoluteFilePos(vector_48, PC, 48);

        aux = readLongFromBufLE(vector_48, 35, 1);
        aux = aux >> 4;

        file_block_size = readLongFromBufLE(vector_48, 39, 4);
        file_block_size = (file_block_size << 4) + aux;

        file_number = readLongFromBufLE(vector_48, 28, 4);
        file_count = readLongFromBufLE(vector_48, 32, 4);

        // Procurar Logical AD1
        readBytesFromRelativeFilePos(vector_17, PC, 17);
        ad1_name = new String(vector_17, 0, 14, charset);
        if (!ad1_name.equals("ADLOGICALIMAGE")) {
            throw new AD1ExtractorException("Expected signature not found in AD1: " + ad1_name);
        }
        if (vector_17[16] != 0x03) {
            ;// throw new AD1ExtractorException("AD1 version not supported: " +
             // vector_17[16]);
        }

        readBytesFromRelativeFilePos(vector_48, PC, local_ad1_len);
        PC_first_file = readLongFromBufLE(vector_48, 44, 8);
        PC_root = readLongFromBufLE(vector_48, 36, 8);

        /*
         * //Essa parte tem q mudar para versao mais nova do ftk imager PC +=
         * local_ad1_len; //Ler nome da imagem ad1 name_ad1_len =
         * lerTamanhoInteiroDeHexReverso(vector_48,48,4); vector_dynamic = new byte
         * [(int)(name_ad1_len)]; lerBytesArquivoRelativo(arquivo, vector_dynamic, PC,
         * name_ad1_len); ad1_name = String.valueOf(vector_dynamic);
         * 
         * vector_dynamic = null;
         * 
         * PC += name_ad1_len;
         */

        // Ler Root
        root_info_len = 20L; // 0x14

        PC = PC_root;

        readBytesFromRelativeFilePos(vector_20, PC, root_info_len);

        root_name_len = readLongFromBufLE(vector_20, 20, 4);
        vector_dynamic = new byte[(int) root_name_len];

        PC += root_info_len;
        readBytesFromRelativeFilePos(vector_dynamic, PC, root_name_len);
        // String root_name = new String(vector_dynamic, charset);

        PC = PC_first_file;

        if (PC != 0L) {
            rootHeader = readObject(PC, null);
        }

    }

    public FileHeader readObject(long PC, FileHeader parent) throws IOException {

        byte vector_48[] = new byte[48];

        byte vector_16[] = new byte[16];
        byte vector_variavel[];

        long info_objetos_tam = 48L; // 0x30

        long pedacos_tam_adicionais = 0L;

        // Ler Objeto

        FileHeader header = new FileHeader(this, parent);
        header.object_address = PC;

        readBytesFromRelativeFilePos(vector_48, PC, info_objetos_tam);
        PC += info_objetos_tam;

        // Os 8 primeiros bytes apontam para o proximo
        header.nextObjAddress = readLongFromBufLE(vector_48, 8, 8);

        // Os proximos 8 bytes apontos para o filho
        header.childAddress = readLongFromBufLE(vector_48, 16, 8);

        // Os proximos 8 bytes tamanho do arquivo
        header.object_PC_partial_end = readLongFromBufLE(vector_48, 24, 8);

        // Tipo 0/2 arquivo, tipo 5/7 diretorio
        header.objectType = (int) readLongFromBufLE(vector_48, 44, 4);

        // Ler nome do arquivo
        header.objectNameSize = readLongFromBufLE(vector_48, 48, 4);

        // Ler nome do objeto
        vector_variavel = new byte[(int) header.objectNameSize];

        readBytesFromRelativeFilePos(vector_variavel, PC, header.objectNameSize);
        PC += header.objectNameSize;

        header.objectName = new String(vector_variavel, charset);

        vector_variavel = null;

        // Ler tamanho do objeto em bytes
        header.setObjectSizeBytes(readLongFromBufLE(vector_48, 40, 8));

        // Aqui ficam os mapeamentos para os bytes stream do arquivo
        if (header.getFileSize() != 0) {

            readBytesFromRelativeFilePos(vector_16, PC, 16);
            PC += 16;

            header.objectChunkSize = readLongFromBufLE(vector_16, 16, 8);

            pedacos_tam_adicionais = 8 + 7; // mais 15
            vector_variavel = new byte[(int) ((header.objectChunkSize * 8) + pedacos_tam_adicionais)];

            readBytesFromRelativeFilePos(vector_variavel, PC, (header.objectChunkSize * 8) + pedacos_tam_adicionais);
            PC += (header.objectChunkSize * 8) + pedacos_tam_adicionais;

            header.object_PC_partial_start = readLongFromBufLE(vector_variavel, 8, 8);
        } else {

            header.object_PC_partial_start = 0;
            header.objectChunkSize = 0;

        }

        if (header.objectChunkSize == 1) {

            header.addChunk(header.object_PC_partial_start, header.object_PC_partial_end);

        } else if (header.objectChunkSize > 1) {

            for (int i = 0; i < header.objectChunkSize; i++) {

                if (i != header.objectChunkSize - 1) {

                    header.addChunk(readLongFromBufLE(vector_variavel, ((i + 1) * 8), 8),
                            readLongFromBufLE(vector_variavel, ((i + 2) * 8), 8));

                } else {

                    header.addChunk(readLongFromBufLE(vector_variavel, ((i + 1) * 8), 8),
                            header.object_PC_partial_end);

                }

            }

        }

        vector_variavel = null;

        if (parent != null) {
            header.path = parent.path;
        }
        header.path += "/" + header.objectName;

        PC = header.object_PC_partial_end;

        readProperty(PC, header);

        vector_48 = null;

        vector_16 = null;

        return header;

    }

    public void readProperty(long endereco, FileHeader header) throws IOException {

        long endereco_prox_propriedade = 0L;
        long tamanho_propriedade = 20; // 0x14
        long propriedade_tam = 0;
        byte vector_variavel[] = null;
        byte vector_propriedade[] = null;
        String propriedade_extenso = "";
        long PC = 0;

        PC = endereco;

        vector_propriedade = new byte[(int) tamanho_propriedade];

        readBytesFromRelativeFilePos(vector_propriedade, PC, tamanho_propriedade);
        PC += tamanho_propriedade;

        endereco_prox_propriedade = readLongFromBufLE(vector_propriedade, 8, 8);

        int propCode = (int) readLongFromBufLE(vector_propriedade, 16, 4);

        propriedade_tam = readLongFromBufLE(vector_propriedade, 20, 4);

        vector_variavel = new byte[(int) propriedade_tam];

        readBytesFromRelativeFilePos(vector_variavel, PC, propriedade_tam);
        PC += propriedade_tam;

        propriedade_extenso = new String(vector_variavel, charset);

        header.propertiesMap.put(propCode, new Property(propriedade_extenso));

        if (endereco_prox_propriedade != 0) {
            readProperty(endereco_prox_propriedade, header);
        }

        vector_variavel = null;
        vector_propriedade = null;
        propriedade_extenso = null;

        return;
    }

    private static long readLongFromBufLE(byte[] cbuf, int pos, int tam) {

        long r = 0L;

        for (int i = (pos - 1); i > ((pos - 1) - tam); i--) {
            r = (r << 8) + (cbuf[i] & 0xFF);
        }

        return r;

    }

    private void readBytesFromAbsoluteFilePos(byte[] cbuf, long off, int len) throws IOException {
        // reads from first ad1 only
        ByteBuffer src = fcMap.get(1).get((int) (off / Integer.MAX_VALUE));
        src.duplicate().get(cbuf, (int) (off % Integer.MAX_VALUE), len);
    }

    private int seekAndRead(int ad1Ord, long seekOff, byte[] buf, int off, int len) throws IOException {

        try {
            List<ByteBuffer> bbList;
            synchronized (lock) {
                bbList = fcMap.get(ad1Ord);
                if (bbList == null) {
                    File newAd1 = new File(
                            file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf(".") + 3) + ad1Ord);
                    FileChannel fc = FileChannel.open(newAd1.toPath(), StandardOpenOption.READ);
                    channels.add(fc);
                    bbList = new ArrayList<>();
                    for (long pos = 0; pos < fc.size(); pos += Integer.MAX_VALUE) {
                        int size = (int) Math.min(fc.size() - pos, Integer.MAX_VALUE);
                        bbList.add(fc.map(MapMode.READ_ONLY, pos, size));
                    }
                    fcMap.put(ad1Ord, bbList);
                }
            }
            ByteBuffer src = bbList.get((int) (seekOff / Integer.MAX_VALUE));
            int seek = (int) (seekOff % Integer.MAX_VALUE);
            ByteBuffer bb = src.duplicate();
            bb.position(seek);
            int size = Math.min(len, bb.remaining());
            bb.get(buf, off, size);
            return size;

        } catch (ClosedChannelException e) {
            synchronized (lock) {
                fcMap.put(ad1Ord, null);
            }
            throw e;
        }
    }

    private int readBytesFromRelativeFilePos(byte[] cbuf, long off, long len) throws IOException {

        long endereco_final = SIGNATURE_SIZE + off;
        // String arquivo_final = arquivo;
        long len_aux = len;
        long off_aux = off;
        long bloco_aux = ((file_block_size * 1024 * 1024) - SIGNATURE_SIZE);
        int bytes_lidos = 0;
        int bytes_lidos_total = 0;
        int posicao_buffer = 0;

        while (len_aux > 0) {

            endereco_final = SIGNATURE_SIZE + (off_aux % bloco_aux);

            int ad1File = (int) (off_aux / bloco_aux) + 1;

            bytes_lidos = seekAndRead(ad1File, endereco_final, cbuf, posicao_buffer, (int) len_aux);
            bytes_lidos_total += bytes_lidos;

            if (bytes_lidos < 0) {
                throw new IOException("Erro ao ler arquivo. Fim de arquivo inesperado.");
            }

            len_aux -= bytes_lidos;

            posicao_buffer += bytes_lidos;

            off_aux += bytes_lidos;

        }

        return bytes_lidos_total;

    }

    public SeekableInputStream getSeekableInputStream(FileHeader header) throws IOException {

        return new AD1SeekableInputstream(header);

    }

    public FileHeader getRootHeader() {
        return rootHeader;
    }

    public boolean isEncrypted() {
        return false;
    }

    @Override
    public void close() throws IOException {
        for (Closeable c : channels)
            c.close();
    }

    /**
     * 
     * @author guilherme.dutra
     * @author Luis Nassif
     */
    public class AD1SeekableInputstream extends SeekableInputStream {

        private int chunkSize = 65536; // 0x10000

        private Inflater inflater = null;
        private byte[] compressed_buffer = null;
        private int compressed_size = 0;
        private byte[] uncompressed_buffer = new byte[(int) chunkSize];
        private int uncompressed_size = -1;
        private volatile boolean closed;

        private FileHeader header;
        long position = 0;
        int lastInflatedChunk = -1;

        public AD1SeekableInputstream(FileHeader header) {
            this.header = header;
            inflater = new Inflater();
        }

        @Override
        public void seek(long pos) throws IOException {
            checkIfClosed();
            if (pos >= size())
                throw new IOException("Position requested larger than size");
            position = pos;
        }

        @Override
        public long position() throws IOException {
            checkIfClosed();
            return position;
        }

        @Override
        public long size() throws IOException {
            // allow reading size even if closed
            return header.getFileSize();
        }

        @Override
        public int read() throws IOException {
            checkIfClosed();
            byte[] b = new byte[1];
            int i;
            do {
                i = read(b, 0, 1);
            } while (i == 0);

            if (i == -1)
                return -1;
            else
                return b[0] & 0xFF;
        }

        public long skip(long n) throws IOException {
            checkIfClosed();
            this.seek(position + n);
            return n;
        }

        public int read(byte buf[], int off, int len) throws IOException {

            checkIfClosed();

            if (position >= size())
                return -1;

            int chunk = (int) (position / chunkSize);
            int posInChunk = (int) (position % chunkSize);

            if (chunk != lastInflatedChunk) {

                Chunk p = header.chunkList.get(chunk);

                compressed_size = (int) (p.object_PC_end - p.object_PC_ini);

                compressed_buffer = new byte[compressed_size];
                readBytesFromRelativeFilePos(compressed_buffer, p.object_PC_ini, compressed_size);

                inflater.reset();
                inflater.setInput(compressed_buffer, 0, compressed_size);
                try {
                    uncompressed_size = inflater.inflate(uncompressed_buffer);

                } catch (DataFormatException e) {
                    throw new IOException(e);
                }

                lastInflatedChunk = chunk;
            }

            int available = uncompressed_size - posInChunk;
            int copyLen = len > available ? available : len;
            System.arraycopy(uncompressed_buffer, posInChunk, buf, off, copyLen);
            position += copyLen;

            return copyLen;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                inflater.end();
            }
        }

        private void checkIfClosed() throws IOException {
            if (closed) {
                throw new IOException("InputStream already closed.");
            }
        }

    }

}

class Property {

    private String value = "";

    Property(String v) {
        this.value = v;
    }

    public String getValue() {
        return this.value;
    }

}

class Chunk {

    public long object_PC_ini = 0L;
    public long object_PC_end = 0L;

    public Chunk(long ini, long fim) {
        this.object_PC_ini = ini;
        this.object_PC_end = fim;
    }

}

class AD1ExtractorException extends IOException {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public AD1ExtractorException() {

    }

    public AD1ExtractorException(Exception source) {
        super(source);
    }

    public AD1ExtractorException(String message) {
        super(message);
    }

    public AD1ExtractorException(Throwable cause) {
        super(cause);
    }

    public AD1ExtractorException(String message, Throwable throwable) {
        super(message, throwable);
    }

}