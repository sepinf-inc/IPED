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
package dpf.sp.gpinf.indexer.desktop;

import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;

import iped3.IItemId;

public class GalleryValue {

    public String name;
    public ImageIcon icon;
    public IItemId id;
    public int originalW = Integer.MAX_VALUE;
    public int originalH = Integer.MAX_VALUE;
    public BufferedImage image;

    public GalleryValue(String name, ImageIcon icon, IItemId id) {
        this.name = name;
        this.icon = icon;
        this.id = id;
    }

    @Override
    public String toString() {
        return name;
    }
}
