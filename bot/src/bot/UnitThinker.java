package bot;

import ai.abstraction.AbstractAction;
import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Attack;
import ai.abstraction.Build;
import ai.abstraction.Harvest;
import ai.abstraction.Move;
import ai.abstraction.Train;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
import ai.mcts.uct.UCTUnitActions;
import extra_abstractions.DoNothing;
import extra_abstractions.Step;
import extra_abstractions.TrainWithPreferredTile;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

import rts.*;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;
import utilities.DebugUtils;
import utilities.MapUtils;
import utilities.UnitUtils;

/**
 * \brief A UnitThinker is associated with each unit when the unit is created. They contain various strategies that any unit can use.
 * \author Louis
 * 
 * The action of each unit at the end of a compatible bot's turn is to execute the 'action' parameter. This can be changed at any time.
 * Strategy functions are postfixed with 'Strategy', which indicates that the 'action' parameter will be set
 * Some Strategy functions will return false if the strategy can't and/or hasn't been applied
 * This is grounds for choosing another strategy.
 * Strategies can safely call other strategies with this system.
 * 
 */
public class UnitThinker {
    private Unit unit; /**< The Unit associated with this thinker */
    
    private UnitUtils units; /**< The UnitUtils associated with the player controlling this thinker */
    
    private AbstractAction action; /**< The action to perform at the end of the tick */
    
    private GameState gameState; /**< The GameState as of the last tick */
    
    private PathFinding pathFinding; /**< The default PathFinding engine to be used with this thinker */
    
    private boolean[] blockedTiles; /**< A list of tiles being blocked by other units moving in the same position */
    
    private int timeWaited = 0; /**< The current number of ticks that this bot spent waiting since a wait began something */
    
    public String role = ""; /**< A custom descriptor of this unit's role, used in coordination */
    
    @FunctionalInterface
    public interface StrategyFunc {
    	void invoke();
    }
    
    public StrategyFunc strategy = () -> doNothingStrategy();
    
    /**
     * \brief Instantiates the UnitThinker with the associated unit and bot dependencies
     * \param unit the unit associated with this thinker 
     * \param units the UnitUtils of the controlling bot
     */
    public UnitThinker(Unit unit, UnitUtils units) {
    	this.unit = unit;
    	this.units = units;
    	this.pathFinding = new AStarPathFinding();
    }
    
    /**
     * Returns the unit associated with this thinker
     */
    public Unit getUnit() {
    	return unit;
    }
    
    /**
     * \brief Ticks the thinker, undertaking any assigned strategies.
     * \param gs the current game state to be used for strategies
     */
    public void tick(GameState gs, boolean[] blockedTiles) {
    	// Update state variables
    	this.gameState = gs;
    	this.blockedTiles = blockedTiles;
    	
    	// Do nothing by default
    	if (units.getAction(unit) == null) {
    		action = new DoNothing(unit, 1);
    	}
    	
    	// Try the assigned strategy
    	if (strategy != null) {
    		strategy.invoke();
    	}
    	
    	// Detect blockages
		if (action instanceof Step) {
			int stepDirection = ((Step)action).getDirection();
			int position = MapUtils.toPosition(unit.getX() + UnitAction.DIRECTION_OFFSET_X[stepDirection], 
											   unit.getY() + UnitAction.DIRECTION_OFFSET_Y[stepDirection], gs);
			
			if (position >= 0 && position < blockedTiles.length) {
				if (blockedTiles[position]) {
					// This unit was blocked, wait a while
					DebugUtils.setUnitLabel(unit, "[B]locked" + UnitAction.DIRECTION_NAMES[stepDirection]);
					
					// This is where a backup plan would be good
					action = new DoNothing(unit, 1);
				} else {
					blockedTiles[position] = true;
				}
			}
		}
    	
    	// Detect wait strategies
    	if (action instanceof DoNothing) {
    		timeWaited++;
    	} else {
    		timeWaited = 0;
    	}
    }
    
    /**
     * \brief Does nothing (waits for 1 tick)
     */
    public void doNothingStrategy() {
    	action = new DoNothing(unit, 1);
    }
    
