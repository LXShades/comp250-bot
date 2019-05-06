package extra_abstractions;

import ai.abstraction.AbstractAction;
import ai.abstraction.Train;
import rts.GameState;
import rts.PhysicalGameState;
import rts.ResourceUsage;
import rts.UnitAction;
import rts.units.Unit;
import rts.units.UnitType;
import util.XMLWriter;
import utilities.MapUtils;

/**
 * \brief Trains a unit with a preference for a particular direction
 *
 */
public class TrainWithPreferredTile extends AbstractAction {
	// Unit information
    Unit unit; /**< The unit to train a unit */
    UnitType type; /**< The type of unit to be trained */
    
    int targetX; /**< The desired X position to train towards */
    int targetY; /**< The desired Y position to train towards */
    
    boolean completed = false; /**< Whether the action has been completed */
    
    // Train with a preferred tile (as a relative direction)
    public TrainWithPreferredTile(Unit u, UnitType a_type, int direction) {
        super(u);
        unit = u;
        type = a_type;
        
        // Assume the target based on the direction
        targetX = unit.getX() + UnitAction.DIRECTION_OFFSET_X[direction];
        targetY = unit.getY() + UnitAction.DIRECTION_OFFSET_Y[direction];
    }
    
    // Train with a preferred tile (as a destination position to travel to)
    public TrainWithPreferredTile(Unit u, UnitType a_type, int destinationX, int destinationY) {
        super(u);
        unit = u;
        type = a_type;

        targetX = destinationX;
        targetY = destinationY;
    }
    
    public boolean completed(GameState pgs) {
        return completed;
    }
    
    public boolean equals(Object o)
    {
        if (!(o instanceof TrainWithPreferredTile)) return false;
        TrainWithPreferredTile a = (TrainWithPreferredTile)o;
        
        return targetX == a.targetX && targetY == a.targetY && unit == a.unit && type == a.type;
    }
    
    
    public void toxml(XMLWriter w)
    {
        w.tagWithAttributes("TrainWithPreferredTile","unitID=\""+unit.getID()+"\" type=\""+type.name+"\" targetX=\""+targetX+"\" targetY=\""+targetY+"\"");
        w.tag("/TrainWithPreferredTile");
    }     
    
    public UnitAction execute(GameState gs, ResourceUsage ru) {
        // Find a free spot closest to the desired direction
        int closestDistance = Integer.MAX_VALUE;
        int closestDirection = -1;
        
        for (int i = 0; i < 4; i++) {
        	int produceX = unit.getX() + UnitAction.DIRECTION_OFFSET_X[i], produceY = unit.getY() + UnitAction.DIRECTION_OFFSET_Y[i];
        	int currentDistance = distance(produceX, produceY, targetX, targetY);
        	
        	// check if this is the closest place to spawn
        	if (currentDistance < closestDistance && MapUtils.tileIsFree(produceX, produceY, gs)) {
        		closestDistance = currentDistance;
        		closestDirection = i;
        	}
        }
        
        // Train the unit here!
        if (closestDirection != -1) {
        	UnitAction action = new UnitAction(UnitAction.TYPE_PRODUCE, closestDirection, type);
        	if (gs.isUnitActionAllowed(unit, action)) {
        		completed = true;
        		return action;
        	}
        }
        
        // We can't produce right now! Wait for a short while
        completed = true;
        return new UnitAction(UnitAction.TYPE_NONE, 1);
    }
    
    /**
     * \brief Returns the Manhattan distance between two points
     * \param aX the X coordinate of the first point
     * \param aY the Y coordinate of the first point
     * \param bX the X coordinate of the second point
     * \param bY the Y coordinate of the second point
     * \return the Manhattan distance between the two points
     */
    private int distance(int aX, int aY, int bX, int bY) {
    	return Math.abs(aX - bX) + Math.abs(aY - bY);
    }
}
