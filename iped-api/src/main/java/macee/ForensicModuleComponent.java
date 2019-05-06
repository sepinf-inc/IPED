package macee;

import java.lang.reflect.Method;
import macee.core.Module;

/**
 * Defines a component of a ForensicModule.
 * 
 * COMENTÁRIO (Werneck): é criado a partir de métodos anotados com
 * ExaminationComponent. o Method anotado será invocado durante o processamento.
 * Module (não deveria ser ForensicModule?) é a instância cujo método será
 * invocado: method.invoke(module, args)
 * 
 * A nomenclatura está confusa, porque ForensicModuleComponent deveria ser
 * um componente de ForensicModule, mas a relação não está clara e se mistura
 * com ExaminationProcedure e ExaminationComponent (que pode
 * anotar uma classe qualquer e não só um Module!). A parametrização <T>
 * provavelmente não agrega valor. Talvez Module possa ser trocado
 * por Object já que a função é só invocar o método.
 *
 * Talvez possamos armazenar a classe que originalmente estava anotada para
 * fins de casting caso seja necessário.
 * 
 * @param T the type of component
 */
public class ForensicModuleComponent<T> {

    protected final Method method;
    protected final Module module;
    protected final T component;

    public ForensicModuleComponent(Method method, Module module, T component) {
        this.method = method;
        this.module = module;
        this.component = component;
    }

    public Method getMethod() {
        return method;
    }

    public Module getModule() {
        return module;
    }

    public T getComponent() {
        return component;
    }
}
