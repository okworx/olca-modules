package org.openlca.core.matrix.io.olcamat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.openlca.core.database.IDatabase;
import org.openlca.core.matrix.FlowIndex;
import org.openlca.core.matrix.IndexFlow;
import org.openlca.core.matrix.MatrixData;
import org.openlca.core.matrix.ProcessProduct;
import org.openlca.core.matrix.TechIndex;

class IndexWriter {

	private final MatrixData data;

	private File folder;
	private Indexer indexer;

	IndexWriter(MatrixData data) {
		this.data = data;
	}

	void write(IDatabase db, File folder) throws Exception {
		this.folder = folder;
		this.indexer = new Indexer(db);
		writeTechIndex();
		writeEnviIndex();
	}

	private void writeTechIndex() throws Exception {
		TechIndex techIndex = data.techIndex;
		List<String> rows = new ArrayList<>(techIndex.size() + 1);
		rows.add(Csv.techIndexHeader());
		for (int i = 0; i < techIndex.size(); i++) {
			ProcessProduct idx = techIndex.getProviderAt(i);
			TechIndexEntry e = indexer.getTechEntry(idx);
			e.index = i;
			rows.add(e.toCsv());
		}
		File f = new File(folder, "index_A.csv");
		Csv.writeFile(rows, f);
	}

	private void writeEnviIndex() throws Exception {
		FlowIndex enviIndex = data.flowIndex;
		List<String> rows = new ArrayList<>(enviIndex.size() + 1);
		rows.add(Csv.enviIndexHeader());
		for (int i = 0; i < enviIndex.size(); i++) {
			IndexFlow f = enviIndex.at(i);
			EnviIndexEntry e = indexer.getEnviEntry(f.flow.id);
			e.index = i;
			rows.add(e.toCsv());
		}
		File f = new File(folder, "index_B.csv");
		Csv.writeFile(rows, f);
	}

}
