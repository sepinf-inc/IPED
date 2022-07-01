package iped.app.ui;

import javax.swing.event.TableModelEvent;
import javax.swing.table.TableModel;

public class RowSorterTableDataChange extends TableModelEvent{

	public RowSorterTableDataChange(TableModel source) {
		super(source);
	}

}
