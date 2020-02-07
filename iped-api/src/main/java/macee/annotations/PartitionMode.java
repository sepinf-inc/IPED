package macee.annotations;

/**
 * Defines how a task can be partitioned and fed to an examination component.
 * Some components can deal only with single files, while others need a whole
 * directory or volume. The dataset mode indicates a selection of one or more
 * specific items.
 * 
 * COMENTÁRIO (Werneck): acho que não será mais necessário se utilizarmos o
 * modelo de produtor/consumidor do IPED com múltiplas filas de processamento.
 * Essa divisão foi pensada para execução de processos específicos e
 * distribuídos (para manter colocação de dados) e não para um pipeline. Talvez
 * ainda seja útil para chamadas de processamento adicional.
 *
 * @author WERNECK
 */
public enum PartitionMode {
    /**
     * The task can be executed on each item separately.
     */
    PER_ITEM,
    /**
     * The task requires the item and its children to be examined together.
     */
    PER_ITEM_WITH_CHILDREN,
    /**
     * The task needs to be executed per evidence
     */
    PER_DATA_SOURCE,
    /**
     * The task needs to be executed in the case as a whole
     */
    NO_PARTITION
}
