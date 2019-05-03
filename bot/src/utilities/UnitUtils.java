package utilities;

import rts.units.UnitTypeTable;
import rts.units.UnitType;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import utilities.UnitConditions;
import rts.GameState;
import rts.UnitAction;
import rts.UnitActionAssignment;
import rts.units.Unit;

public class UnitUtils {
	// References to each unit type
    public UnitType worker;   /**< workers */
    public UnitType base;     /**< bases */
    public UnitType resource; /**< resources */
    public UnitType barracks; /**< barracks...es */
    public UnitType light;    /**< light units */
    public UnitType heavy;    /**< heavy units */
    public UnitType ranged;   /**< ranged units*/
    
    private int playerId; /**< The player owning this unit utils */
    private GameState gs; /**< The gamestate on the last tick */
    
    /**
     * \brief Initialises the unit utilities
     * \param utt the unit type table to use for type referencing
     */
    public UnitUtils(UnitTypeTable utt) {
		// Prefetch game unit type information
		worker = utt.getUnitType("Worker");
		base = utt.getUnitType("Base");
		resource = utt.getUnitType("Resources");
		barracks = utt.getUnitType("Barracks");
		light = utt.getUnitType("Light");
		heavy = utt.getUnitType("Heavy");
		ranged = utt.getUnitType("Ranged");
    }
    
    /** 
     * \brief Refreshes dependencies
     * \param playerId ID of the player owning this UnitUtils
     * \param gs the current GameState
     */
    public void tick(int playerId, GameState gs) {
    	this.playerId = playerId;
    	this.gs = gs;
    }
    
    /**
     * \brief Returns the currently assigned gamestate
     */
    public GameState getGameState() {
    	return gs;
    }
    
    /**
     * \brief Returns whether the unit is a Worker type
     */
    public boolean isWorker(Unit u) {
    	return u.getType() == worker;
    }

    /**
     * \brief Returns whether the unit is a Base type
     */
    public boolean isBase(Unit u) {
    	return u.getType() == base;
    }

    /**
     * \brief Returns whether the unit is a Resource type
     */
    public boolean isResource(Unit u) {
    	return u.getType() == resource;
    }

    /**
     * \brief Returns whether the unit is a Barracks type
     */
    public boolean isBarracks(Unit u) {
    	return u.getType() == barracks;
    }

    /**
     * \brief Returns whether the unit is a Light type
     */
    public boolean isLight(Unit u) {
    	return u.getType() == light;
    }
    
    /**
     * \brief Returns whether the unit is a Heavy type
     */
    public boolean isHeavy(Unit u) {
    	return u.getType() == heavy;
    }
    
    /**
     * \brief Returns whether the unit is a Ranged type
     */
    public boolean isRanged(Unit u) {
    	return u.getType() == ranged;
    }
    
    /**
     * \brief Returns whether the unit is an enemy of the player owning this unitutils
     */
    public boolean isEnemy(Unit u) {
    	return u.getPlayer() != playerId && u.getPlayer() != -1;
    }
    
	/** 
	 * \brief Returns a list of units that match the condition specified
	 * \param conditions The conditions to test against the unit
	 * \return A list of units matching where UnitConditions.meetsConditions(unit) is true
	 */
	public List<Unit> findUnits(UnitConditions conditions) {
		List<Unit> result = new ArrayList<Unit>();

		for (Unit u : gs.getUnits()) {
			if (conditions.meetsConditions(u)) {
				result.add(u);
			}
		}

		return result;
	}

