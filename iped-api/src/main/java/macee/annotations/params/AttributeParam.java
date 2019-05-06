package macee.annotations.params;

import java.lang.annotation.*;

/**
 * Um ExaminationMethod anotado com AttributeParam é chamado passando o atributo nomeado
 * para o método. Ex.: um método que acessa somente as coordenadas de LAT/LONG de uma
 * imagem pode ser definido como {@code
 * void consultaInteligeo(@AttributeParam String lat, @AttributeParam String long)
 * }.
 * 
 * Classes como CaseItem e ItemRef não precisam de anotações. Elas são inseridas na invocação
 * pelo tipo. Do exemplo anterior:
 * {@code
 * void consultaInteligeo(CaseItem item, @AttributeParam String lat, @AttributeParam String long)
 * }
 * 
 * COMENTÁRIO (Werneck): para dar mais segurança à chamada, o valor poderia vir
 * de um Enum com os campos válidos. No entanto, é necessário considerar
 * que plugins podem introduzir novos atributos. IMPORTANTE: creio que não existe
 * mecanismo no momento para registrar atributos (seja via anotação ou arquivo externo).
 * 
 */
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER)
public @interface AttributeParam {

    String value(); // attribute name
}