    /**
	 * \brief Sends the worker out to collect resources. Uses moveSafely to avoid getting murdered along the way
	 */
	public void workerCollectStrategy() {
		// Go collect resources and stuff
		Unit closestResource = null, closestBase = null;
		
		// Find the closest relevant units
		closestResource = units.findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> u.getType().isResource && MapUtils.doesPathExist(unit, u.getX(), u.getY(), pathFinding, gameState));
		closestBase = units.findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> units.isBase(u) && !units.isEnemy(u));
		
		if (closestResource != null && closestBase != null) {
			if (unit.getResources() == 1) {
				if (MapUtils.distance(unit, closestBase) > 1) {
					// Move to the best tile next to the base
					moveSafely(closestBase.getX(), closestBase.getY(), 1, unit.getMoveTime());
					DebugUtils.setUnitLabel(unit, "travelling home~");
				} else {
					// We're next to a base! Install our resources into the base
					DebugUtils.setUnitLabel(unit, "dropping weed for da boiz");
					action = new Harvest(unit, closestResource, closestBase, pathFinding);
				}
			} else {
				if (MapUtils.distance(unit,  closestResource) > 1) {
					// Go out and safely collect resources
					moveSafely(closestResource.getX(), closestResource.getY(), 1, unit.getMoveTime());
					DebugUtils.setUnitLabel(unit, "finding some weed");
				} else {
					DebugUtils.setUnitLabel(unit, "grabbing some weed");
					action = new Harvest(unit, closestResource, closestBase, pathFinding);
				}
			}
		} else {
			// TODO: a backup strategy
		}
	}

	/**
	 * \brief Sends a worker to build barracks
	 */
	public void workerBuildBarracksStrategy() {
		// Only build if we're not already building/doing something
		if (units.getAction(unit) == null) {
			Unit myBase = units.findFirstUnit((Unit u) -> units.isBase(u) && !units.isEnemy(u));
			GameState gs = units.getGameState();
			PhysicalGameState pgs = gs.getPhysicalGameState();
			int buildX = unit.getX(), buildY = unit.getY();
			
			// Decide where to build the barracks
			if (myBase != null) {
				// Build the barracks in a safe place meeting the following conditions:
				// 1) at least 2 tiles away from the base
				// 2) at least 2 tiles away from resources (let's ignore this for now to make this easier plz)
				// 3) far from approaching enemies (let's ignore this because actually 
				
				Unit closestEnemyAttacker = units.findClosestUnit(myBase.getX(), myBase.getY(), (Unit u) -> (units.isEnemy(u) && u.getType().canAttack)); 
				int avoidX = pgs.getWidth() / 2, avoidY = pgs.getHeight() / 2;
				int bestTile = -1;
				int bestTileHeuristic = 0;
				boolean doesResourceExist = units.findFirstUnit((Unit u) -> units.isResource(u)) != null;
				
				if (closestEnemyAttacker != null) {
					// Maximise distance to the closest threatening enemy
					avoidX = closestEnemyAttacker.getX();
					avoidY = closestEnemyAttacker.getY();
				}
				
				// Check tiles in a square
				for (int line = -2; line <= 2; line++) {
					for (int side = 0; side < 4; side++) {
						// Retrieve this part of the square
						int tileX, tileY;
						
						switch (side) {
							case 0:
								tileX = myBase.getX() + line; tileY = myBase.getY() - 2;
								break;
							case 1:
								tileX = myBase.getX() + line; tileY = myBase.getY() + 2;
								break;
							case 2:
								tileX = myBase.getX() - 2; tileY = myBase.getY() ;
								break;
							case 3:
							default:
								tileX = myBase.getX() + 2; tileY = myBase.getY();
								break;
						}
						
						// Check we can go there first
						if (!MapUtils.tileIsFree(tileX, tileY, gs, blockedTiles)) {
							continue;
						}
						
						// Pick the position furthest from the enemies, but ideally at least two tiles from the base and resources
						int heuristic = MapUtils.distance(tileX,  tileY, avoidX, avoidY) + Math.min(MapUtils.distance(myBase, tileX, tileY), 2);

						if (doesResourceExist) {
							heuristic += Math.min(MapUtils.distance(units.findClosestUnit(tileX, tileY, (Unit u) -> units.isResource(u)), tileX, tileY), 2);
						}
						
						if (heuristic > bestTileHeuristic || bestTile == -1) {
							bestTile = MapUtils.toPosition(tileX, tileY, gs);
							bestTileHeuristic = heuristic;
						}
					}
				}
				
				// Select the best tile
				if (bestTile != -1) {
					buildX = MapUtils.toX(bestTile, gs);
					buildY = MapUtils.toY(bestTile, gs);
				}
			}
			
			// Build the barracks
			action = new Build(unit, units.barracks, buildX, buildY, pathFinding);
			
			DebugUtils.setUnitLabel(unit, "[buildBarks] " + MapUtils.distance(unit, buildX, buildY));
		}
	}
	
	/**
	 * \brief Dodges nearby attackers with a delay
	 * If enemyToDodge is null, all nearby enemies will be dodged.
	 * The strategy automatically attacks enemies that are likely to kill the player (NOTE: MOVE THIS?)
	 * 
	 * \param enemyToDodge Enemy to dodge, or null
	 * \return Whether the strategy was undertaken
	 */
	public boolean dodgeStrategy(Unit enemyToDodge) {
		// Can we act now?
		if (units.getAction(unit) != null) {
			return false; // can't do anything yet
		}

		int danger = MapUtils.getDangerTime(unit.getX(), unit.getY(), 1, units);
		
		// Dodge the enemy if a) they are about to move next to us and b) we have time to run
		int enemyTimeTilMove = units.timeToFinishAction(enemyToDodge);
		
		if ((enemyToDodge != null && enemyTimeTilMove < unit.getMoveTime() - 1 && enemyTimeTilMove + enemyToDodge.getAttackTime() > unit.getMoveTime())
				|| (enemyToDodge == null && MapUtils.getDangerTime(unit.getX(), unit.getY(), 1, units) < unit.getMoveTime() + 1)) {
			// Run to a safe neighbouring tile
			int runDirection = MapUtils.findSafestNeighbour(unit.getX(), unit.getY(), unit.getMoveTime(), units);
			
			action = new Step(unit, runDirection);
			return true;
		}
		else
		{
			// We either haven't encountered an enemy yet, or we can't move in time to escape. Eye for an eye, find nearby enemies to attack
			return attackNeighbourStrategy(false, 20);
		}
	}

	/**
	 * \brief Attacks neighbours Or potential neighbours
	 * \param onlyIfDangerous if true, only attacks enemies that endanger this unit
	 * \return whether the strategy was undertaken
	 * TODO: max wait time so it doesn't spare units who don't attack
	 */
	public boolean attackNeighbourStrategy(boolean onlyIfDangerous, int maxWaitTime) {
		if (units.getAction(unit) != null) {
			return false;
		}
		
		// Determine danger level
		int timeUntilDeath = MapUtils.getDangerTime(unit.getX(), unit.getY(), 1, units);
		
		// If no one was attacked, but there is a dangerous enemy approaching, wait for it to arrive and kill it
		if (timeUntilDeath <= unit.getMoveTime() || !onlyIfDangerous) {
			// Choose an enemy to attack. Ideally the one with the lowest HP
			Unit bestEnemyToAttack = null;
			int bestEnemyArrivalTime = Integer.MAX_VALUE;
			
			for (Unit enemy : gameState.getPhysicalGameState().getUnitsAround(unit.getX(), unit.getY(), unit.getAttackRange() + 1)) {
				if (!units.isEnemy(enemy)) {
					continue;
				}
				
				// Figure out where the enemy will be in a moment
				int enemyX = units.getXAfter(enemy, unit.getAttackTime()), enemyY = units.getYAfter(enemy, unit.getAttackTime());
				
				// Is this enemy close enough?
				if (MapUtils.isInAttackRange(unit, enemyX, enemyY)) {
					// Can we kill it in time?
					if (enemy.getAttackTime() >= unit.getAttackTime() || !enemy.getType().canAttack) {
						// Check when it'll arrive
						int arrivalTime = 0;
						
						if (units.getAction(enemy) != null && units.getAction(enemy).getType() == UnitAction.TYPE_MOVE) {
							arrivalTime = units.timeToFinishAction(enemy);
						}
						
						// Kill the fastest, most violent enemies first
						if (bestEnemyToAttack == null || 
							(enemy.getType().canAttack && !bestEnemyToAttack.getType().canAttack) || 
							(arrivalTime <= bestEnemyArrivalTime)) {
								// Attack this enemy
								bestEnemyToAttack = enemy;
								bestEnemyArrivalTime = arrivalTime;
						}
					}
				}
			}
			
			// Decide what to do
			if (bestEnemyToAttack != null && MapUtils.isInAttackRange(unit, bestEnemyToAttack.getX(), bestEnemyToAttack.getY())) {
				// Attack now!
				DebugUtils.setUnitLabel(unit, "[AtkNbr]: Attacking");
				action = new Attack(unit, bestEnemyToAttack, pathFinding);
				return true;
			} else if (bestEnemyToAttack != null) {
				// Wait for this enemy to arrive
				// Don't wait if we've waited too long
				if (timeWaited > maxWaitTime) {
					return false;
				}
				
				DebugUtils.setUnitLabel(unit, "[AtkNbr]: Waiting");
				action = new DoNothing(unit, 1);
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * \brief Focuses on an enemy, avoiding all distractions like a good ninja
	 * \param enemy the enemy to focus. If null, the closest enemy is chosen
	 */
	public void ninjaWarriorStrategy(Unit enemy) {
		// Make sure we can act
		if (units.getAction(unit) != null) {
			return;
		}
		
		// Pick the closest enemy if one isn't provided
		if (enemy == null) {
			enemy = units.findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> units.isEnemy(u));
		}
		
		DebugUtils.setUnitLabel(unit, "[ninja]");
		
		// Attack neighbours, dodge attackers, or move towards the enemy
		if (attackNeighbourStrategy(false, 10)) {
			return;//DebugUtils.setUnitLabel(unit, "DIE NEIGHBOUR");
		} else if (dodgeStrategy(null)) {
			DebugUtils.setUnitLabel(unit, "[ninja] Dodging!");
		} else if (enemy != null) {
			DebugUtils.setUnitLabel(unit, "[ninja] Running! (" + enemy.getX() + "," + enemy.getY() + ")");
			moveSafely(enemy.getX(), enemy.getY(), 1, 2);
		}
	}
	
	/**
	 * \brief Attempts to brush by a naive enemy, killing it in a timing attack
	 */
	public void driveByStrategy(Unit target) {
		if (units.getAction(unit) != null) {
			return;
		}
		
		DebugUtils.setUnitLabel(unit, "[DriveBy]");
		
		// If there's no target, default to being a ninja
		if (target == null || !target.getType().canMove) {
			ninjaWarriorStrategy(null);
			return;
		}
		
		// Attack vulnerable neighbours
		if (attackNeighbourStrategy(false, 10)) {
			DebugUtils.setUnitLabel(unit, "[DriveBy] attacking");
			return;
		}

		// Wait for the enemy to move
		UnitAction targetAction = units.getAction(target);
		
		if (targetAction == null) {
			// wait
			action = new DoNothing(unit, 1);
			return;
		}
		
		// Determine its major axis
		if (targetAction.getType() == UnitAction.TYPE_MOVE) {
			boolean stratWorked = false;
			
			if (targetAction.getDirection() == UnitAction.DIRECTION_LEFT || targetAction.getDirection() == UnitAction.DIRECTION_RIGHT) {
				// Major direction is horizontal, test if we can swipe by it
				if (Math.abs(target.getX() - unit.getX()) > Math.abs(target.getY() - unit.getY())) {
					// Affirmative! Let's attempt to swipe this target at the closest possible point
					if (unit.getY() <= target.getY()) {
						DebugUtils.setUnitLabel(unit, "[DriveBy] to (0,-1)");
						moveStrategy(target.getX(), target.getY() - 1, 0);
						stratWorked = true;
					} else {
						DebugUtils.setUnitLabel(unit, "[DriveBy] to (0,+1)");
						moveStrategy(target.getX(), target.getX() + 1, 0);
						stratWorked = true;
					}
				} else {
					// Failed, revert to ninja warrior
					ninjaWarriorStrategy(target);
					return;
				}
			} else {
				// Major direction is vertical, test if we can swipe by it
				if (Math.abs(target.getY() - unit.getY()) > Math.abs(target.getX() - target.getX())) {
					if (unit.getX() <= target.getX()) {
						DebugUtils.setUnitLabel(unit, "[DriveBy] to (-1,0)");
						moveStrategy(target.getX() - 1, target.getY(), 0);
						stratWorked = true;
					} else {
						DebugUtils.setUnitLabel(unit, "[DriveBy] to (+1,0)");
						moveStrategy(target.getX() + 1, target.getY(), 0);
						stratWorked = true;
					}
				} else {
					// Failed, revert to ninja warrior
					ninjaWarriorStrategy(target);
					return;
				}
			}
			
			if (stratWorked) {
				// Sync with enemy
				if (MapUtils.distance(unit, units.getXAfter(target, target.getMoveTime()), units.getYAfter(unit,  unit.getMoveTime())) == 3) {
					// We want to wait a moment for the enemy to walk in. TODO: Add maximum wait time
					DebugUtils.setUnitLabel(unit, "Hold it!");
					action = new DoNothing(unit, 1);
				}
			}
		} else {
			// OK let's just go
			ninjaWarriorStrategy(target);
			return;
		}
	}
	
	/**
	 * \brief Produces a resource collector in a position biased towards the nearest resource
	 */
	public void produceCollectorStrategy() {
		// Drop a collector
		// Put a worker in the position closest to a resource
		Unit closestResource = units.findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> u.getType().isResource);
		int trainX = 0, trainY = 0;

		if (closestResource != null) {
			trainX = closestResource.getX();
			trainY = closestResource.getY();
		}

		action = new TrainWithPreferredTile(unit, units.worker, trainX, trainY);
	}
	
	/**
	 * \brief Produces a rushing worker in a position towards the enemy
	 * \param type the type of unit to produce
	 */
	public void produceRusherStrategy(UnitType type) {
		// Train a unit closest to the closest enemy
		Unit closestEnemy = units.findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> units.isEnemy(u));
		
		if (closestEnemy != null) {
			action = new TrainWithPreferredTile(unit, type, closestEnemy.getX(), closestEnemy.getY());	
		} else {
			// OK....uh, train a unit wherever then.
			action = new Train(unit, type);
		}
		
	}
	
	/**
	 * \brief Moves to a position, trying to avoid enemies along the way. If an enemy is confronted, the enemy may be attacked
	 * \param targetX the X position of the target
	 * \param targetY the Y position of the target
	 * \param range how close to the target to arrive at
	 * \param maxWaitTime how long will we wait for a tile to become safe before we take another? (TODO)
	 * \return whether the strategy was undertaken
	 */
	public boolean moveSafely(int targetX, int targetY, int range, int maxWaitTime) {
		if (units.getAction(unit) != null) {
			return false;
		}
		
		// todo rewrite the entire A* algorithm to avoid dangerous tiles.....
		if (attackNeighbourStrategy(false, maxWaitTime)) {
			return true;
		}
		
		// Try the move strategy
		if (!moveStrategy(targetX, targetY, range)) {
			DebugUtils.setUnitLabel(unit,  "[moveSafely] Impossible");
			return false;
		}

		// Ensure the next step is not a dangerous tile
		int stepDirection = ((Step)action).getDirection();
		int dangerTime = MapUtils.getDangerTimeAssumingEnemiesCharge(unit.getX() + UnitAction.DIRECTION_OFFSET_X[stepDirection],
				   unit.getY() + UnitAction.DIRECTION_OFFSET_Y[stepDirection], 1, unit.getMoveTime(), units);

		if (dangerTime > unit.getMoveTime() + unit.getAttackTime() || timeWaited >= maxWaitTime) {
			// If we can move to AND escape the tile unharmed (or attack), it's safe
			DebugUtils.setUnitLabel(unit, "[moveSafely] Moving normally " + dangerTime);
		} else {
			DebugUtils.setUnitLabel(unit, "[moveSafely] Run safest " + dangerTime);
			
			// Consider waiting for a while. When time has expired, move somewhere else I guess!
			//action = new Step(unit, MapUtils.findSafestNeighbour(unit.getX(), unit.getY(), 1, units));
			action = new DoNothing(unit, 1);
		}
		
		return true;
	}
	
	/**
	 * \brief Moves to a position with pathfinding
	 * \param targetX the X position of the target
	 * \param targetY the Y position of the target
	 * \param range how close to the target to arrive at
	 */
	public boolean moveStrategy(int targetX, int targetY, int range) {
		if (units.getAction(unit) != null) {
			return false;
		}
		
		// Get a path to the target
		ResourceUsage ru = new ResourceUsage();
		UnitAction moveAction = pathFinding.findPathToPositionInRange(unit, MapUtils.toPosition(targetX, targetY, gameState), range, gameState, ru);
		
		// Make sure we have somewhere to go!
		if (moveAction != null && moveAction.getType() == UnitAction.TYPE_MOVE) {
			// Go there
			action = new Step(unit, moveAction.getDirection());
			
			// If there is another step in the path, block it. This is to stop two units from fighting each other whilst pathfinding
			Unit clone = unit.clone(); // we can only access pathfinding functions with a unit :/
			clone.setX(unit.getX() + UnitAction.DIRECTION_OFFSET_X[moveAction.getDirection()]);
			clone.setY(unit.getY() + UnitAction.DIRECTION_OFFSET_Y[moveAction.getDirection()]);
			UnitAction nextAction = pathFinding.findPathToPositionInRange(clone, MapUtils.toPosition(targetX, targetY, gameState), range, gameState, ru);
			
			if (nextAction != null && nextAction.getType() == UnitAction.TYPE_MOVE) {
				// Block this tile
				int position = MapUtils.getStep(clone, nextAction.getDirection(), gameState);
				int x = clone.getX(), y = clone.getY();
				
				if (!blockedTiles[position]) {
					blockedTiles[position] = true;
				} else {
					// if the tile is already blocked, let's say we failed
					return false;
				}
			}
			
			// Done!
			return true;
		}
		
		return false;
	}
	
	/**
	 * Runs a literal bait-and-switch strategy, where one brother baits an enemy into attacking, while the other leaps in to finish them off
	 * \param this unit's loyal companion
	 * \param the most likely (but not guaranteed!) next victim of the pair
	 */
	public void brotherStrategy(Unit myBrother, Unit victim) {
		if (units.getAction(unit) != null) {
			return;
		}
		
		// Make sure the brothers are close and cosy enough!
		int brotherToMe = MapUtils.distance(unit, myBrother);
		int victimToMe = MapUtils.distance(unit, victim);
		int victimToBrother = MapUtils.distance(myBrother, victim);
		boolean isDefaultBrother = (MapUtils.toPosition(unit.getX(), unit.getY(), units.getGameState()) 
								  < MapUtils.toPosition(myBrother.getX(), myBrother.getY(), units.getGameState()));
		boolean isTooFarFromMyBeautifulBrother = brotherToMe > 1;
		
		if (attackNeighbourStrategy(true, 15)) {
			DebugUtils.setUnitLabel(unit, "Busy attacking");
		} else if (isTooFarFromMyBeautifulBrother) {
			// Move brothers towards each other (team up!)
			if (brotherToMe <= 2) {
				// Pick a brother to move because we don't want to swap positions
				if (victimToMe > victimToBrother || (victimToMe == victimToBrother && isDefaultBrother)) {
					moveSafely(myBrother.getX(), myBrother.getY(), 1, 0);
					DebugUtils.setUnitLabel(unit, "Joining mah bro");
				} else {
					attackNeighbourStrategy(true, 100);
					DebugUtils.setUnitLabel(unit, "Waitin for mah bro");
				}
			} else {
				// Move both brothers towards each other
				moveSafely(myBrother.getX(), myBrother.getY(), 1, 0);
				DebugUtils.setUnitLabel(unit, "Joining mah bro");
			}
		} else {
			// Evaluate all possible positions where (E) is the enemy:
			// 1) The attacker (A) is two free steps away from the enemy's target
			// 2) The bait (B) is one step away from the enemy's target and ready to move
			/*. . . . .
			 *. A B A .
			 *A . | . A
			 *  A E A  */
			//  One brother is 
			// Take the one which can be prepared the quickest
			
			// A is always two steps away from the next position
			// B is always one step away from the enemy's next position
			// How to decide which one shall be A and B?
			
			// See if we're in a good position to dodge the enemy...
			if (false/*dodgeStrategy(null)*/) {
				DebugUtils.setUnitLabel(unit, "Dodge brother strat");
			} else if (victimToMe > 3 && victimToBrother > 3) {
				// Move both brothers towards the victim
				moveSafely(victim.getX(), victim.getY(), 1, 0);
				DebugUtils.setUnitLabel(unit, "Moving safely toward victim");
			} else {
				// See if the enemy is moving in
				int enemyNextX = victim.getX(), enemyNextY = victim.getY();
				UnitAction enemyAction = units.getAction(victim); 
				if (enemyAction != null && enemyAction.getType() == UnitAction.TYPE_MOVE) {
					enemyNextX += UnitAction.DIRECTION_OFFSET_X[enemyAction.getDirection()];
					enemyNextY += UnitAction.DIRECTION_OFFSET_Y[enemyAction.getDirection()];
				}
				
				// Determine whether our bait and switch can take place
				int myDistance = MapUtils.distance(unit, enemyNextX, enemyNextY);
				int brotherDistance = MapUtils.distance(myBrother,  enemyNextX, enemyNextY);
				if ((myDistance == 1 && brotherDistance == 2) || (myDistance == 2 && brotherDistance == 1)) {
					if (myDistance == 2) {
						// We're the ATTACKER. We're going to step in just as soon as the victim has started
						if (units.timeToFinishAction(victim) <= unit.getMoveTime() - 2) {
							action = new Attack(unit, victim, pathFinding);
						} else {
							// wait until the victim can feel reasonably disappointed with themselves
							action = new DoNothing(unit, 1);
						}
					} else {
						// We're the BAIT. Let's get outta here!
						action = new Step(unit, MapUtils.findSafestNeighbour(unit.getX(), unit.getY(), 1, units));
					}
					DebugUtils.setUnitLabel(unit,  "CALL TO ACTION!");
				} else {
					// Wait for the enemy to come closer, but attack any dangerous neighbours if any randomly show up
					action = new DoNothing(unit, 1);
					attackNeighbourStrategy(true, 20);
					DebugUtils.setUnitLabel(unit, "HOLD IT!!");
				}
			}
			
			// Do vulnerability thing
		}
	}
	
	/**
	 * \brief Aggressively attacks the closest enemy. This highly original strategy is (c) DrLouisman
	 */
	public void rangedTempStrategy() {
		if (units.getAction(unit) != null) {
			return;
		}
		
		Unit closestEnemy = units.findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> units.isEnemy(u));
		
		if (closestEnemy != null) {
			if (attackNeighbourStrategy(false, unit.getMoveTime())) {
				DebugUtils.setUnitLabel(unit, "[ranged] DIE!!");
			} else {
				DebugUtils.setUnitLabel(unit, "[ranged] CHASING!");
				moveStrategy(closestEnemy.getX(), closestEnemy.getY(), 2);				
			}
		} else {
			DebugUtils.setUnitLabel(unit, "[ranged] chilling");
			action = new DoNothing(unit, 1);
		}
	}
	
	/**
	 * \brief Moves away from the base
	 */
	public void vacateBaseStrategy() {
		if (units.getAction(unit) != null) {
			return;
		}

		// Protect yourself from enemies!
		if (attackNeighbourStrategy(false, 20)) {
			return;
		}
		
		DebugUtils.setUnitLabel(unit, "[vacate]");
		
		// Find the best free tile that moves away from the base
		Unit myBase = units.findFirstUnit((Unit u) -> units.isBase(u) && !units.isEnemy(u));
		
		if (myBase != null) {
			float bestDistance = MapUtils.euclideanDistance(unit, myBase.getX(), myBase.getY());
			int targetX = unit.getX(), targetY = unit.getY();
			for (int i = 0; i < 4; i++) {
				int tileX = unit.getX() + UnitAction.DIRECTION_OFFSET_X[i], tileY = unit.getY() + UnitAction.DIRECTION_OFFSET_Y[i];
			
				if (MapUtils.tileIsFree(tileX, tileY, units.getGameState())) {
					float distance = MapUtils.euclideanDistance(myBase, tileX, tileY);
					
					if (distance > bestDistance) {
						bestDistance = distance;
						targetX = tileX;
						targetY = tileY;
					}
				}
			}
			
			if (targetX != unit.getX() || targetY != unit.getY()) {
				moveStrategy(targetX, targetY, 0);
				DebugUtils.setUnitLabel(unit, "[vacate] move");
			} else {
				action = new DoNothing(unit, 1);
				DebugUtils.setUnitLabel(unit, "[vacate] blocked");
			}
		} else {
			// todo?
			DebugUtils.setUnitLabel(unit, "[vacate] lol help");
		}
	}
	
	/**
	 * Returns the action to be performed by this bot
	 */
	public AbstractAction getAction() {
		return action;
	}
}
