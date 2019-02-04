package org.openlca.jsonld.input;

import java.util.Objects;

import org.openlca.core.model.Flow;
import org.openlca.core.model.FlowProperty;
import org.openlca.core.model.FlowPropertyFactor;
import org.openlca.core.model.ImpactCategory;
import org.openlca.core.model.ImpactFactor;
import org.openlca.core.model.ModelType;
import org.openlca.core.model.Unit;
import org.openlca.jsonld.Json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

class ImpactCategories {

	static ImpactCategory map(JsonObject json, ImportConfig conf) {
		if (json == null || conf == null)
			return null;
		ImpactCategory cat = new ImpactCategory();
		In.mapAtts(json, cat, 0);
		cat.referenceUnit = Json.getString(json, "referenceUnitName");
		JsonArray factors = Json.getArray(json, "impactFactors");
		if (factors == null || factors.size() == 0)
			return cat;
		for (JsonElement e : factors) {
			if (!e.isJsonObject())
				continue;
			ImpactFactor factor = mapFactor(e.getAsJsonObject(), conf);
			if (factor == null)
				continue;
			cat.impactFactors.add(factor);
		}
		return cat;
	}

	private static ImpactFactor mapFactor(JsonObject json, ImportConfig conf) {
		if (json == null || conf == null)
			return null;
		ImpactFactor factor = new ImpactFactor();
		factor.value = Json.getDouble(json, "value", 0);
		factor.formula = Json.getString(json, "formula");
		String flowId = Json.getRefId(json, "flow");
		Flow flow = FlowImport.run(flowId, conf);
		factor.flow = flow;
		Unit unit = conf.db.get(ModelType.UNIT, Json.getRefId(json, "unit"));
		factor.unit = unit;
		FlowPropertyFactor propFac = getPropertyFactor(json, flow);
		if (flow == null || unit == null || propFac == null) {
			conf.log.warn("invalid flow {}; LCIA factor not imported", flowId);
			return null;
		}
		factor.flowPropertyFactor = propFac;
		JsonElement u = json.get("uncertainty");
		if (u != null && u.isJsonObject())
			factor.uncertainty = Uncertainties.read(u.getAsJsonObject());
		return factor;
	}

	private static FlowPropertyFactor getPropertyFactor(JsonObject json,
			Flow flow) {
		if (json == null || flow == null)
			return null;
		String propId = Json.getRefId(json, "flowProperty");
		for (FlowPropertyFactor fac : flow.flowPropertyFactors) {
			FlowProperty prop = fac.flowProperty;
			if (prop == null)
				continue;
			if (Objects.equals(propId, prop.refId))
				return fac;
		}
		return null;
	}

}
