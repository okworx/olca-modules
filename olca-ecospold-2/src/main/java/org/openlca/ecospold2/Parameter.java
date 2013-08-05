package org.openlca.ecospold2;

import org.jdom2.Element;

public class Parameter {

	private String id;
	private String variableName;
	private double amount;
	private String name;
	private String unitName;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getVariableName() {
		return variableName;
	}

	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}

	public double getAmount() {
		return amount;
	}

	public void setAmount(double amount) {
		this.amount = amount;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUnitName() {
		return unitName;
	}

	public void setUnitName(String unitName) {
		this.unitName = unitName;
	}

	static Parameter fromXml(Element e) {
		if (e == null)
			return null;
		Parameter p = new Parameter();
		p.amount = In.decimal(e.getAttributeValue("amount"));
		p.id = e.getAttributeValue("parameterId");
		p.name = In.childText(e, "name");
		p.unitName = In.childText(e, "unitName");
		p.variableName = e.getAttributeValue("variableName");
		return p;
	}

	Element toXml() {
		Element e = new Element("parameter", Out.NS);
		e.setAttribute("parameterId", id);
		e.setAttribute("amount", Double.toString(amount));
		e.setAttribute("variableName", variableName);
		Out.addChild(e, "name", name);
		Out.addChild(e, "unitName", unitName);
		return e;
	}

}