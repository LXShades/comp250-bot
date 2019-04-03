package bot;

import ai.abstraction.AbstractAction;
import ai.abstraction.Train;
import rts.GameState;
import rts.PhysicalGameState;
import rts.ResourceUsage;
import rts.UnitAction;
import rts.units.Unit;
import rts.units.UnitType;
import util.XMLWriter;

public class DoNothing extends AbstractAction {
	// Unit information
    UnitType type;
    Unit unit;
    
    // How long we'll wait for
    int waitDuration;
    
    boolean completed = false;
    
    // Wait for the given period
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
        if (!(o instanceof Train)) return false;
        TrainWithPreferredTile a = (TrainWithPreferredTile)o;
        if (type != a.type) return false;
        
        return true;
    }
    
    
    public void toxml(XMLWriter w)
    {
        w.tagWithAttributes("Train","unitID=\""+unit.getID()+"\" type=\""+type.name+"\"");
        w.tag("/Train");
    }     
    
    public UnitAction execute(GameState gs, ResourceUsage ru) {        
        // Let's chill bruh
        completed = true;
        return new UnitAction(UnitAction.TYPE_NONE, waitDuration);
    }
}
