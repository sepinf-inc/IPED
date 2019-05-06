package macee.core;

/**
 * Uma aplicação constituída de módulos e que pode gerenciar seus objetos.
 * Possui também um EventBus para comunicação entre módulos.
 * 
 * No lugar (ou além) do EventBus, poderia utilizar também um Flow (Java 9)
 * para que os objetos subscrevam vários fluxos de eventos.
 * https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/Flow.html
 *
 * @param <M> the type of modules used in the application.
 * @author Bruno W. P. Hoelz
 */
public interface App<M extends Module> extends Lifecycle, ObjectManager<M> {

    /**
     * Gets the version of the application.
     *
     * @return the version of the application.
     */
    Version getCurrentVersion();

    /**
     * Gets the event bus.
     *
     * @return the event bus.
     */
    EventBus getEventBus();
}
