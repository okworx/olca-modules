package org.openlca.simapro.csv.model.refdata;

import java.util.ArrayList;
import java.util.List;

import org.openlca.simapro.csv.model.annotations.BlockModel;
import org.openlca.simapro.csv.model.annotations.BlockRows;
import org.openlca.simapro.csv.model.enums.ElementaryFlowType;

@BlockModel("Social issues")
public class SocialIssueBlock implements ElementaryFlowBlock {

	@BlockRows
	private List<ElementaryFlowRow> flows = new ArrayList<>();

	@Override
	public List<ElementaryFlowRow> rows() {
		return flows;
	}

	@Override
	public ElementaryFlowType type() {
		return ElementaryFlowType.SOCIAL_ISSUES;
	}

}