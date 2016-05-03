package gpinf.dev.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Classe que define dados pertencentes a um Bookmark, que é uma categoria sob a qual os arquivos
 * extraídos são organizados.
 *
 * @author Wladimir Leite (GPINF/SP)
 */
public class Bookmark implements Serializable {

  /**
   * Identificador utilizado para serialização da classe.
   */
  private static final long serialVersionUID = 1974121400190027L;

  /**
   * Nome do bookmark.
   */
  private final String name;

  /**
   * Descrição do bookmark.
   */
  private final String descr;

  /**
   * Nome do arquivo no qual os arquivos pertencentes a este bookmark (categoria) estão listados.
   */
  private final String fileName;

  /**
   * Arquivos de evidência associados a este bookmark.
   */
  private final List<EvidenceFile> evidenceFiles = new ArrayList<EvidenceFile>();

  /**
   * @param name nome do bookmark
   * @param descr descrição do bookmark
   * @param fileName nome do arquivo no qual os arquivos de book mark estão listados
   */
  public Bookmark(String name, String descr, String fileName) {
    this.name = name;
    this.descr = descr;
    this.fileName = fileName;
  }

  /**
   * Adiciona um arquivo de evidência.
   *
   * @param evidenceFile arquivo a ser adicionado
   */
  public void addEvidenceFile(EvidenceFile evidenceFile) {
    evidenceFiles.add(evidenceFile);
  }

  /**
   * Obtém lista de arquivos de evidência associados a este bookmark.
   *
   * @return lista não modificável de arquivos.
   */
  public List<EvidenceFile> getEvidenceFiles() {
    return Collections.unmodifiableList(evidenceFiles);
  }

  /**
   * Obtém a descrição do bookmark.
   *
   * @return descrição do bookmark
   */
  public String getDescr() {
    return descr;
  }

  /**
   * Obtém o nome do arquivo do bookmark.
   *
   * @return o nome do arquivo que lista os arquivos pertencentes a este bookmark
   */
  public String getFileName() {
    return fileName;
  }

  /**
   * Obtém o nome do bookmark.
   *
   * @return nome do bookmark
   */
  public String getName() {
    return name;
  }

  /**
   * Retorna representação em texto do bookmark.
   */
  @Override
  public String toString() {
    return name + " (" + evidenceFiles.size() + ")";
  }
}
