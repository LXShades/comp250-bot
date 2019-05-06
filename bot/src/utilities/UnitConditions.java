package utilities;

import rts.units.Unit;

/**
 * An interface that evaluates and returns whether a unit meets a custom set of conditions.
 * Often used with lambdas for unit searches where specific traits are desired for each unit.
 * \author Louis
 *
 */
@FunctionalInterface
public interface UnitConditions {
	/**
	 * \brief Whether the unit meets the conditions defined by this implementation
	 * \param u the unit to check
	 * \return whether it meets the conditions defined by this implementation
	 */
	boolean meetsConditions(Unit u);
}