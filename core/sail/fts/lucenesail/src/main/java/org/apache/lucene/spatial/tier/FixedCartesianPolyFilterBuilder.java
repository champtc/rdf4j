/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
package org.apache.lucene.spatial.tier;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.apache.lucene.search.Filter;
import org.apache.lucene.spatial.geometry.FloatLatLng;
import org.apache.lucene.spatial.geometry.LatLng;
import org.apache.lucene.spatial.geometry.shape.LLRect;
import org.apache.lucene.spatial.tier.projections.CartesianTierPlotter;
import org.apache.lucene.spatial.tier.projections.FixedSinusoidalProjector;
import org.apache.lucene.spatial.tier.projections.IProjector;

/**
 * Fixed version of CartesianPolyFilterBuilder.
 * 
 * @deprecated since 4.1.0. Use the LuceneSail in package
 *             {@code org.openrdf.sail.lucene} instead.
 */
@Deprecated
public class FixedCartesianPolyFilterBuilder {

	// Finer granularity than 1 mile isn't accurate with
	// standard java math. Also, there's already a 2nd
	// precise filter, if needed, in DistanceQueryBuilder,
	// that will make the filtering exact.
	public static final double MILES_FLOOR = 1.0;

	private IProjector projector = new FixedSinusoidalProjector();

	private final String tierPrefix;

	private int minTier;

	private int maxTier;

	/**
	 * @param tierPrefix
	 *        The prefix for the name of the fields containing the tier info
	 * @param minTierIndexed
	 *        The minimum tier level indexed
	 * @param maxTierIndexed
	 *        The maximum tier level indexed
	 */
	@Deprecated
	public FixedCartesianPolyFilterBuilder(String tierPrefix, int minTierIndexed, int maxTierIndexed) {
		this.tierPrefix = tierPrefix;
		this.minTier = minTierIndexed;
		this.maxTier = maxTierIndexed;
	}

	@Deprecated
	public Shape getBoxShape(double latitude, double longitude, double miles) {
		if (miles < MILES_FLOOR) {
			miles = MILES_FLOOR;
		}
		LLRect box1 = LLRect.createBox(new FloatLatLng(latitude, longitude), miles, miles);
		LatLng ll = box1.getLowerLeft();
		LatLng ur = box1.getUpperRight();

		double latY = ur.getLat();
		double latX = ll.getLat();
		double longY = ur.getLng();
		double longX = ll.getLng();
		double longX2 = 0.0;
		// These two if checks setup us up to deal with issues around the prime
		// meridian and the 180th meridian
		// In these two cases, we need to get tiles (tiers) from the lower left
		// up to the meridian and then
		// from the meridan to the upper right
		// Are we crossing the 180 deg. longitude, if so, we need to do some
		// special things
		if (ur.getLng() < 0.0 && ll.getLng() > 0.0) {
			longX2 = ll.getLng();
			longX = -180.0;
		}
		// are we crossing the prime meridian (0 degrees)? If so, we need to
		// account for it and boxes on both sides
		if (ur.getLng() > 0.0 && ll.getLng() < 0.0) {
			longX2 = ll.getLng();
			longX = 0.0;
		}

		// System.err.println("getBoxShape:"+latY+"," + longY);
		// System.err.println("getBoxShape:"+latX+"," + longX);
		CartesianTierPlotter ctp = new CartesianTierPlotter(2, projector, tierPrefix);
		int bestFit = ctp.bestFit(miles);
		if (bestFit < minTier) {
			bestFit = minTier;
		}
		else if (bestFit > maxTier) {
			bestFit = maxTier;
		}

		ctp = new CartesianTierPlotter(bestFit, projector, tierPrefix);
		Shape shape = new Shape(ctp.getTierFieldName());

		// generate shape
		// iterate from startX->endX
		// iterate from startY -> endY
		// shape.add(currentLat.currentLong);
		// for the edge cases (prime meridian and the 180th meridian), this call
		// handles all tiles East of the meridian
		// for all other cases, it handles the whole set of tiles
		shape = getShapeLoop(shape, ctp, latX, longX, latY, longY);
		if (longX2 != 0.0) {
			if (longX == 0.0) {
				longX = longX2;
				longY = 0.0;
				// handles the lower left longitude to the prime meridian
				// shape = getShapeLoop(shape, ctp, latX, longX, latY, longY);
			}
			else {
				// this clause handles the lower left longitude up to the 180
				// meridian
				longX = longX2;
				longY = 180.0;
			}
			shape = getShapeLoop(shape, ctp, latX, longX, latY, longY);

			// System.err.println("getBoxShape2:"+latY+"," + longY);
			// System.err.println("getBoxShape2:"+latX+"," + longX);
		}

		return shape;
	}

	@Deprecated
	public Shape getShapeLoop(Shape shape, CartesianTierPlotter ctp, double latX, double longX, double latY,
			double longY)
	{

		// System.err.println("getShapeLoop:"+latY+"," + longY);
		// System.err.println("getShapeLoop:"+latX+"," + longX);
		double beginAt = ctp.getTierBoxId(latX, longX);
		double endAt = ctp.getTierBoxId(latY, longY);
		if (beginAt > endAt) {
			double tmp = beginAt;
			beginAt = endAt;
			endAt = tmp;
		}
		double tierVert = ctp.getTierVerticalPosDivider();
		// System.err.println(" | "+ beginAt+" | "+ endAt);

		double startX = beginAt - (beginAt % 1);
		double startY = beginAt - startX; // should give a whole number

		double endX = endAt - (endAt % 1);
		double endY = endAt - endX; // should give a whole number

		int scale = (int)Math.log10(tierVert);
		endY = new BigDecimal(endY).setScale(scale, RoundingMode.HALF_EVEN).doubleValue();
		startY = new BigDecimal(startY).setScale(scale, RoundingMode.HALF_EVEN).doubleValue();
		double xInc = 1.0d / tierVert;
		xInc = new BigDecimal(xInc).setScale(scale, RoundingMode.HALF_EVEN).doubleValue();

		// System.err.println("go from startX:"+startX+" to:" + endX);
		for (; startX <= endX; startX++) {

			double itY = startY;
			// System.err.println("go from startY:"+startY+" to:" + endY);
			while (itY <= endY) {
				// create a boxId
				// startX.startY
				double boxId = startX + itY;
				shape.addBox(boxId);
				// System.err.println("----"+startX+" and "+itY);
				// System.err.println("----"+boxId);
				itY += xInc;

				// java keeps 0.0001 as 1.0E-1
				// which ends up as 0.00011111
				itY = new BigDecimal(itY).setScale(scale, RoundingMode.HALF_EVEN).doubleValue();
			}
		}
		return shape;
	}

	@Deprecated
	public Filter getBoundingArea(double latitude, double longitude, double miles) {
		Shape shape = getBoxShape(latitude, longitude, miles);
		return new CartesianShapeFilter(shape, shape.getTierId());
	}
}
