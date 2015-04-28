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

import java.util.Date;

/**
 * Classe auxiliar com métodos para impressão no console de mensagens de log.
 * @author Wladimir
 */
public class Log {
    public static void error(String msg) {
        print("ERRO", msg);
    }

    public static void warning(String msg) {
        print("AVISO", msg);
    }

    public static void alert(String msg) {
        print("ALERTA", msg);
    }

    public static void info(String msg) {
        print("INFO", msg);
    }

    private static void print(String type, String msg) {
        StringBuilder sb = new StringBuilder(msg.length() + 64);
        sb.append(new Date().toString());
        sb.append("\t[").append(type).append("]\t");
        //sb.append(Thread.currentThread().getName()).append(' ');
        sb.append(msg);
        System.out.println(sb.toString());
    }
}
