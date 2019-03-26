package bot;

import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import rts.*;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;

public class MyDisappointingRoboticSon extends AbstractionLayerAI {    
    private UnitTypeTable utt;
    private UnitType worker;
    private UnitType base;
    private UnitType resource;
    
    public MyDisappointingRoboticSon (UnitTypeTable utt) {
        super(new AStarPathFinding());
        this.utt = utt;
        worker = utt.getUnitType("Worker");
        base = utt.getUnitType("Base");
        resource = utt.getUnitType("Resources");
    }
    

    @Override
    public void reset() {
    }

    
    @Override
    public AI clone() {
        return new MyDisappointingRoboticSon(utt);
    }
   
    
    @Override
    public PlayerAction getAction(int player, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        
        // Get enemy units
        Unit enemy = null;
        List<Unit> bases = new LinkedList<Unit>();
        List<Unit> resources = new LinkedList<Unit>();
        
        // Find an enemy unit
        for (Unit unit : pgs.getUnits()) {
        	if (unit.getType() == base) {
        		bases.add(unit);
        	}
        	
        	if (unit.getType().isResource) {
        		resources.add(unit);
        	}
        	
        	if (unit.getPlayer() != player && unit.getPlayer() != -1) {
        		enemy = unit;
        	}
        }
        
        boolean doneFirstWorker = false;
        
        for (Unit unit : pgs.getUnits()) {
            // TODO: issue commands to units
        	if (unit.getPlayer() == player) {
        		if (unit.getType() == worker && enemy != null) {
        			if (!doneFirstWorker) {
            			// Go collect resources and stuff
        				Unit closestResource = null, closestBase = null;
        				
        				for (Unit b : bases) {
        					if (b.getPlayer() == player) {
            					closestBase = b;
        					} 
        				}
        				for (Unit r : resources) {
        					closestResource = r;
        				}
        				
        				harvest(unit, closestResource, closestBase);
            			doneFirstWorker = true;
        			} else {
            			attack(unit, enemy);
        			}
        		}
        		
        		if (unit.getType() == base && gs.getActionAssignment(unit)==null) {
        	        if (gs.getPlayer(player).getResources() >= worker.cost) {
        	        	// Produce workers
        	        	train(unit, worker);
        	        }
        		}
        	}
        }
        
        return translateActions(player, gs);
    }
    
    @Override
    public List<ParameterSpecification> getParameters() {
        return new ArrayList<>();
    }
}