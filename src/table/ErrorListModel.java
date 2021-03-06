package table;

import javax.swing.table.DefaultTableModel;

/**
 * A customized table model
 */
@SuppressWarnings("serial")
public class ErrorListModel extends DefaultTableModel {

	/**
	 * Constructs ErrorListModel using default column names and max viewable rows <code>maxViewableRows</code>
	 */
	public ErrorListModel() {
        super(ErrorListTable.columnNames, ErrorListTable.maxViewableRows);
    }

    /**
     * Constructs ErrorListModel using the specified number of rows
     * @param nrows the number of rows
     */
    public ErrorListModel(int nrows) {
        super(ErrorListTable.columnNames, nrows);
    }

    /**
     * Constructs ErrorListModel using column names array and row number
     * @param columnNames the column names of this model
     * @param nrow the number of rows
     */
    public ErrorListModel(Object[] columnNames, int nrow) {
        super(columnNames, nrow);
    }

    /**
     * Not editable for all cells of this model
     * @see javax.swing.table.DefaultTableModel#isCellEditable(int, int)
     */
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }
}
