package macee.collection;

/**
 * Uma recomendação que indica a relevância de um conjunto de itens.
 * 
 * IMPORTANTE: extrair strings para internacionalização.
 */
public enum Recommendation {

    ALERT(10, "Alertar"), IGNORE(0, "Ignorar"), INFORM(5, "Informar"), WARN(8,
        "Informar com prioridade"), POSTPONE(3, "Adiar análise"), REVIEW_LATER(6,
        "Para revisão posterior");

    private final int value;
    private final String name;

    Recommendation(int value, String name) {
        this.value = value;
        this.name = name;
    }

    public static Recommendation forName(String name) {
        for (Recommendation r : Recommendation.values()) {
            if (r.getName().equals(name)) {
                return r;
            }
        }
        return null;
    }

    public int getValue() {
        return this.value;
    }

    public String getName() {
        return this.name;
    }

    @Override public String toString() {
        return this.getName();
    }
}
