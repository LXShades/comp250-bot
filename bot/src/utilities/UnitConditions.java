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
	boolean meetsConditions(Unit u);
}