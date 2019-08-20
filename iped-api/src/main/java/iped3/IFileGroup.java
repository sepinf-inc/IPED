package iped3;

import java.io.Serializable;

/**
 * (INTERFACE DO IPED) Classe que define dados pertencentes a uma grupo de
 * arquivos de evidência, que pode ser uma categoria (bookmark) sob a qual os
 * arquivos extraídos são organizados, ou alguma outra forma de agrupamento dos
 * arquivos de evidência do caso.
 *
 * @author Wladimir Leite (GPINF/SP)
 */
public interface IFileGroup extends Serializable {

    /**
     * Adiciona um arquivo de evidência.
     *
     * @param evidenceFile
     *            arquivo a ser adicionado
     */
    /*
     * public void addEvidenceFile(EvidenceFile evidenceFile) {
     * evidenceFiles.add(evidenceFile); }
     */
    /**
     * Obtém lista de arquivos de evidência associados a este agrupamento.
     *
     * @return lista não modificável de arquivos.
     */
    /*
     * public List<EvidenceFile> getEvidenceFiles() { return
     * Collections.unmodifiableList(evidenceFiles); }
     */
    /**
     * @return descrição do agrupamento
     */
    String getDescr();

    /**
     * Obtém o nome do arquivo do agrupamento.
     *
     * @return o nome do arquivo que lista os arquivos pertencentes a este
     *         agrupamento
     */
    String getFileName();

    /**
     * Obtém o nome do agrupamento.
     *
     * @return nome do agrupamento
     */
    String getName();

    /**
     * Retorna representação em texto do agrupamento.
     */
    @Override
    String toString();

}
