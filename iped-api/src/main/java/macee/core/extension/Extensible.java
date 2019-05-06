package macee.core.extension;

import java.util.Set;

/**
 * This interface is used to indicate an extensible object. Extension providers can registered and
 * retrieved.
 *
 * @author Bruno W. P. Hoelz
 */
public interface Extensible {

    /**
     * Registers an extension provider.
     *
     * @param <T>           the extension type.
     * @param extensionType the class representing the extension type.
     * @param descriptor    the extension descriptor.
     */
    <T> void registerExtension(Class<T> extensionType, ExtensionDescriptor descriptor);

    /**
     * Gets the providers of the specified extension type.
     *
     * @param <T>   the extension type.
     * @param clazz the class representing the extension type.
     * @return the set of providers for the given type.
     */
    <T> Set<ExtensionDescriptor> getProviders(Class<T> clazz);

    /**
     * Calls the extension method.
     *
     * @param extension the extension to be called.
     * @throws Exception if an exception occurs while running the extension method.
     */
    void runExtension(ExtensionDescriptor extension) throws Exception;
}
