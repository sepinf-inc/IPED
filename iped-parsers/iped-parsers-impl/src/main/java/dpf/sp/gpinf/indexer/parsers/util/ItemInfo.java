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
package dpf.sp.gpinf.indexer.parsers.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;

/**
 * Contém informações sobre o item sendo processado, sendo configurado no
 * contexto do parsing de forma que os parsers tenham acesso a tais informações.
 * 
 * @author Nassif
 *
 */
public class ItemInfo implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private int child = -1, id;
    private Collection<String> bookmarks;
    private Collection<String> categories;
    private String path, hash;
    private boolean carved = false;

    public ItemInfo(int id, String hash, Collection<String> bookmarks, Collection<String> categories, String path, boolean carved) {
        this.id = id;
        this.hash = hash;
        this.bookmarks = bookmarks;
        this.categories = categories;
        this.path = path;
        this.setCarved(carved);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void incChild() {
        this.child++;
    }

    public int getChild() {
        return child;
    }

    public void setBookmarks(HashSet<String> bookmarks) {
        this.bookmarks = bookmarks;
    }

    public Collection<String> getBookmarks() {
        return bookmarks;
    }
    
    public Collection<String> getCategories() {
        return categories;
    }

    public boolean isCarved() {
        return carved;
    }

    public void setCarved(boolean carved) {
        this.carved = carved;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

}
