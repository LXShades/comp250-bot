package utilities;

import rts.units.Unit;

public class MapUtils {
	/**
	 * \brief Returns the time it takes for a unit to reach another unit, assuming the other unit doesn't move
	 * \param source the source unit
	 * \param target the target unit
	 * \return the time, in game ticks, that it would take the source unit to reach the target unit. If the unit cannot actually move, Integer.MAX_VALUE is returned, you silly person.
	 */
	public static int timeToReach(Unit source, Unit target) {
		return timeToReach(source, target.getX(), target.getY());
	}

	/**
	 * \brief Returns the time it would take for a unit to reach a position (assuming no obstructions)
	 * \param source the travelling unit
	 * \param destinationX the X coordinate of the destination position
	 * \param destinationY the Y coordinate of the destination position
	 * \return the time, in game ticks, that it would take to reach this position. If the unit cannot actually move, Integer.MAX_VALUE is returned, you silly person.
	 */
	public static int timeToReach(Unit source, int destinationX, int destinationY) {
		// Make sure this unit can actually move!
		if (!source.getType().canMove) {
			return Integer.MAX_VALUE;
		}

		// Find the length of the path to this destination (todo: pathfinding)
		return distance(source, destinationX, destinationY) * source.getType().moveTime;
	}

	/**
	 * \brief Returns the distance between two locations
	 * \param aX X coord of the first location
	 * \param aY Y coord of the first location
	 * \param bX X coord of the second location
	 * \param bY Y coord of the second location
	 * \return the inclusive distance between the two locations, as a combined number of horizontal and vertical steps required to reach. A distance of 1 is a neighbour.
	 */
	public static int distance(int aX, int aY, int bX, int bY) {
		return Math.abs(aX - bX) + Math.abs(aY - bY);
	}
	
	/**
	 * \brief Returns the distance between two units
	 * \param a the first unit
	 * \param b the second unit
	 * \return the inclusive distance between the two units, as a combined number of horizontal and vertical steps required to reach. A distance of 1 is a neighbour.
	 */
	public static int distance(Unit a, Unit b) {
		return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
	}

	/**
	 * \brief Returns the distance between a unit and a location
	 * \param a the unit
	 * \param x the X coordinate of the location
	 * \param y the Y coordinate of the location
	 * \return the inclusive distance between the two units, as a combined number of horizontal and vertical steps required to reach. A distance of 1 is a neighbour.
	 */
	public static int distance(Unit a, int x, int y) {
		return Math.abs(a.getX() - x) + Math.abs(a.getY() - y);
	}
}
