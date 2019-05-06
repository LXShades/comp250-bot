package utilities;

import ai.abstraction.pathfinding.PathFinding;
import extra_abstractions.DoNothing;
import extra_abstractions.Step;
import rts.GameState;
import rts.PhysicalGameState;
import rts.ResourceUsage;
import rts.UnitAction;
import rts.units.Unit;

/***
 * \brief Static map-oriented utilities
 * \author Louis
 *
 */
public class MapUtils {
	/**
	 * \brief Converts a coordinate pair to a position
	 * \param x the X position to convert
	 * \param y the Y position to convert
	 * \param gs the current GameState
	 * \return the position as a single int
	 */
	public static int toPosition(int x, int y, GameState gs) {
		return x + y * gs.getPhysicalGameState().getWidth();
	}
	
	/**
	 * \brief Converts an int position to an X coordinate
	 * \param position the position to convert
	 * \param gs the current gamestate
	 * \return the X coordinate of the position
	 */
	public static int toX(int position, GameState gs) {
		return position % gs.getPhysicalGameState().getWidth();
	}

	/**
	 * \brief Converts an int position to an Y coordinate
	 * \param position the position to convert
	 * \param gs the current gamestate
	 * \return the Y coordinate of the position
	 */
	public static int toY(int position, GameState gs) {
		return position / gs.getPhysicalGameState().getWidth();
	}
	
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

	/**
	 * \brief Returns the Euclidean distance between a unit and a location. Useful for moving away from things
	 * \param a the unit
	 * \param x the X coordinate of the location
	 * \param y the Y coordinate of the location
	 * \return the Euclidean distance between the two points
	 */
	public static float euclideanDistance(Unit a, int x, int y) {
		return (float)Math.sqrt((double)((a.getX() - x) * (a.getX() - x) + (a.getY() - y) * (a.getY() - y)));
	}

	/**
	 * \brief Returns whether a position is within attacking range of a unit
	 * \param a the attacking unit
	 * \param x the X coordinate of the victim
	 * \param y the Y coordinate of the victim
	 * \return whether 
	 */
	public static boolean isInAttackRange(Unit a, int x, int y) {
        if (a.getAttackRange() == 1) {
        	// this is just the number of steps away
        	return (Math.abs(a.getX() - x) + Math.abs(a.getY() - y)) == 1;
        } else {
        	// this is some fancy Euclidean shizzle
        	int squareRange = a.getAttackRange() * a.getAttackRange();
        	
            return ((a.getX() - x) * (a.getX() - x) + (a.getY() - y) * (a.getY() - y)) <= squareRange;
        }
	}
	
	/**
	 * Returns how quickly it would take to receive a certain amount of damage at the given tile, if every enemy unit attacked
	 * \param x the X coordinate of the position
	 * \param y the Y coordinate of the position
	 * \param damageAmount the amount of damage could be taken before a tile is considered dangerous
	 * \return The shortest time that this tile could be attacked by an enemy to the amount 'damageAmount'
	 */
	public static int getDangerTime(int x, int y, int damageAmount, UnitUtils units) {
		if (damageAmount == 0) {
			return Integer.MAX_VALUE;
		}
		
		int dangerTime = Integer.MAX_VALUE;
		
		// Search all enemy units that can attack us
		for (Unit u : units.findUnits((Unit u) -> units.isEnemy(u) && u.getType().canAttack)) {
			int enemyX = u.getX(), enemyY = u.getY();
			int timeToFinishCurrentAction = units.timeToFinishAction(u);
			
			if (timeToFinishCurrentAction > 0) {
				// Simulate enemy movement if necessary
				UnitAction enemyAction = units.getAction(u);
				
				if (enemyAction.getType() == UnitAction.TYPE_MOVE) {
					enemyX += UnitAction.DIRECTION_OFFSET_X[enemyAction.getDirection()];
					enemyY += UnitAction.DIRECTION_OFFSET_Y[enemyAction.getDirection()];
				}
			}
			
			// Check how long it would take to get in range of the player
			int distance = MapUtils.distance(enemyX, enemyY, x, y);
			int timeToTravel = Math.max(distance - u.getType().attackRange, 0) * u.getType().moveTime;
			
			// Update the danger level
			dangerTime = Math.min(dangerTime, timeToFinishCurrentAction + timeToTravel + damageAmount * u.getType().attackTime);
		}
		
		return dangerTime;
	}
	
