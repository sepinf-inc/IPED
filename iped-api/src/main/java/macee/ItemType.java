package macee;

/**
 * Predefined item types.
 * 
 * COMENTÁRIO (Werneck): pode ser usado na filtragem de itens por módulos de processamento. Ex.: ignorar
 * links, não processar slack, etc. É necessário verificar se o Sleuth fornece essas informações.
 *
 * @author WERNECK
 */
public enum ItemType {

    SLACK_SPACE, UNALLOCATED_SPACE, REGULAR_FILE, DIRECTORY, SYMBOLIC_LINK, HARD_LINK, /**
     * If the item was generated from another and does not correspond to a physical item in the data
     * source.
     */
    VIRTUAL_ITEM, /**
     * Special files include pipes, sockets and other items of the sort.
     */
    SPECIAL_FILE
}
