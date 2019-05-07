package macee.annotations.components;

import java.lang.annotation.*;

/**
 * Identifies items in the case and notifies the user.
 *
 * @author WERNECK
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Identifier {

    static final ComponentType TYPE = ComponentType.IDENTIFIER;

    /**
     * Notification category.
     *
     * @return Notification category.
     */
    String category() default "";

    /**
     * The notification level.
     *
     * @return The notification level.
     */
    NotificationLevel level() default NotificationLevel.INFORM;

    /**
     * An URL to a technical reference (such as a web page or wiki article) related
     * to what was identified.
     *
     * @return the technical reference related to the identified item.
     */
    String reference() default "";
}
