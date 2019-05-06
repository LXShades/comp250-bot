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

/**
 * \brief Makes the unit step once in the given direction
 *
 */
public class Step extends AbstractAction {
	// Unit information
    Unit unit; /**< The unit being moved */
    int moveDirection; /**< The direction being moved in */
    
    boolean completed = false; /**< Whether the action has been completed, usually instant */
    
    public Step(Unit u, int direction) {
        super(u);
        
        // init vars
        this.unit = u;
        this.moveDirection = direction;
    }
    
    public boolean completed(GameState pgs) {
        return completed;
    }
    
    public boolean equals(Object o)
    {
        if (!(o instanceof Step)) return false;
        Step a = (Step)o;
        
        return this.moveDirection == a.moveDirection && this.unit == a.unit;
    }
    
    public void toxml(XMLWriter w)
    {
        w.tagWithAttributes("Step","unitID=\""+unit.getID()+"\" direction=\""+moveDirection+"\"");
        w.tag("/Step");
    }
    
    public UnitAction execute(GameState gs, ResourceUsage ru) {        
        // Step in the direction, but only if it is possible to reach
        completed = true;
        UnitAction action = new UnitAction(UnitAction.TYPE_MOVE, moveDirection);
        
        if (unit.canExecuteAction(action, gs)) {
            return action;
        } else {
        	// Move not possible, wait instead
        	return new UnitAction(UnitAction.TYPE_NONE, 1);
        }
    }
    
    /**
     * \brief Returns the direction being moved towards
     * @return
     */
    public int getDirection() {
    	return this.moveDirection;
    }
}
