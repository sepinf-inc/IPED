package iped.app.ui;

public class SimilarFacesFilterPanel extends SimilarImagesFilterPanel {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    protected String getDefaultToolTip() {
        String tooltipTitle = Messages.getString("FaceSimilarity.FilterTipTitle");
        String tooltipDescInternal = Messages.getString("FaceSimilarity.FilterTipInternal");
        String tooltipDescExternal = Messages.getString("FaceSimilarity.FilterTipExternal");
        return buildDefaultToolTip(tooltipTitle, tooltipDescInternal, tooltipDescExternal);
    }

    @Override
    protected String getRemoveToolTip() {
        return Messages.getString("FaceSimilarity.RemoveFilter");
    }

    @Override
    protected void clearFilterAndUpdate() {
        SimilarFacesFilterActions.clear(true);
    }

    @Override
    public void clearFilter() {
        SimilarFacesFilterActions.clear(false);
    }

}
