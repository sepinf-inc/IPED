package iped.viewers.api;

import org.roaringbitmap.RoaringBitmap;

/**
 * A filter that exposes a RoaringBitmap that can be used to filter the
 * resultSet
 * 
 * @author patrick.pdb
 */
public interface IBitmapFilter extends IFilter {
    RoaringBitmap[] getBitmap();

    /*
     * for some filter (like NoBookmarks filter) it is easier return the bitmap of
     * items to filter out, instead of the included items bitmap.
     */
    default public boolean isToFilterOut() {
        return false;
    };

}
