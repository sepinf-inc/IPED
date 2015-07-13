/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.search;

import java.awt.event.ActionListener;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class MenuClass extends JPopupMenu {

	private static final long serialVersionUID = 1L;

	JMenuItem exportarSelecionados, copiarSelecionados, marcarSelecionados, desmarcarSelecionados, lerSelecionados, deslerSelecionados, exportarMarcados, copiarMarcados, salvarMarcadores,
			carregarMarcadores, aumentarGaleria, diminuirGaleria, disposicao, copiarPreview, gerenciarMarcadores, limparBuscas, importarPalavras, navigateToParent, exportTerms;

	// JCheckBoxMenuItem changeViewerTab;

	public MenuClass() {
		super();

		ActionListener menuListener = new MenuListener(this);

		
		marcarSelecionados = new JMenuItem("Selecionar itens destacados");
		marcarSelecionados.addActionListener(menuListener);
		this.add(marcarSelecionados);

		desmarcarSelecionados = new JMenuItem("Remover seleção dos itens destacados");
		desmarcarSelecionados.addActionListener(menuListener);
		this.add(desmarcarSelecionados);

		/*lerSelecionados = new JMenuItem("Marcar selecionados como lido");
		lerSelecionados.addActionListener(menuListener);
		this.add(lerSelecionados);

		deslerSelecionados = new JMenuItem("Marcar selecionados como novo");
		deslerSelecionados.addActionListener(menuListener);
		this.add(deslerSelecionados);
		*/

		//this.addSeparator();
		
		gerenciarMarcadores = new JMenuItem("Gerenciar Marcadores");
		gerenciarMarcadores.addActionListener(menuListener);
		this.add(gerenciarMarcadores);
		

		salvarMarcadores = new JMenuItem("Salvar marcadores");
		salvarMarcadores.addActionListener(menuListener);
		this.add(salvarMarcadores);

		carregarMarcadores = new JMenuItem("Carregar marcadores");
		carregarMarcadores.addActionListener(menuListener);
		this.add(carregarMarcadores);
		
		this.addSeparator();

		exportarSelecionados = new JMenuItem("Exportar itens destacados");
		exportarSelecionados.addActionListener(menuListener);
		this.add(exportarSelecionados);

		exportarMarcados = new JMenuItem("Exportar itens selecionados");
		exportarMarcados.addActionListener(menuListener);
		this.add(exportarMarcados);

		this.addSeparator();

		copiarSelecionados = new JMenuItem("Exportar propriedades dos itens destacados");
		copiarSelecionados.addActionListener(menuListener);
		this.add(copiarSelecionados);

		copiarMarcados = new JMenuItem("Exportar propriedades dos itens selecionados");
		copiarMarcados.addActionListener(menuListener);
		this.add(copiarMarcados);

		this.addSeparator();
		
		importarPalavras = new JMenuItem("Importar lista de palavras-chave");
		importarPalavras.addActionListener(menuListener);
		this.add(importarPalavras);
		
		limparBuscas = new JMenuItem("Limpar expressões pesquisadas");
		limparBuscas.addActionListener(menuListener);
		this.add(limparBuscas);
		
		exportTerms = new JMenuItem("Exportar lista de termos indexados");
		exportTerms.addActionListener(menuListener);
		this.add(exportTerms);
		
		this.addSeparator();
		
		disposicao = new JMenuItem("Alterar disposição vertical/horizontal");
		disposicao.addActionListener(menuListener);
		this.add(disposicao);

		copiarPreview = new JMenuItem("Copiar imagem do visualizador");
		copiarPreview.addActionListener(menuListener);
		this.add(copiarPreview);
		
		aumentarGaleria = new JMenuItem("Alterar nº colunas da galeria");
		aumentarGaleria.addActionListener(menuListener);
		this.add(aumentarGaleria);
		
		if(!App.get().isReport){
			navigateToParent = new JMenuItem("Navegar para item pai na árvore");
			navigateToParent.addActionListener(menuListener);
			this.add(navigateToParent);
		}
		
		
	}

}
