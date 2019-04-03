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
import java.util.concurrent.Callable;

import rts.*;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;


@FunctionalInterface
interface UnitConditions
{ 
    boolean meetsConditions(Unit u);
} 
  
public class MyDisappointingRoboticSon extends AbstractionLayerAI {    
    private UnitTypeTable utt;
    private UnitType worker;
    private UnitType base;
    private UnitType resource;
    private UnitType barracks;
    private UnitType ranged;
    
    private int player = 0;
    
    public MyDisappointingRoboticSon (UnitTypeTable utt) {
        super(new AStarPathFinding());
        this.utt = utt;
        worker = utt.getUnitType("Worker");
        base = utt.getUnitType("Base");
        resource = utt.getUnitType("Resources");
        barracks = utt.getUnitType("Barracks");
        ranged = utt.getUnitType("Ranged");
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

        this.player = player;
        
        // Get enemy units
        List<Unit> bases = new LinkedList<Unit>();
        List<Unit> resources = new LinkedList<Unit>();
        
        // Collect units into lists
        for (Unit unit : pgs.getUnits()) {
        	if (unit.getType() == base) {
        		bases.add(unit);
        	}
        	
        	if (unit.getType().isResource) {
        		resources.add(unit);
        	}
        }
        
        int numWorkersCollectingStuff = 0;
        int playerDps[] = new int[]{0, 0, 0};
        List<Unit> targetedEnemies = new LinkedList<Unit>();
        // Note the concentration of DPS into a small area
        // 4 workers can distribute their DPS very effectively, and move fast
        
        // Find out how quickly an enemy could attack us
        List<Unit> closestBases = findUnits(pgs, (Unit u) -> u.getType() == base && u.getPlayer() == player);
        int closestEnemyTimeToBase = 0;
        
        if (closestBases.size() > 0) {
           	Unit closestBaseToDefend = closestBases.get(0);
           	
           	if (closestBaseToDefend != null) {
           		Unit closestEnemyUnitToBase = findClosestUnit(pgs, closestBaseToDefend.getX(), closestBaseToDefend.getY(), (Unit u) -> u.getPlayer() != player && u.getPlayer() != -1);
           		
           		if (closestEnemyUnitToBase != null) {
           			closestEnemyTimeToBase = timeToReach(closestEnemyUnitToBase, closestBaseToDefend);
           			debug("Closest enemy time to reach base: " + closestEnemyTimeToBase);
           		}
           	}
        }
        
        int numResources = 0;
        int numRanged = countMyUnits(pgs, (Unit u) -> u.getType() == ranged);
        int numBarracks = countMyUnits(pgs, (Unit u) -> u.getType() == barracks);
        
        for (Unit unit : pgs.getUnits()) {
            // TODO: issue commands to units
        	if (unit.getPlayer() == player) {
        		if (unit.getType() == worker) {
        			if (numWorkersCollectingStuff < 1) {
            			// Go collect resources and stuff
        				Unit closestResource = null, closestBase = null;
        				
        				// Find the closest relevant units
        				closestResource = findClosestUnit(pgs, unit.getX(), unit.getY(), 
        						(Unit u) -> u.getType().isResource);
        				closestBase = findClosestUnit(pgs, unit.getX(), unit.getY(), 
        						(Unit u) -> u.getType() == base && u.getPlayer() == player);
        				
        				if (closestResource != null && closestBase != null) {
            				harvest(unit, closestResource, closestBase);	
        				}
        				
            			numWorkersCollectingStuff++;
        			} else if (numWorkersCollectingStuff < 2 && numBarracks < 1) {
        				// Build a barracks
        				buildBarracks(gs.getPlayer(player), pgs, unit);
        				numWorkersCollectingStuff++;
        			} else {
        				// Attack the closest enemy
        				Unit closestEnemy = findClosestUnit(pgs, unit.getX(), unit.getY(), (Unit u) -> u.getPlayer() != player && u.getPlayer() != -1 && !targetedEnemies.contains(u));
        				
        				if (closestEnemy != null) {
                			attack(unit, closestEnemy);
                			targetedEnemies.add(closestEnemy);
        				}
        			}
        			
        			// Record into total DPS
        			playerDps[player] += unit.getType().maxDamage;
        		}
        		
        		if (unit.getType() == base && gs.getActionAssignment(unit)==null) {
        	        if (gs.getPlayer(player).getResources() >= worker.cost && 
        	        		(numRanged > 0 || gs.getPlayer(player).getResources() > barracks.cost + 1)) {
        	        	// Produce workers
        	        	train(unit, worker);
        	        }
        		}
        		
        		if (unit.getType() == barracks && gs.getActionAssignment(unit) == null) {
        			if (gs.getPlayer(player).getResources() >= ranged.cost) {
        				train(unit, ranged);
        			}
        		}
        		
        		if (unit.getType() == ranged) {
        			Unit closestEnemy = findClosestUnit(pgs, unit.getX(), unit.getY(), (Unit u) -> u.getPlayer() != player && u.getPlayer() != -1);
        			attack(unit, closestEnemy);
        			debug("my closest enemy is " + closestEnemy);
        		}
        	}
        }
        
        return translateActions(player, gs);
    }
    
