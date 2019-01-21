/*
 * Copyright 2012-2015, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.datasource;

import java.io.File;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import gpinf.dev.data.CaseData;
import gpinf.dev.data.DataSource;
import gpinf.dev.data.EvidenceFile;

/**
 * Classe abstrata base para leitura dos itens de uma fonte de dados (pasta, imagem forense,
 * relatório do FTK, caso do iped). Pode ser estendida para implementar suporte a uma nova fonte de
 * dados (ex: ad1, l01, etc).
 *
 * @author Nassif
 *
 */
public abstract class DataSourceReader {

  /**
   * Objeto com dados do caso
   */
  CaseData caseData;

  /**
   * Indica se os itens serão apenas listados (contados) ou adicionados à fila de processamento
   */
  boolean listOnly;

  /**
   * Pasta de saída do processamento.
   */
  File output;
  
  /**
   * Fonte de dados de origem
   */
  DataSource dataSource;

  /**
   * Construtor.
   *
   * @param caseData Objeto com dados do caso
   * @param output Pasta de saída do processamento
   * @param listOnly Se os itens serão apenas listados(contados) ou adicionados à fila de
   * processamento
   */
  public DataSourceReader(CaseData caseData, File output, boolean listOnly) {
    this.caseData = caseData;
    this.listOnly = listOnly;
    this.output = output;
  }

  /**
   * @param datasource fonte de dados
   * @return Se a fonte de dados informada é suportada por este leitor.
   */
  public abstract boolean isSupported(File datasource);

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
  public abstract int read(File datasource) throws Exception;

  /**
   * Lê a fonte de dados informada considerando a evidencia passada como parent.
   * @param datasource Fonte de dados que será processada/lida.
   * @param parent Evidencia parent.
   * @throws Exception
   * @see DataSourceReader#read(File)
   */
  public void read(File datasource, EvidenceFile parent) throws Exception {
    throw new IllegalAccessException("Method read(File datasource, EvidenceFile parent) not implemented in class "
        + this.getClass().getName() + ".");
  }

  /**
   *
   * @return diretório atualmente sendo adicionado ao caso. Útil para informar progresso nos casos
   * em que o processamento só possa ser iniciado após a adição, possivelmente lenta, de todos os
   * itens (ex: sleuthkit).
   */
  public String currentDirectory() {
    return null;
  }

  /**
   * @param datasource Fonte de dados
   * @return nome de exibição da fonte de dados informado pelo usuário
   */
  public String getEvidenceName(File datasource) {
    CmdLineArgs cmdArgs = ((CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName()));
    if (cmdArgs.getDname() == null || cmdArgs.getDname().size() == 0) {
      return null;
    }
    return cmdArgs.getDataSourceName(datasource);
  }
}
