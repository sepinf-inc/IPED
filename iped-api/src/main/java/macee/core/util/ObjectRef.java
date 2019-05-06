package macee.core.util;

import java.util.Properties;
import java.util.UUID;

/**
 * An object description with a unique ID.
 * 
 * COMENTÁRIO (Werneck): serve de base para implementar referências a objetos
 * remotos que são identificados por um UUID e tem propriedades associadas.
 *
 * @author WERNECK
 */
public interface ObjectRef extends ObjectDescription, Identity<UUID> {

    String OBJECT_REF_TYPE = "OBJECT";

    default String getRefType() {
        return OBJECT_REF_TYPE;
    }

    String getObjectClass();

    Properties getProperties();

    default String guid() {
        return getId().toString();
    }
}
