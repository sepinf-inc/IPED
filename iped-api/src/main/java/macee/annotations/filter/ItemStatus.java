package macee.annotations.filter;

/**
 * Predefined item statuses.
 * 
 * COMENTÁRIO (Werneck): poderia ser transformado em classe para dar mais flexibilidade
 * ao registro de outros estados. O status não precisa ser mutuamente exclusivo, já que o objetivo
 * é fornecer uma forma de filtrar itens para os módulos de processamento e não de classficar itens.
 *
 * @author WERNECK
 */
public enum ItemStatus {

    ACTIVE, DELETED, CARVED, IGNORED
}
