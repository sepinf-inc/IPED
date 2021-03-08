package gpinf.dev.filetypes;

import iped3.IItem;

import java.io.File;
import java.util.List;

/**
 * Implementação da classe utilizada para arquivos não suportados
 * (desconhecidos).
 *
 * @author Wladimir Leite (GPINF/SP)
 */
public class GenericFileType extends EvidenceFileType {

    /**
     * Identificador para serialização.
     */
    private static final long serialVersionUID = 17897987897L;

    /**
     * Ícone associado ao tipo. * private static final Icon icon =
     * IconUtil.createIcon("ft-unknown");
     *
     * /** Descrição do tipo de arquivo.
     */
    private String descr;

    /**
     * Construtor que recebe a descrição do tipo de arquivo.
     *
     * @param descr
     *            Descrição a ser utilizada pélo método de obtenção da descrição.
     */
    public GenericFileType(String descr) {
        this.descr = descr;
    }

    /**
     * Retorna a descrição fornecida no construtor.
     *
     * @return descrição do tipo de arquivo
     */
    @Override
    public String getLongDescr() {
        return descr;
    }

    /**
     * Processa arquivos deste tipo.
     *
     * @param baseDir
     *            diretório base onde arquivo de evidência exportados estão
     *            armazenados
     * @param items
     *            lista de arquivos a ser processada
     */
    @Override
    public void processFiles(File baseDir, List<IItem> items) {

    }

    /**
     * Retorna o ícone correspondente ao tipo de arquivo. * public Icon getIcon() {
     * return icon; }
     */
}
