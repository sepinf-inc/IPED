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
package dpf.ap.gpinf.telegramextractor;

import java.util.ArrayList;
import java.util.HashMap;

public class TagHtml {
    private String tagname;
    private ArrayList<Object> inner;

    private HashMap<String, String> atributes;

    public TagHtml(String tagname) {
        this.tagname = tagname;
        inner = new ArrayList<>();
        atributes = new HashMap<>();
    }

    public void setAtribute(String atribute, String value) {
        atributes.put(atribute, value);
    }

    public String getAtribute(String atribute) {
        return atributes.get(atribute);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<");
        sb.append(tagname);

        for (String atr : atributes.keySet()) {
            sb.append(" " + atr);
            if (atributes.get(atr) != null) {
                sb.append("=");
                sb.append("\"" + atributes.get(atr) + "\"");
            }
        }
        sb.append(">");
        for (Object subelm : inner) {
            sb.append(subelm.toString());
        }
        sb.append("</");
        sb.append(tagname);
        sb.append(">");
        return sb.toString();

    }

    public ArrayList<Object> getInner() {
        return inner;
    }

    public void setInner(ArrayList<Object> inner) {
        this.inner = inner;
    }

}
