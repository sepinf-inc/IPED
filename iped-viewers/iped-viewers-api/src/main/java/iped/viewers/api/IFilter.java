package iped.viewers.api;

/**
 * Represents an individual filter to be applied in a resultset
 * 
 * @author patrick.pdb
 */
public interface IFilter {
    public default String getTextualDetails() {
        return null;
    };
}
