package macee.annotations.filter;

import java.util.List;

/**
 * Predefined item categories backed by an RDF class.
 * 
 * COMENTÁRIO (Werneck): anteriormente era um Enum, mas para dar mais flexibilidade,
 * a classe pode ser carregada de um JSON/XML/config externo. A vinculação a uma
 * classe RDF serve para fins semânticos e de hierarquia. O nome de exibição poderia
 * incluir um valor default (displayName) e uma referência para internacionalização.
 * 
 * IMPORTANTE: falta incluir a relação entre categorias (subTypeOf).
 *
 * @author WERNECK
 */
public class ItemCategory {

    private final String rdfClass;
    private final String name;
    private List<String> mimeTypes;

    private ItemCategory(String displayName, String rdfClass) {
        this.name = displayName;
        this.rdfClass = rdfClass;
    }

    public String type() {
        return this.rdfClass;
    }

    public String displayName() {
        return this.name;
    }

    private List<String> mimeTypes() {
        return mimeTypes;
    }
}
