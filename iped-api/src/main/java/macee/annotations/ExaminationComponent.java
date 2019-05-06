package macee.annotations;

import java.lang.annotation.*;
import macee.annotations.components.ComponentType;

/**
 * Uma classe anotada com ExaminationComponent define um tipo de componente (Carver, Parser, Reporter, etc.)
 * que está associado a uma determinada fase do processo.
 * 
 * COMENTÁRIO (Werneck): não confundir com a classe Component de macee.core.
 * O guid serve para identificar unicamente o componente.
 * Não gostei muito do fato dessa anotação amarrar o tipo de componente, a fase e o modo
 * de partição a uma classe. Acho que o local de aplicação dessas anotações
 * pode ser alterada para METHOD, permitindo que uma classe anotada com ExaminationComponent
 * possa misturar uma grande variedade de componentes distintos como Query, Script, Filter, Carver, etc.
 *
 *
 * @author WERNECK
 */
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface ExaminationComponent {

    String name();

    String guid();

    String version() default "1.0";

    ComponentType type() default ComponentType.OTHER;

    ExaminationPhase phase() default ExaminationPhase.EXAMINATION;

    PartitionMode partition() default PartitionMode.PER_ITEM;
}