    @Override
    public List<ParameterSpecification> getParameters() {
        return new ArrayList<>();
    }
    
    // Returns a list of units that match the condition specified
    private List<Unit> findUnits(PhysicalGameState pgs, UnitConditions conditions) {
    	List<Unit> result = new LinkedList<Unit>();
    	
    	for (Unit u : pgs.getUnits()) {
    		if (conditions.meetsConditions(u)) {
    			result.add(u);
    		}
    	}
    	
    	return result;
    }
    
    private Unit findClosestUnit(PhysicalGameState pgs, int x, int y, UnitConditions yolo) {
    	int closestDistance = Integer.MAX_VALUE;
    	Unit closestUnit = null;
    	
    	// Find the unit closest to the supplied position
    	for (Unit unit : pgs.getUnits()) {
    		// Ensure the unit meets the supplied conditions
    		if (yolo.meetsConditions(unit)) {
    			/* Since only horizontal and vertical movements are possible, 
    			 * distance is always equal to xDifference + yDifference */
    			int thisDistance = Math.abs(unit.getX() - x) + Math.abs(unit.getY() - y);
    			 
    			if (thisDistance < closestDistance) {
    				closestUnit = unit;
    				closestDistance = thisDistance;
    			}
    		}
    	}
    	
    	return closestUnit;
    }
    
    // Returns the number of units meeting the specified conditions
    private int countMyUnits(PhysicalGameState pgs, UnitConditions conditions) {
    	int count = 0;
    	
    	for (Unit u : pgs.getUnits()) {
    		if (u.getPlayer() == player && conditions.meetsConditions(u)) {
    			count++;
    		}
    	}
    	
    	return count;
    }
    
    private void buildBarracks(Player player, PhysicalGameState pgs, Unit worker) {
        List<Integer> reservedPositions = new LinkedList<Integer>();
    	buildIfNotAlreadyBuilding(worker, barracks, worker.getX() + 3, worker.getY(), reservedPositions, player, pgs);
    }
    
    // attacking worker:
    // pick a target to attack
    // but attack anything along the way, or avoid them for a limited time (if chase is detected, maybe stall them?
    
    private void debug(String text) {
    	System.out.print(text + "\n");
    }
    
    // Returns the time it takes for a unit to reach another
    private int timeToReach(Unit source, Unit target) {
    	// Make sure this unit can actually move!
    	if (!source.getType().canMove) {
    		return Integer.MAX_VALUE;
    	}
    	
    	// Find the length of the path to this destination (todo pathfinding)
    	return unitDistance(source, target) * source.getType().moveTime;
    }
    
    // Returns the distance between two units, in the number of steps it would take to reach
    private int unitDistance(Unit a, Unit b) {
    	return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }
    
    // Returns the distance between a unit and a position
    private int unitDistance(Unit a, int x, int y) {
    	return Math.abs(a.getX() - x) + Math.abs(a.getY() - y);
    }
}