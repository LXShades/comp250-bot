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
 * \brief Makes the unit do nothing for the given duration
 *
 */
public class DoNothing extends AbstractAction {
    Unit unit; /**< The unit to wait this action */
    
    int waitDuration; /**< How long we'll wait for */
    
    boolean completed = false; /**< Whether this action has been completed */

    /**
     * \brief Wait for the given duration
     * \param u the unit to wait
     * \param duration how long to wait for, in game ticks
     */
    public DoNothing(Unit u, int duration) {
        super(u);
        
        // init vars
        this.waitDuration = duration;
        this.unit = u;
    }
    
    public boolean completed(GameState pgs) {
        return completed;
    }
    
    public boolean equals(Object o)
    {
        if (!(o instanceof DoNothing)) return false;
        DoNothing a = (DoNothing)o;
        return a.unit == unit && a.waitDuration == waitDuration;
    }
    
    
    public void toxml(XMLWriter w)
    {
        w.tagWithAttributes("DoNothing","unitID=\""+unit.getID()+"\" duration=\""+waitDuration+"\"");
        w.tag("/DoNothing");
    }     
    
    public UnitAction execute(GameState gs, ResourceUsage ru) {        
        // Let's chill bruh
        completed = true;
        return new UnitAction(UnitAction.TYPE_NONE, waitDuration);
    }
}
