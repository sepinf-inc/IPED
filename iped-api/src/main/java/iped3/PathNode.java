/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3;

import java.io.Serializable;
import java.util.SortedMap;

/**
 *
 * @author WERNECK
 */
public interface PathNode extends Comparable<PathNode>, Serializable {

    /**
     * Cria um novo nó, adicionando à estrutura do nó atual.
     *
     * @param path
     *            Caminho completo do novo nó (xxx\yyy\zzz).
     * @return novo nó criado
     */
    PathNode addNewPath(String path);

    /**
     * Constrói o array de filhos, a partir do mapa de filhos. Chama recursivamente
     * a atualização para os filhos.
     */
    void buildChildArray();

    /**
     * Compara um nó com outro, utilizando ordenação por nome dentro de uma mesma
     * pasta.
     *
     * @param pn
     *            outro nó a ser comparado
     * @return negativo se este nó for menor que o outro, 0 se for igual e positivo
     *         se for maior.
     */
    @Override
    int compareTo(PathNode pn);

    /**
     * Compara com outro objeto. Nome e pai igual determinam que os objetos desta
     * classe são iguais.
     *
     * @param obj
     *            outro objeto a ser comparado.
     */
    @Override
    boolean equals(Object obj);

    /**
     * Retorna o filho em uma dada posição.
     *
     * @param pos
     *            índice requisitado
     * @return nó filho correspondente a um índice
     */
    PathNode getChild(int pos);

    /**
     * Retorna a quantidade de filhos deste nó.
     *
     * @return quantidade de filhos
     */
    int getChildCount();

    /**
     * Retorna a quantidade de descendentes deste nó.
     *
     * @return quantidade de nós que estão "abaixo" na hierarquia.
     */
    int getDescendantsCount();

    /**
     * Retorna o caminho completo corresponte ao nó atual, percorrendo a árvore na
     * order inversa.
     *
     * @return String com o caminho completo correspondente
     */
    String getFullPath();

    /**
     * Retorna a posição de um determinado nó filho.
     *
     * @param child
     *            nó requisitado
     * @return índice correspondente a um nó
     */
    int getIndexOfChild(PathNode child);

    /**
     * Obtém o nó pai do arquivo.
     */
    PathNode getParent();

    /**
     * @return o hashCode, calculado a partir do nome do nó.
     */
    @Override
    int hashCode();

    /**
     * Verifica se o nó é descendente de outro nó.
     *
     * @param node
     *            nó ancestral
     * @return <code>true</code> se este nó estiver abaixo no mesmo ramo da árvore
     *         de hierarquia de nós, senão <code>false</code>.
     */
    boolean isDescendantOf(PathNode node);

    /**
     * Retorna o mapa de objetos filho.
     *
     * @return mapa contendo todos os PathNode filhos.
     */
    public SortedMap<String, PathNode> getChildsMap();

    /**
     * Retorna o nome do nó (pasta).
     */
    public String getName();

    /**
     * @return String com nome do nó formatado
     */
    @Override
    String toString();

}
