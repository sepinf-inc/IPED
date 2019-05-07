package macee.collection;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Uma coleção apoiada por vários bitsets. Cada bitset possui um nome único.
 * 
 * IMPORTANTE: Essa interface não exige que os bitsets sejam independentes. Por
 * exemplo, os bitsets podem estar relacionados entre si e um item marcado em um
 * bitset pode ter que ser desmarcado em outro. Por outro lado, se são
 * independentes, o item 10 marcado em um bitset não tem relação com o item 10
 * de outro.
 * 
 * IMPORTANTE: bitsets dependentes podem usar operações de stream (zip, flatMap,
 * etc.).
 * 
 * COMENTÁRIO: a atualização de timestamp e versão pode ser colocada em outra
 * interface.
 * 
 * IMPORTANTE: se possível estender Collection do Java, por questões semânticas
 * ou pelo menos Iterable.
 */
public interface BitSetCollection extends Serializable {

    BitSetCollection copy();

    Map<String, ItemBitSet> getBitSets();

    ItemBitSet getBitSet(String name);

    void setBitSet(String bitsetName, ItemBitSet bitSet);

    boolean isEmpty(String bitsetName);

    void removeAll(String bitsetName);

    void removeAll(BitSetCollection toRemove);

    void merge(String bitsetName, ItemBitSet other);

    void subtract(String bitsetName, ItemBitSet other);

    void remove(String bitsetName, ItemBitSet other);

    void applyDiff(String bitsetName, ItemBitSet diff);

    ItemBitSet getDiff(String bitsetName, ItemBitSet other);

    void updateTimestampAndSize();

    void and(BitSetCollection other);

    void or(BitSetCollection other);

    void xor(BitSetCollection other);

    void andNot(BitSetCollection other);

    abstract Iterator<Integer> iterator(String bitsetName);

    Iterator<Integer> iterator();

    int size();

    Set<String> getBitSetNames();

    int size(String bitsetName);

    void setItemCount(int count);

    boolean add(String bitSetName, int id);

    boolean isSet(String bitsetName, int id);

    void subtractFromAll(BitSetCollection toRemove);

    void mergeWithAll(BitSetCollection toAdd);

    void clearAll();

    default boolean isEmpty() {
        return size() == 0;
    }

    void remove(String name, int id);

    String getLastUpdate();

    void setLastUpdate(String preserveLastUpdate);
}