	/**
	 * \brief Returns the closest unit of the given conditions
	 * \param x the X coordinate to measure against
	 * \param y the Y coordinate to measure against
	 * \param conditions a set of conditions that the unit must match
	 * \return the closest unit to x,y matching the given conditions
	 */
	public Unit findClosestUnit(int x, int y, UnitConditions conditions) {
		int closestDistance = Integer.MAX_VALUE;
		Unit closestUnit = null;

		// Find the unit closest to the supplied position
		for (Unit unit : gs.getUnits()) {
			// Ensure the unit meets the supplied conditions
			if (conditions.meetsConditions(unit)) {
				/*
				 * Since only horizontal and vertical movements are possible, distance is always
				 * equal to xDifference + yDifference
				 */
				int thisDistance = Math.abs(unit.getX() - x) + Math.abs(unit.getY() - y);

				if (thisDistance < closestDistance) {
					closestUnit = unit;
					closestDistance = thisDistance;
				}
			}
		}

		return closestUnit;
	}

	// 
	/**
	 * \brief Returns the unit that could reach the given position soonest
	 * \param x the X coordinate to measure the distance against
	 * \param y the Y coordinate to measure the distance against
	 * \param conditions a set of conditions that the unit must match
	 * \return the closest unit to x,y matching the given positions
	 */
	public Unit findSoonestUnit(int x, int y, UnitConditions conditions) {
		int bestTravelTime = Integer.MAX_VALUE;
		Unit bestUnit = null;

		// Return the unit with the smallest timeToReach
		for (Unit unit : gs.getUnits()) {
			// Verify the conditions
			if (conditions.meetsConditions(unit)) {
				int thisTravelTime = MapUtils.timeToReach(unit, x, y);

				if (thisTravelTime < bestTravelTime) {
					bestTravelTime = thisTravelTime;
					bestUnit = unit;
				}
			}
		}

		return bestUnit;
	}
	
	/**
	 * \brief Returns the unit's current action, or null if there is no active action
	 * \param u the unit to check
	 * \return the currently assigned action to the unit, or null if N/A
	 */
	public UnitAction getAction(Unit u) {
		UnitActionAssignment assignment = gs.getActionAssignment(u);
		
		return assignment != null ? assignment.action : null;
	}

	/**
	 * \brief Returns how long it would take for a unit to finish its current action
	 * \param u the unit to check
	 * \return the time it'll take to finish an action, or 0 if there is no action
	 */
	// Returns how long it would take for a unit to finish its current action
	public int timeToFinishAction(Unit u) {
		if (getAction(u) != null) {
			return gs.getActionAssignment(u).action.ETA(u) - (gs.getTime() - gs.getActionAssignment(u).time);
		} else {
			return 0;
		}
	}
	
	/**
	 * \brief Returns the expected X position of a unit after a given time period, considering their current action, with no further projection.
	 * \param u the unit to check
	 * \param period how long from now
	 * \return the X position of a unit after the period
	 */
	// Returns the X position of a unit after the given time period
	public int getXAfter(Unit u, int period) {
		UnitAction action = getAction(u);
		
		if (action != null && action.getType() == UnitAction.TYPE_MOVE && timeToFinishAction(u) <= period) {
			return u.getX() + UnitAction.DIRECTION_OFFSET_X[action.getDirection()];
		} else {
			return u.getX();	
		}
	}

	/**
	 * \brief Returns the expected Y position of a unit after a given time period, considering their current action, with no further projection.
	 * \param u the unit to check
	 * \param period how long from now
	 * \return the Y position of a unit after the period
	 */
	public int getYAfter(Unit u, int period) {
		UnitAction action = getAction(u);
		
		// Check an action ahead
		if (action != null && action.getType() == UnitAction.TYPE_MOVE && timeToFinishAction(u) <= period) {
			return u.getY() + UnitAction.DIRECTION_OFFSET_Y[action.getDirection()];
		} else {
			return u.getY();
		}
	}
	
	/**
	 * \brief returns the number of this player's units meeting the specified conditions
	 * \param conditions the conditions to test against
	 * \return the number of units owned by this player satisfying these conditions
	 */
	public int countMyUnits(UnitConditions conditions) {
		int count = 0;

		// Count matching units
		for (Unit u : gs.getUnits()) {
			if (u.getPlayer() == playerId && conditions.meetsConditions(u)) {
				count++;
			}
		}

		return count;
	}
}
