package macee.core.extension;

import java.lang.reflect.Method;

/**
 * Construído a partir de reflection/package scan dos métodos anotados com
 * ExtensionProvider. A parametrização pode ser substituída por Extensible.
 */
public interface ExtensionDescriptor<T> {

    ExtensionProvider getProvider();

    Method getMethod();

    T getOwner();
}
