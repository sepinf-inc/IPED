package macee.annotations.filter;

import java.lang.annotation.*;

/**
 * Allows an examination component to filter items (include or exclude) by category and more
 * specifically by the given MIME types. Default values accept every item. If the category or mime
 * types are not specified, all are accepted.
 * 
 * COMENTÁRIO (Werneck): o objetivo aqui é que quem for implementar um plugin/módulo não precise
 * fazer filtros amarrados a um query Lucene, por exemplo. Se eu só quero trabalhar com uma
 * categoria, porque vou receber itens de outra? Pode servir também para obter itens de uma
 * fila de itens pendentes (dar um peek() antes de pegar o item).
 * 
 * IMPORTANTE: requer que categorias "canônicas" estejam registradas em um Enum
 * e algum outro método para registrar categorias novas. Não pensei no tratamento
 * de categorias hierarquizadas.
 *
 * @author WERNECK
 */
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface CategoryFilter {

    FilterOperation filter() default FilterOperation.INCLUDE_ONLY;

    // Filter categories by name
    String[] category() default {};

    // Filter specific mime types
    String[] mimeTypes() default {};
}
