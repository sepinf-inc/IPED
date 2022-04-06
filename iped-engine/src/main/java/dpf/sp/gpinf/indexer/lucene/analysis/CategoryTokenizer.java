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
package dpf.sp.gpinf.indexer.lucene.analysis;

import org.apache.lucene.analysis.util.CharTokenizer;

/*
 * Tokenizador específico para a propriedade 'categoria'. Um item pode ver várias categorias,
 * as quais são separadas pelo caracter SEPARATOR.
 */
public class CategoryTokenizer extends CharTokenizer {

    public static final char SEPARATOR = 0x00;

    public CategoryTokenizer() {
        super();
    }

    @Override
    protected boolean isTokenChar(int c) {
        if (c == SEPARATOR) {
            return false;
        } else {
            return true;
        }
    }

}
