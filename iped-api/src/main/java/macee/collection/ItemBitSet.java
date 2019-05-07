package macee.collection;

import java.io.Serializable;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Um bitset implementado por T.
 * 
 * COMENTÁRIO (Werneck): remover o parâmetro <T> e diferenciar as implementações
 * concretas. Não dar acesso ao BitSet subjacente (getBitSet).
 */
public interface ItemBitSet<T> extends Serializable {

    void or(ItemBitSet<T> value);

    int cardinality();

    void add(Integer index);

    void remove(int index);

    boolean isEmpty();

    void clear();

    void andNot(ItemBitSet<T> other);

    boolean contains(int id);

    void and(ItemBitSet<T> other);

    void xor(ItemBitSet<T> other);

    Iterator<Integer> iterator();

    default Stream<Integer> stream() {
        Iterable<Integer> iterable = () -> iterator();
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    void flip(Integer id);

    T getBitSet();

    ItemBitSet<T> copy();
}
