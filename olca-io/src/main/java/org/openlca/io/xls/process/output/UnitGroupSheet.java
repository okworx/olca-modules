package org.openlca.io.xls.process.output;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.openlca.core.database.UnitGroupDao;
import org.openlca.core.model.UnitGroup;
import org.openlca.core.model.Version;
import org.openlca.io.CategoryPath;
import org.openlca.io.xls.Excel;

import java.util.Date;

class UnitGroupSheet {

	private Config config;
	private Sheet sheet;
	private int row = 0;

	private UnitGroupSheet(Config config) {
		this.config = config;
		sheet = config.workbook.createSheet("Unit groups");
	}

	public static void write(Config config) {
		new UnitGroupSheet(config).write();
	}

	private void write() {
		writeHeader();
		UnitGroupDao dao = new UnitGroupDao(config.database);
		for (UnitGroup group : dao.getAll()) {
			row++;
			write(group);
		}
		Excel.autoSize(sheet, 0, 7);
	}

	private void writeHeader() {
		config.header(sheet, row, 0, "UUID");
		config.header(sheet, row, 1, "Name");
		config.header(sheet, row, 2, "Description");
		config.header(sheet, row, 3, "Category");
		config.header(sheet, row, 4, "Reference unit");
		config.header(sheet, row, 5, "Default flow property");
		config.header(sheet, row, 6, "Version");
		config.header(sheet, row, 7, "Last change");
	}

	private void write(UnitGroup group) {
		Excel.cell(sheet, row, 0, group.getRefId());
		Excel.cell(sheet, row, 1, group.getName());
		Excel.cell(sheet, row, 2, group.getDescription());
		Excel.cell(sheet, row, 3, CategoryPath.getFull(group.getCategory()));
		if (group.getReferenceUnit() != null)
			Excel.cell(sheet, row, 4, group.getReferenceUnit().getName());
		if (group.getDefaultFlowProperty() != null)
			Excel.cell(sheet, row, 5, group.getDefaultFlowProperty().getName());
		Excel.cell(sheet, row, 6, Version.asString(group.getVersion()));
		if (group.getLastChange() > 0) {
			Cell cell = Excel.cell(sheet, row, 7);
			cell.setCellValue(new Date(group.getLastChange()));
			cell.setCellStyle(config.dateStyle);
		}
	}
}
