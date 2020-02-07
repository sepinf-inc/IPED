package dpf.sp.gpinf.carver.api;

import iped3.IItem;

public interface CarvedItemListener {

    /**
     * Method that must be called by the CarverTask whenever a item is carved for
     * all the registered Listeners
     * <p>
     *
     * @param parentEvidence
     *            The parent evidence item from where the item was carved
     * @param carvedEvidence
     *            The carved evidence item
     * @param offset
     *            The offset from the start of the parent evidence from where the
     *            item was carved
     * @see IItem
     */
    public void processCarvedItem(IItem parentEvidence, IItem carvedEvidence, long offset);
}
