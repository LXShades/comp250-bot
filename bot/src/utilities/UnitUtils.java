package utilities;

import rts.units.UnitTypeTable;
import rts.units.UnitType;
import rts.units.Unit;

public class UnitUtils {
	// Unit type references for comparisons
    public UnitType worker;
    public UnitType base;
    public UnitType resource;
    public UnitType barracks;
    public UnitType light;
    public UnitType heavy;
    public UnitType ranged;
    
    private int playerId; /**< The player owning this unit utils */
    
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
     * \brief Sets the current player owning this unitutils
     * \param id ID of the player
     */
    public void setCurrentPlayer(int id) {
    	playerId = id;
    }
}
