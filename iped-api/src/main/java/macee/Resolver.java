package macee;

/**
 * Interface para resolução de referências. Dado uma referência K, retorna um
 * item V. Por exemplo, uma referência a um item ou fonte de dados (contendo um
 * ID ou GUID) é resolvido para o objeto concreto.
 * 
 * Exemplo: Resolver<ItemRef, CaseItem>
 * 
 * O gerente de itens do caso deve fornecer esse recurso. {@code
 * ItemRef ref = new ItemRef(10543, "hd-01")
 * CaseItem item = caseManager.resolve(ref);
 * }
 * 
 * Comentário (Werneck): usar Optional<V> para o caso da referência ser inválida
 * e lançar exception no método sem Optional?
 */
@FunctionalInterface
public interface Resolver<K, V> {

    V resolve(K key);
}
