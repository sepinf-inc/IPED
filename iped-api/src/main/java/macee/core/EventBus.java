package macee.core;

/**
 * EventBus generic interface.
 *
 * COMENTÁRIO (Werneck): usado para desvincular a interface da implementação
 * concreta (Guava EventBus ou vert.x EventBus).
 * 
 * @author WERNECK
 */
public interface EventBus {

    void register(Object o);

    void unregister(Object o);

    void post(Object o);
}
