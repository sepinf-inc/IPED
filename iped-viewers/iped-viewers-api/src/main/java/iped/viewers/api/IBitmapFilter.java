package iped.viewers.api;

import org.roaringbitmap.RoaringBitmap;

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
