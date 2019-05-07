package iped3.process;

import iped3.Item;

/**
 * Responsável por retirar um item da fila e enviá-lo para cada tarefa de
 * processamento instalada: análise de assinatura, hash, expansão de itens,
 * indexação, carving, etc.
 *
 * São executados vários Workers paralelamente. Cada Worker possui instâncias
 * próprias das tarefas, para evitar problemas de concorrência.
 *
 * Caso haja uma exceção não esperada, ela é armazenada para que possa ser
 * detectada pelo manager.
 */
public interface Worker {

    void finish() throws Exception;

    /**
     * Processa o item em todas as tarefas instaladas. Caso ocorra exceção não
     * esperada, armazena exceção para abortar processamento.
     *
     * @param evidence
     *            Item a ser processado
     */
    void process(Item evidence);

    /**
     * Processa ou enfileira novo item criado (subitem de zip, pst, carving, etc).
     *
     * @param evidence
     *            novo item a ser processado.
     */
    void processNewItem(Item evidence);

    void processNextQueue();

    void run();

}
