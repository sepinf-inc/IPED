package macee.descriptor;

import macee.collection.CaseItemCollection;

public enum CoreTypes {
    // Coleções de itens
    COLLECTION("Coleção", true, CaseItemCollection.class), HIGHLIGHTED_ITEMS("Itens selecionados", false,
            CaseItemCollection.class), CHECKED_ITEMS("Itens marcados", false, CaseItemCollection.class), LISTED_ITEMS(
                    "Itens listados", false, CaseItemCollection.class), IGNORED_ITEMS("Itens ignorados", true,
                            CaseItemCollection.class), BOOKMARK_ITEMS("Itens de interesse", true,
                                    CaseItemCollection.class), TAGGED_ITEMS("Marcadores", true,
                                            CaseItemCollection.class), SEARCH_RESULTS("Resultados de busca", true,
                                                    CaseItemCollection.class), FILTER_RESULTS("Itens filtrados", true,
                                                            CaseItemCollection.class), CATEGORY("Categoria", false,
                                                                    CaseItemCollection.class), REPORT("Relatórios",
                                                                            true,
                                                                            CaseItemCollection.class), KNOWN_ITEMS(
                                                                                    "Itens conhecidos", false,
                                                                                    CaseItemCollection.class), KEYWORDS(
                                                                                            "Palavras-chave", false,
                                                                                            FilterDescriptor.class), TIMELINE(
                                                                                                    "Linhas do tempo",
                                                                                                    true,
                                                                                                    CaseItemCollection.class), // Descritores
                                                                                                                               // específicos
    CASE("Casos", false, CaseDescriptor.class), EVIDENCE_ITEM("Material examinado", false,
            EvidenceItemDescriptor.class), DATA_SOURCE("Fontes de dados", false, DataSourceDescriptor.class), QUERY(
                    "Consultas", true,
                    QueryDescriptor.class), FILTER("Filtros", true, FilterDescriptor.class), COMMAND("Comandos", false,
                            CommandDescriptor.class), SCRIPT("Scripts", true, ScriptDescriptor.class), USER("Usuários",
                                    false, UserDescriptor.class), HELP("Ajuda", false, HelpTopicDescriptor.class), // ENTIDADES/RELACIONAMENTOS
                                                                                                                   // (Análise
    ANNOTATION("Anotações", true, CaseItemCollection.class), ENTITY_CLASS("Tipos de entidades", false,
            EntityDescriptor.class), ENTITY_INSTANCE("Entidades", true, EntityInstanceDescriptor.class);

    private final boolean userCreated;
    private final String label;
    private final Class<? extends Descriptor> descriptorClass;

    private CoreTypes(final String label, final boolean userCreated, final Class<? extends Descriptor> clazz) {
        this.label = label;
        this.userCreated = userCreated;
        this.descriptorClass = clazz;
    }

    // public static DescriptorType forName(final String name) {
    // for (CoreTypes r : CoreTypes.values()) {
    // if (r.getLabel().equals(name)) {
    // return r.toDescriptorType();
    // }
    // }
    // return null;
    // }

    public boolean isUserCreated() {
        return userCreated;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return this.label;
    }

    // public DescriptorType toDescriptorType() {
    // return new DescriptorType(this.label, this.descriptorClass.getName(),
    // this.userCreated);
    // }

    public Class<? extends Descriptor> getDescriptorClass() {
        return this.descriptorClass;
    }

}