	/**
	 * \brief Returns how quickly it would take to receive a certain amount of damage at the given tile, if every enemy unit attacked. This also assumes that enemies will never wait on a tile.
	 * \param x the X coordinate of the position
	 * \param y the Y coordinate of the position
	 * \param arrivalTime the time it would take for an allied unit to arrive at the position
	 * \param damageAmount the amount of damage could be taken before a tile is considered dangerous
	 * \return The shortest time that this tile could be attacked by an enemy to the amount 'damageAmount'
	 */
	public static int getDangerTimeAssumingEnemiesCharge(int x, int y, int damageAmount, int arrivalTime, UnitUtils units) {
		if (damageAmount == 0) {
			return Integer.MAX_VALUE;
		}
		
		int dangerTime = Integer.MAX_VALUE;
		
		// Search all enemy units that can attack us
		for (Unit u : units.findUnits((Unit u) -> units.isEnemy(u) && u.getType().canAttack)) {
			int enemyX = u.getX(), enemyY = u.getY();
			int timeToFinishCurrentAction = units.timeToFinishAction(u);
			
			if (timeToFinishCurrentAction > 0) {
				// Simulate enemy movement if necessary
				UnitAction enemyAction = units.getAction(u);
				
				if (enemyAction.getType() == UnitAction.TYPE_MOVE) {
					enemyX += UnitAction.DIRECTION_OFFSET_X[enemyAction.getDirection()];
					enemyY += UnitAction.DIRECTION_OFFSET_Y[enemyAction.getDirection()];
				}
			}
			
			// Check how long it would take to get in range of the player
			int distance = MapUtils.distance(enemyX, enemyY, x, y);
			int timeToTravel = Math.max(distance - u.getType().attackRange, 0) * u.getType().moveTime;
			
			if ((timeToTravel + timeToFinishCurrentAction) % arrivalTime == 0) {
				// We expect the arrival time of the enemy to be in sync with the arrival time of our unit
				dangerTime = Math.min(dangerTime, timeToFinishCurrentAction + timeToTravel + damageAmount * u.getType().attackTime);
			} else {
				// We expect the enemy will rush past and need to turn back
				dangerTime = Math.min(dangerTime, timeToFinishCurrentAction + timeToTravel + u.getType().moveTime + damageAmount * u.getType().attackTime);
			}
		}
		
		return dangerTime;
	}
	


	/**
	 * \brief Returns the direction of the safest tile next to the position
	 * \param x the X coordinate of the position
	 * \param y the Y coordinate of the position
	 * \param damageAmount the amount of damage could be taken before a tile is considered dangerous (todo remove?)
	 * \return The safest neighbour for a unit to take
	 */
	public static int findSafestNeighbour(int x, int y, int damageAmount, UnitUtils units) {
		GameState gs = units.getGameState();
		int mapWidth = gs.getPhysicalGameState().getWidth(), mapHeight = gs.getPhysicalGameState().getHeight();
		int safestDangerTime = Integer.MIN_VALUE;  // 'dangerLevel' is determined by 'how quickly could you die by standing in this spot'
		int safestTile = UnitAction.DIRECTION_DOWN;
		
		// Iterate every neighbouring tile
		for (int i = 0; i < 4; i++) {
			int targetX = x + UnitAction.DIRECTION_OFFSET_X[i], targetY = y + UnitAction.DIRECTION_OFFSET_Y[i];
			
			// Skip if we can't move here
			if (targetX >= mapWidth || targetX < 0 || targetY >= mapHeight|| targetY < 0 || !gs.free(targetX, targetY)) {
				continue;
			}
			
			int dangerLevel = getDangerTime(targetX, targetY, damageAmount, units);
			
			if (dangerLevel > safestDangerTime) {
				safestDangerTime = dangerLevel;
				safestTile = i;
			}
		}
		
		// Return the direction
		return safestTile;
	}
	
	/**
	 * \brief Returns whether the given position exists
	 * \param x the X coordinate of the tile
	 * \param y the Y coordinate of the tile
	 * \param pgs current physical game state
	 * \return true if the tile exists, false otherwise
	 */
	public static boolean tileExists(int x, int y, PhysicalGameState pgs) {
		return (x >= 0 && y >= 0 && x < pgs.getWidth() && y < pgs.getHeight());
	}

	/**
	 * \brief Returns whether the tile at the given position is free AND exists
	 * \param x the X coordinate of the tile
	 * \param y the Y coordinate of the tile
	 * \param gs current game state
	 * \return true of the tile is free and exists, false otherwise
	 */
	public static boolean tileIsFree(int x, int y, GameState gs) {
		PhysicalGameState pgs = gs.getPhysicalGameState();
		return (x >= 0 && y >= 0 && x < pgs.getWidth() && y < pgs.getHeight() && gs.free(x, y));
	}

	/**
	 * \brief Returns whether the tile at the given position is free AND exists, and is not in blockedTiles
	 * \param x the X coordinate of the tile
	 * \param y the Y coordinate of the tile
	 * \param gs current game state
	 * \param blockedTiles an array where the index=a position and the value=whether that position is blocked by a moving ally
	 * \return true of the tile is free and exists, false otherwise
	 */
	public static boolean tileIsFree(int x, int y, GameState gs, boolean[] blockedTiles) {
		PhysicalGameState pgs = gs.getPhysicalGameState();
		int position = toPosition(x, y, gs);
		
		return (x >= 0 && y >= 0 && x < pgs.getWidth() && y < pgs.getHeight() && !blockedTiles[position] && gs.free(x, y));
	}
	
	/**
	 * \brief Returns whether a path currently exists between two points
	 * \param unit the unit at the starting position
	 * \param targetX the destination X position
	 * \param targetY the destination Y position
	 * \param pf a pathfinding method
	 * \param gs the current gamestate
	 * \return whether a valid path exists
	 */
	public static boolean doesPathExist(Unit start, int targetX, int targetY, PathFinding pf, GameState gs) {
		ResourceUsage ru = new ResourceUsage();
		return pf.pathToPositionInRangeExists(start, toPosition(targetX, targetY, gs), 1, gs, ru);
	}
	
	/**
	 * \brief Returns the position of a unit's single step in 'stepDirection'
	 * \param unit the unit to start at
	 * \param stepDirection the direction to step in
	 * \param gs the current GameState
	 * \return the unit's position after a step in this direction
	 */
	public static int getStep(Unit unit, int stepDirection, GameState gs) {
		return toPosition(unit.getX() + UnitAction.DIRECTION_OFFSET_X[stepDirection], unit.getY() + UnitAction.DIRECTION_OFFSET_Y[stepDirection], gs);
	}
}
