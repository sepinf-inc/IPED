package sef.mg.laud.ad1extractor;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 *
 * @author guilherme.dutra
 */
public class FileHeader {

    private static final int FILESIZE_CODE = 3;
    private static final int ACCESSED_DATE_CODE = 7;
    private static final int CREATED_DATE_CODE = 8;
    private static final int MODIFIED_DATE_CODE = 9;
    private static final int RECORD_DATE_CODE = 40962;

    private AD1Extractor extractor;

    public FileHeader parent;

    public long object_address = 0L;
    public long endereco_prox_objeto = 0L;
    public long endereco_filho_objeto = 0L;
    public long objeto_PC_fim_parcial = 0L;
    public long objeto_PC_ini_parcial = 0L;
    private long objetoTamanhoBytes = 0L;
    public int objeto_tipo = 0;
    public long nome_objeto_tam = 0L;
    public String objeto_nome = "";
    public long objeto_pedacos_tam = 0L;
    public String caminho = "";
    public List<Pedaco> pedacosList = null;
    public Map<Integer, Propriedade> propriedadesMap = new HashMap<>();

    private SimpleDateFormat simpleDateFormat = null;

    public void setObjetoTamanhoBytes(long tam) {
        this.objetoTamanhoBytes = tam;
    }

    public long getFileSize() {
        return objetoTamanhoBytes;
    }

    public FileHeader(AD1Extractor extractor, FileHeader parent) {
        this.extractor = extractor;
        this.parent = parent;
        pedacosList = new ArrayList<>();
        simpleDateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public FileHeader getNextHeader() throws IOException {
        if (endereco_prox_objeto == 0)
            return null;

        return extractor.lerObjeto(endereco_prox_objeto, parent);
    }

    public FileHeader getChildHeader() throws IOException {
        if (endereco_filho_objeto == 0)
            return null;

        return extractor.lerObjeto(endereco_filho_objeto, this);
    }

    public Date getATime() {

        Propriedade prop = propriedadesMap.get(ACCESSED_DATE_CODE);
        if (prop != null) {
            try {
                return simpleDateFormat.parse(prop.getValor());

            } catch (ParseException e) {
                System.out.println("Error reading ATime from " + getFilePath() + " " + e.toString());
            }
        }

        return null;
    }

    public Date getCTime() {

        Propriedade prop = propriedadesMap.get(CREATED_DATE_CODE);
        if (prop != null) {
            try {
                return simpleDateFormat.parse(prop.getValor());

            } catch (ParseException e) {
                System.out.println("Error reading CTime from " + getFilePath() + " " + e.toString());
            }
        }

        return null;
    }

    public Date getMTime() {

        Propriedade prop = propriedadesMap.get(MODIFIED_DATE_CODE);
        if (prop != null) {
            try {
                return simpleDateFormat.parse(prop.getValor());

            } catch (ParseException e) {
                System.out.println("Error reading MTime from " + getFilePath() + " " + e.toString());
            }
        }

        return null;
    }

    public Date getRTime() {

        Propriedade prop = propriedadesMap.get(RECORD_DATE_CODE);
        if (prop != null) {
            try {
                return simpleDateFormat.parse(prop.getValor());

            } catch (ParseException e) {
                System.out.println("Error reading RTime from " + getFilePath() + " " + e.toString());
            }
        }

        return null;
    }

    public boolean hasChildren() {
        return endereco_filho_objeto != 0;
    }

    public boolean isDirectory() {
        return (objeto_tipo & 0x04) == 4;
    }

    public boolean isDeleted() {
        return (objeto_tipo & 0x02) == 2;
    }

    public String getFilePath() {
        return caminho;
    }

    public String getFileName() {
        return objeto_nome;
    }

    public boolean isEncrypted() {

        return false;

    }

    public void adicionaPedaco(long ini, long fim) {

        pedacosList.add(new Pedaco(ini, fim));

    }

}
