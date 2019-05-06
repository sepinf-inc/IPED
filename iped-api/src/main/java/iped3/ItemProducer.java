package iped3;

import iped3.datasource.DataSourceReader;
import iped3.process.Manager;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * (INTERFACE DO IPED)
 * Responsável por instanciar e executar o contador e o produtor de itens do caso que adiciona os
 * itens a fila de processamento. Podem obter os itens de diversas fontes de dados: pastas,
 * relatórios do FTK, imagens forenses ou casos do IPED.
 *
 */
public interface ItemProducer {

  String currentDirectory();

  CaseData getCaseData();

  DataSourceReader getCurrentReader();

  List<File> getDatasources();

  Manager getManager();

  File getOutput();

  ArrayList<DataSourceReader> getSourceReaders();

  boolean isListOnly();

  void run();
  
}
