package macee.annotations;

import java.util.UUID;

/**
 * Defines the examination phases. COMENTÁRIO (Werneck): serve apenas para
 * agrupar os componentes em "fases". A interface Comparable serve apenas para
 * ordenar as fases segundo a metodologia do MACEE (visualmente). Como a
 * metodologia é iterativa, não significa que um processo de correlação não
 * possa ser executado antes da anotação ou exame. Falta incluir texto para
 * internacionalização.
 *
 * @author WERNECK
 */
public enum ExaminationPhase implements Comparable<ExaminationPhase> {

    /**
     * The preparation phase is placeholder for anything that happens before the
     * examination.
     */
    PREPARATION("b7a67401-27c0-4771-9063-c83a6dea969a", 0), IDENTIFICATION("c907c990-0f4d-4645-9521-6bedd37bb60b",
            1), REDUCTION("58fe7b93-f688-4342-9a25-0747c3438022", 2), EXTRACTION("c4b68702-d9eb-45d3-84d2-e42f6aca21f7",
                    3), EXAMINATION("1b4265b9-a522-44c4-9b32-28c2f63fd5dc", 4), ANNOTATION(
                            "560ea5a0-e7eb-475b-9260-fd667c3d15b3",
                            5), CORRELATION("94980dd1-38b0-4927-a002-33da2dc1185c", 6), REPORTING(
                                    "f2db7937-13fc-48f7-93e8-1c5310ef72fe",
                                    7), FEEDBACK("f0229628-e990-4a8e-ab07-d4e83faab39b", 8), CLOSING(
                                            "263d23c1-7518-43bd-993f-920aefcf7911",
                                            9), ERROR_HANDLING("5a63c115-de75-4834-9010-73a941b3bdae", -1);

    private final UUID guid;
    private final int order;

    ExaminationPhase(final String guid, int order) {
        this.guid = UUID.fromString(guid);
        this.order = order;
    }

    public UUID guid() {
        return this.guid;
    }

    public int getOrder() {
        return this.order;
    }
}
