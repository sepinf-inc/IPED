/*
 * Copyright 2020-2020, João Vitor de Sá Hauck
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
package iped.parsers.telegram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import iped.utils.IOUtil;

public class Util {

    public static void invertByteArray(byte[] array, int start, int len) {
        for (int i = 0; i < len / 2; i++) {
            byte aux = array[start + i];
            array[start + i] = array[start + len - i - 1];
            array[start + len - i - 1] = aux;
        }
    }

    public static String readResourceAsString(String resource) {
        try {
            byte[] bytes = IOUtil.loadInputStream(Util.class.getResourceAsStream(resource));
            return new String(bytes, StandardCharsets.UTF_8);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

}
