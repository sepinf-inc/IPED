package macee.core;

/**
 * A module is an owner of multiple components that is associated with an
 * application (@link App).
 * 
 * COMENTÁRIO: antes havia um vínculo explícito para os componentes, mas sou
 * mais favorável a definição via anotações.
 *
 * @param <A>
 *            the type of App associated with the module
 * @author Bruno W. P. Hoelz
 */
public interface Module<A extends App> extends Lifecycle {

    /**
     * Return the application instance associated with this module.
     *
     * @return the application instance
     */
    A getApp();

    default void post(Object event) {
        getApp().getEventBus().post(event);
    }

    default void register(Object obj) {
        getApp().getEventBus().register(obj);
    }
}