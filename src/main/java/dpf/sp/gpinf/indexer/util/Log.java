/*
 * Copyright 2015-2015, Wladimir Leite
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classe auxiliar com métodos para impressão no console de mensagens de log.
 *
 * @author Wladimir
 */
public class Log {

  private static Logger LOGGER = LoggerFactory.getLogger(Log.class);

  public static void error(String source, String msg) {
    LOGGER.error("[{}]\t{}", source, msg);
  }

  public static void debug(String source, Throwable t) {
    LOGGER.debug("[" + source + "]\t{}", t);
  }

  public static void warning(String source, String msg) {
    LOGGER.warn("[{}]\t{}", source, msg);
  }

  public static void alert(String source, String msg) {
    LOGGER.warn("[{}]\t{}", source, msg);
  }

  public static void info(String source, String msg) {
    LOGGER.info("[{}]\t{}", source, msg);
  }
}
