package org.openlca.core.matrix.cache;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openlca.core.database.IDatabase;
import org.openlca.core.matrix.CalcImpactFactor;
import org.openlca.core.model.UncertaintyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class ImpactFactorCache {

	static LoadingCache<Long, List<CalcImpactFactor>> create(
			IDatabase database, ConversionTable conversionTable) {
		return CacheBuilder.newBuilder().build(
				new FactorLoader(database, conversionTable));
	}

	private static class FactorLoader extends
			CacheLoader<Long, List<CalcImpactFactor>> {

		private Logger log = LoggerFactory.getLogger(getClass());
		private IDatabase database;
		private ConversionTable conversionTable;

		public FactorLoader(IDatabase database,
				ConversionTable conversionTable) {
			this.database = database;
			this.conversionTable = conversionTable;
		}

		@Override
		public List<CalcImpactFactor> load(Long impactId) throws Exception {
			log.trace("load impact factors for category {}", impactId);
			try (Connection con = database.createConnection()) {
				String query = "select * from tbl_impact_factors where f_impact_category = "
						+ impactId;
				Statement statement = con.createStatement();
				ResultSet result = statement.executeQuery(query);
				List<CalcImpactFactor> factors = new ArrayList<>();
				while (result.next()) {
					CalcImpactFactor factor = nextFactor(result);
					factors.add(factor);
				}
				result.close();
				statement.close();
				return factors;
			} catch (Exception e) {
				log.error("failed to load impact factors for " + impactId, e);
				return Collections.emptyList();
			}
		}

		@Override
		public Map<Long, List<CalcImpactFactor>> loadAll(
				Iterable<? extends Long> impactCategoryIds) throws Exception {
			log.trace("load impact factors for multiple categories");
			try (Connection con = database.createConnection()) {
				String query = "select * from tbl_impact_factors where f_impact_category in "
						+ CacheUtil.asSql(impactCategoryIds);
				Statement statement = con.createStatement();
				ResultSet result = statement.executeQuery(query);
				Map<Long, List<CalcImpactFactor>> map = new HashMap<>();
				while (result.next()) {
					CalcImpactFactor factor = nextFactor(result);
					CacheUtil.addListEntry(map, factor,
							factor.imactCategoryId);
				}
				result.close();
				statement.close();
				CacheUtil.fillEmptyEntries(impactCategoryIds, map);
				return map;
			} catch (Exception e) {
				log.error("failed to load impact factors", e);
				return Collections.emptyMap();
			}
		}

		private CalcImpactFactor nextFactor(ResultSet r) throws Exception {
			CalcImpactFactor f = new CalcImpactFactor();
			f.imactCategoryId = r.getLong("f_impact_category");
			f.amount = r.getDouble("value");
			f.formula = r.getString("formula");
			f.conversionFactor = getConversionFactor(r);
			f.flowId = r.getLong("f_flow");
			int uncertaintyType = r.getInt("distribution_type");
			if (!r.wasNull()) {
				f.uncertaintyType = UncertaintyType.values()[uncertaintyType];
				f.parameter1 = r.getDouble("parameter1_value");
				f.parameter2 = r.getDouble("parameter2_value");
				f.parameter3 = r.getDouble("parameter3_value");
			}
			return f;
		}

		private double getConversionFactor(ResultSet record) throws Exception {
			long propertyFactorId = record.getLong("f_flow_property_factor");
			double propertyFactor = conversionTable
					.getPropertyFactor(propertyFactorId);
			long unitId = record.getLong("f_unit");
			double unitFactor = conversionTable.getUnitFactor(unitId);
			if (unitFactor == 0)
				return 0;
			return propertyFactor / unitFactor;
		}

	}

}
