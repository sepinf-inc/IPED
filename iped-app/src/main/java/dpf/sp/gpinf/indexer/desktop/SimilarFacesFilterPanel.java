package dpf.sp.gpinf.indexer.desktop;

public class SimilarFacesFilterPanel extends SimilarImagesFilterPanel {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    @Override
    protected void clearFilterAndUpdate() {
        SimilarFacesFilterActions.clear(true);
    }

    @Override
    public void clearFilter() {
        SimilarFacesFilterActions.clear(false);
    }

}
