package iped.viewers.api;

import iped.data.IItem;
import iped.data.IItemId;

public interface IItemRef {

    IItem getItemRef();

    IItemId getItemRefId();

}
