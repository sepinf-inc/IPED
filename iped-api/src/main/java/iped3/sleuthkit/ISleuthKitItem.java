package iped3.sleuthkit;

import org.sleuthkit.datamodel.Content;

import iped3.IItem;

public interface ISleuthKitItem extends IItem {

    /**
     *
     * @return o objeto do Sleuthkit que representa o item
     */
    Content getSleuthFile();

    /**
     *
     * @return o id do item no Sleuthkit
     */
    Integer getSleuthId();

    /**
     * @param sleuthFile
     *            objeto que representa o item no sleuthkit
     */
    void setSleuthFile(Content sleuthFile);

    /**
     * @param sleuthId
     *            id do item no sleuthkit
     */
    void setSleuthId(Integer sleuthId);

}
