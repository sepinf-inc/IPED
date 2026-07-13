package iped.app.ui.ai;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import iped.engine.data.SimpleFilterNode;
import iped.localization.LocaleResolver;

public class AIFiltersLocalization {
    private static final String bundleName = "iped-ai-filters";
    private static ResourceBundle resourceBundle;

    private AIFiltersLocalization() {
    }

    public static String get(SimpleFilterNode node) {
        if (resourceBundle == null) {
            synchronized (AIFiltersLocalization.class) {
                if (resourceBundle == null) {
                    resourceBundle = iped.localization.Messages.getExternalBundle(bundleName,
                            LocaleResolver.getLocale());
                }
            }
        }
        try {
            return resourceBundle.getString(node.getFullName());
        } catch (MissingResourceException e) {
            return node.getName();
        }
    }
}
