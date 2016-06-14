package org.openlca.geo.parameter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.openlca.geo.kml.KmlFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.geom.TopologyException;
import com.vividsolutions.jts.precision.GeometryPrecisionReducer;

/**
 * Calculates the intersections between a KML feature of a location and the
 * respective shapes in an shape file. It returns a map which contains the IDs
 * of the intersected shapes and the respective shares of the shapes to the
 * total value (e.g. total intersected area).
 */
class IntersectionsCalculator {

	private Logger log = LoggerFactory.getLogger(getClass());

	private final DataStore dataStore;

	IntersectionsCalculator(DataStore dataStore) {
		this.dataStore = dataStore;
	}

	Map<String, Double> calculate(KmlFeature feature) {
		if (feature.type == null)
			return Collections.emptyMap();
		Geometry geo = feature.geometry;
		switch (feature.type) {
		case POINT:
			return calculatePoint(geo, 1d);
		case MULTI_POINT:
			return calculateMultiPoint((MultiPoint) geo);
		case LINE:
		case MULTI_LINE:
			return calculate(geo, new LineStringValueFetch());
		case POLYGON:
		case MULTI_POLYGON:
			return calculate(geo, new PolygonValueFetch());
		default:
			log.warn("cannot calculate shares for type {}", feature.type);
			return Collections.emptyMap();
		}
	}

	private Map<String, Double> calculateMultiPoint(MultiPoint featureGeo) {
		Map<String, Double> result = new HashMap<>();
		int length = featureGeo.getNumGeometries();
		for (int i = 0; i < length; i++) {
			Geometry next = featureGeo.getGeometryN(i);
			double share = 1d / (double) length;
			result.putAll(calculatePoint(next, share));
		}
		return result;
	}

	private Map<String, Double> calculatePoint(Geometry feature, double share) {
		try (SimpleFeatureIterator iterator = getIterator()) {
			while (iterator.hasNext()) {
				SimpleFeature shape = iterator.next();
				Geometry geometry = (Geometry) shape.getDefaultGeometry();
				if (geometry instanceof Point) {
					if (geometry.equalsExact(feature, 1e-6))
						return Collections.singletonMap(shape.getID(), share);
				} else if (geometry.contains(feature))
					return Collections.singletonMap(shape.getID(), share);
			}
			return Collections.emptyMap();
		} catch (Exception e) {
			log.error("failed to fetch point values", e);
			return null;
		}
	}

	private Map<String, Double> calculate(Geometry featureGeo, ValueFetch fetch) {
		double totalValue = fetch.fetchTotal(featureGeo);
		double total = 0;
		if (totalValue == 0)
			return Collections.emptyMap();
		try (SimpleFeatureIterator iterator = getIterator()) {
			Map<String, Double> shares = new HashMap<>();
			while (iterator.hasNext()) {
				SimpleFeature shape = iterator.next();
				Geometry shapeGeo = (Geometry) shape.getDefaultGeometry();
				if (fetch.skip(featureGeo, shapeGeo))
					continue;
				double value = fetch.fetchSingle(featureGeo, shapeGeo);
				shares.put(shape.getID(), value / totalValue);
				total += value;
				if (total >= totalValue) // >= because of float representation
											// (might be 1.0000000002 e.g.)
					// found all intersections (per definition shape files do
					// not contain overlapping features)
					break;
			}
			return shares;
		} catch (Exception e) {
			log.error("failed to fetch parameters for feature", e);
			return null;
		}
	}

	private SimpleFeatureIterator getIterator() throws Exception {
		String typeName = dataStore.getTypeNames()[0];
		SimpleFeatureCollection collection = dataStore.getFeatureSource(
				typeName).getFeatures();
		return collection.features();
	}

	private interface ValueFetch {

		double fetchTotal(Geometry feature);

		double fetchSingle(Geometry feature, Geometry shape);

		boolean skip(Geometry feature, Geometry shape);
	}

	private class LineStringValueFetch implements ValueFetch {

		@Override
		public double fetchTotal(Geometry feature) {
			return feature.getLength();
		}

		@Override
		public double fetchSingle(Geometry feature, Geometry shape) {
			return feature.intersection(shape).getLength();
		}

		@Override
		public boolean skip(Geometry feature, Geometry shape) {
			return !feature.crosses(shape);
		}

	}

	private class PolygonValueFetch implements ValueFetch {

		@Override
		public double fetchTotal(Geometry feature) {
			return feature.getArea();
		}

		@Override
		public double fetchSingle(Geometry feature, Geometry shape) {
			try {
				return feature.intersection(shape).getArea();
			} catch (TopologyException e) {
				// see http://tsusiatsoftware.net/jts/jts-faq/jts-faq.html#D9
				log.warn("Topology exception in feature calculation, "
						+ "reducing precision of original model", e);
				feature = GeometryPrecisionReducer.reduce(feature,
						new PrecisionModel(PrecisionModel.FLOATING_SINGLE));
				return feature.intersection(shape).getArea();
			}
		}

		@Override
		public boolean skip(Geometry feature, Geometry shape) {
			return !feature.intersects(shape);
		}
	}
}
