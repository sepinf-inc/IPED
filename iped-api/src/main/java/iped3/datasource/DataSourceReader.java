/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.datasource;

import java.io.File;

/**
 *
 * @author WERNECK
 */
public interface DataSourceReader {

  /**
   *
   * @return diretório atualmente sendo adicionado ao caso. Útil para informar progresso nos casos
   * em que o processamento só possa ser iniciado após a adição, possivelmente lenta, de todos os
   * itens (ex: sleuthkit).
   */
  String currentDirectory();

  /**
   * @param datasource Fonte de dados
   * @return nome de exibição da fonte de dados informado pelo usuário
   */
  String getEvidenceName(File datasource);

  /**
   * @param datasource fonte de dados
   * @return Se a fonte de dados informada é suportada por este leitor.
   */
  boolean isSupported(File datasource);

  /**
   * Lê a fonte de dados informada. Adiciona os itens na fila de processamento caso listOnly = false
   * usando caseData.addEvidenceFile(). Ou conta e soma o volume dos itens a processar caso listOnly
   * = true para estimar o progresso do processamento usando caseData.incDiscoveredEvidences() e
   * caseData.incDiscoveredVolume().
   *
   * @param datasource Fonte de dados que será processada/lida.
   * @return Número de itens com versões alternativas de visualização. Só deve ser diferente de zero
   * no caso de relatórios do FTK.
   * @throws Exception Caso algum erro inesperado ocorra durante a leitura dos dados
   */
  int read(File datasource) throws Exception;
  
}
