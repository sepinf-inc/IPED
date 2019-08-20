package iped3.process;

import iped3.ICaseData;
import java.io.File;
import org.apache.lucene.index.IndexWriter;

/**
 * (INTERFACE DO IPED) Classe responsável pela preparação do processamento,
 * inicialização do contador, produtor e consumidores (workers) dos itens,
 * monitoramento do processamento e pelas etapas pós-processamento.
 *
 * O contador apenas enumera e soma o tamanho dos itens que serão processados,
 * permitindo que seja estimado o progresso e término do processamento.
 *
 * O produtor obtém os itens a partir de uma fonte de dados específica
 * (relatório do FTK, diretório, imagem), inserindo-os numa fila de
 * processamento com tamanho limitado (para limitar o uso de memória).
 *
 * Os consumidores (workers) retiram os itens da fila e são responsáveis pelo
 * seu processamento. Cada worker executa em uma thread diferente, permitindo o
 * processamento em paralelo dos itens. Por padrão, o número de workers é igual
 * ao número de processadores disponíveis.
 *
 * Após inicializar o processamento, o manager realiza o monitoramento,
 * verificando se alguma exceção ocorreu, informando a interface sobre o estado
 * do processamento e verificando se os workers processaram todos os itens.
 *
 * O pós-processamento inclui a pré-ordenação das propriedades dos itens, o
 * armazenamento do volume de texto indexado de cada item, do mapeamento indexId
 * para id, dos ids dos itens fragmentados, a filtragem de categorias e
 * palavras-chave e o log de estatísticas do processamento.
 *
 */
public interface IManager {

    void deleteTempDir();

    ICaseData getCaseData();

    File getIndexTemp();

    IndexWriter getIndexWriter();

    void initSleuthkitServers(final String dbPath) throws InterruptedException;

    boolean isProcessingFinished();

    boolean isSearchAppOpen();

    int numItensBeingProcessed();

    void process() throws Exception;

    void setProcessingFinished(boolean isProcessingFinished);

    void setSearchAppOpen(boolean isSearchAppOpen);

}
