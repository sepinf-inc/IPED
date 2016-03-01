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
package dpf.sp.gpinf.indexer;

import org.apache.lucene.util.Version;

/**
 * Classe que contém as constantes da aplicação.
 */
public class Versao {
	
	public static Version current = Version.LUCENE_4_9;
	public static String APP_VERSION = "3.9";
	public static String APP_NAME = "Indexador e Processador de Evidências Digitais " + APP_VERSION;
	public static String APP_EXT = "IPED";
}
