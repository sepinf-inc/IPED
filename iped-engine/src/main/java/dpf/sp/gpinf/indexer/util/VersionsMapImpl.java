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
package dpf.sp.gpinf.indexer.util;

import java.io.Serializable;

import iped3.VersionsMap;

/*
 * Classe que mantém mapa bidirecional de versões de visualização <-> original.
 * Array interno que faz o mapeamento tem tamanho igual ao total de itens, 
 * incluindo aqueles sem versão alternativa. Essa abordagem tem consumo de memória 
 * aceitável para casos com até 10 milhões de itens (40 MB) e consome mto menos que um 
 * HashMap em casos com milhões de versões de visualização.
 * Valores 0 internamente indicam que documento não tem versão alternativa.
 * Mapa de visualização -> original usa valores positivos internamente.
 * Mapa de original -> visualização usa valores negativos internamente.
 * 
 */
public class VersionsMapImpl implements Serializable, VersionsMap {

  /**
   *
   */
  private static final long serialVersionUID = 1L;
  private int[] viewToRaw;
  private int mappings = 0;

  public VersionsMapImpl(int size) {
    viewToRaw = new int[size];
  }

  public void put(int view, int raw) {
    viewToRaw[view] = raw + 1;
    viewToRaw[raw] = -view - 1;
    mappings++;
  }

  public int getMappings() {
    return mappings;
  }

  public boolean isRaw(int doc) {
    return mappings == 0 || viewToRaw[doc] < 0;
  }

  public boolean isView(int doc) {
    return mappings != 0 && viewToRaw[doc] > 0;
  }

  public Integer getView(int raw) {
    if (mappings != 0 && viewToRaw[raw] < 0) {
      return -viewToRaw[raw] - 1;
    } else {
      return null;
    }
  }

  public Integer getRaw(int view) {
    if (mappings != 0 && viewToRaw[view] > 0) {
      return viewToRaw[view] - 1;
    } else {
      return null;
    }
  }

}
