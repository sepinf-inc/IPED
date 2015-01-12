package gpinf.dev.data;

import java.io.Serializable;
import java.util.Arrays;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Define um nó de uma estrutura de árvore que armazena caminhos (pastas) de
 * arquivos. A idéia de utilizar uma árvore é de reaproveitar partes das String
 * que se repetem quando há vários arquivos em uma mesma pasta, economizando
 * espaço de armazenamento. Por exemplo, dois arquivos "xxx\yyy\aaa.txt" e
 * "xxx\yyy\bbb.txt" são armazenados como ("xxx") <-> ("yyy") <-> ("aaa.txt",
 * "bbb.txt"). Além disso esta estrutura permite uma navegação biderecional (pai
 * <-> filhos) das pastas.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class PathNode implements Serializable, Comparable<PathNode> {
	/** Identificador utilizado para serialização da classe. */
	private static final long serialVersionUID = -222554843457545L;

	/** Delimitadores considerados na separação de diretórios. */
	private static final String[] delimiters = new String[] { "\\", "/", ">>" };

	/** Nome do nó (pasta). */
	private String name;

	/** Índice do delimitador imediatamente anterior. */
	private int delimiterIndex = -1;

	/** Nó pai. */
	private PathNode parent;

	/**
	 * Descendentes do nó atual. É utilizado um mapa que associa nome dos nós
	 * filhos com os próprios nós filhos para permitir a obtenção dos mesmos a
	 * partir do nome.
	 */
	private SortedMap<String, PathNode> childsMap;

	/** Arrays de filhos. Criado sob demanda, a partir do mapa de nós filhos. */
	private PathNode[] childs;

	/** Quantidade de descendentes. */
	private int descendantsCount = 0;

	/**
	 * Cria um nó raiz.
	 * 
	 * @param name
	 *            nome do nó raiz
	 */
	public PathNode(String name) {
		this.name = name;
		childsMap = new TreeMap<String, PathNode>();
	}

	/**
	 * Construtor privado para nós "não-raiz". Utilizado pelo método de adição
	 * de novos nós a um nó.
	 * 
	 * @param name
	 *            nome do novo nó
	 * @param parent
	 *            nó pai
	 */
	private PathNode(String name, PathNode parent) {
		this.name = name;
		this.parent = parent;
		if (parent.childsMap == null) {
			parent.childsMap = new TreeMap<String, PathNode>();
		}
		parent.childsMap.put(name, this);
	}

	/**
	 * Obtém o nó pai do arquivo.
	 */
	public PathNode getParent() {
		return parent;
	}

	/**
	 * Cria um novo nó, adicionando à estrutura do nó atual.
	 * 
	 * @param path
	 *            Caminho completo do novo nó (xxx\yyy\zzz).
	 * @return novo nó criado
	 */
	public PathNode addNewPath(String path) {
		PathNode curr = this;
		int p1 = 0;
		int psep = 0;
		int di = -1;
		while (p1 < path.length()) {
			int p2 = path.length();
			int ni = -1;
			for (int i = 0; i < delimiters.length; i++) {
				String separator = delimiters[i];
				int p3 = path.indexOf(separator, p1);
				if (p3 >= 0 && p3 < p2) {
					p2 = p3;
					psep = separator.length();
					ni = i;
				}
			}
			String name = path.substring(p1, p2);

			PathNode next = (curr.childsMap != null) ? curr.childsMap.get(name) : null;
			if (next == null)
				next = new PathNode(name, curr);

			curr = next;
			curr.delimiterIndex = di;
			p1 = p2 + psep;
			di = ni;
		}
		return curr;
	}

	/**
	 * @return String com nome do nó formatado
	 */
	@Override
	public String toString() {
		return name;
	}

	/**
	 * Retorna o caminho completo corresponte ao nó atual, percorrendo a árvore
	 * na order inversa.
	 * 
	 * @return String com o caminho completo correspondente
	 */
	public String getFullPath() {
		StringBuilder sb = new StringBuilder();
		PathNode curr = this;
		while (curr.parent != null) {
			sb.insert(0, curr.name);
			if (curr.delimiterIndex >= 0)
				sb.insert(0, delimiters[curr.delimiterIndex]);
			curr = curr.parent;
		}
		return sb.toString();
	}

	/**
	 * Compara com outro objeto. Nome e pai igual determinam que os objetos
	 * desta classe são iguais.
	 * 
	 * @param obj
	 *            outro objeto a ser comparado.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == null || obj.getClass() != PathNode.class)
			return false;
		PathNode pn = (PathNode) obj;
		if (pn == this)
			return true;
		return name.equals(pn.name) && ((parent == null && pn.parent == null) || (parent != null && parent.equals(pn.parent)));
	}

	/**
	 * Retorna a quantidade de filhos deste nó.
	 * 
	 * @return quantidade de filhos
	 */
	public int getChildCount() {
		return childs.length;
	}

	/**
	 * Retorna a quantidade de descendentes deste nó.
	 * 
	 * @return quantidade de nós que estão "abaixo" na hierarquia.
	 */
	public int getDescendantsCount() {
		return descendantsCount;
	}

	/**
	 * Retorna o filho em uma dada posição.
	 * 
	 * @param índice
	 *            requisitado
	 * @return nó filho correspondente a um índice
	 */
	public PathNode getChild(int pos) {
		return childs[pos];
	}

	/**
	 * Retorna a posição de um determinado nó filho.
	 * 
	 * @param nó
	 *            requisitado
	 * @return índice correspondente a um nó
	 */
	public int getIndexOfChild(PathNode child) {
		return Arrays.binarySearch(childs, child);
	}

	/**
	 * Verifica se o nó é descendente de outro nó.
	 * 
	 * @param node
	 *            nó ancestral
	 * @return <code>true</code> se este nó estiver abaixo no mesmo ramo da
	 *         árvore de hierarquia de nós, senão <code>false</code>.
	 */
	public boolean isDescendantOf(PathNode node) {
		PathNode curr = this;
		while (curr != null) {
			if (curr.equals(node))
				return true;
			curr = curr.parent;
		}
		return false;
	}

	/**
	 * Compara um nó com outro, utilizando ordenação por nome dentro de uma
	 * mesma pasta.
	 * 
	 * @param pn
	 *            outro nó a ser comparado
	 * @return <0 se este nó for menor que o outro, 0 se for igual e >0 se for
	 *         maior.
	 */
	@Override
	public int compareTo(PathNode pn) {
		if ((parent == null && pn.parent == null) || (parent != null && parent.equals(pn.parent)))
			return name.compareToIgnoreCase(pn.name);
		if (parent == null)
			return -1;
		if (pn.parent == null)
			return 1;
		return parent.compareTo(pn.parent);
	}

	/**
	 * @return o hashCode, calculado a partir do nome do nó.
	 */
	@Override
	public int hashCode() {
		int hc = (name.hashCode() << 31);
		if (parent != null)
			hc ^= parent.hashCode();
		return hc;
	}

	/**
	 * Constrói o array de filhos, a partir do mapa de filhos. Chama
	 * recursivamente a atualização para os filhos.
	 */
	public void buildChildArray() {
		if (childs != null)
			return;
		int nonEmptyChildsCount = 0;
		for (PathNode pn : childsMap.values()) {
			if (pn.childsMap != null && pn.childsMap.size() > 0) {
				nonEmptyChildsCount++;
			}
		}
		childs = new PathNode[nonEmptyChildsCount];
		int i = 0;
		for (PathNode pn : childsMap.values()) {
			if (pn.childsMap != null && pn.childsMap.size() > 0) {
				childs[i++] = pn;
				pn.buildChildArray();
				descendantsCount += pn.descendantsCount;
			} else {
				descendantsCount++;
			}
		}
		Arrays.sort(childs);
	}
}
