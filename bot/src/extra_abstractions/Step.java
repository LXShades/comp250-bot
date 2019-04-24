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

public class Step extends AbstractAction {
	// Unit information
    UnitType type;
    Unit unit;
    
    // Which direction to move in
    int moveDirection;
    
    boolean completed = false;
    
    // Wait for the given period
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
        if (!(o instanceof Train)) return false;
        TrainWithPreferredTile a = (TrainWithPreferredTile)o;
        if (type != a.type) return false;
        
        return true;
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
}
