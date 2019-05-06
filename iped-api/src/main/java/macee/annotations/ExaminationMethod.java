package macee.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotação para um método usado durante o processamento. Combinado com
 * ExaminationPhase e outras anotações.
 * 
 * COMENTÁRIO (Werneck): me parece que o nome pode se confundir com sendo
 * específico da fase chamada de Examination. Talvez seja melhor mudar o nome
 * da fase.
 */
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
public @interface ExaminationMethod {

    public String value();
}
