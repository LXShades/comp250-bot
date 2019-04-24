package bot;

import ai.abstraction.AbstractAction;
import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Attack;
import ai.abstraction.Build;
import ai.abstraction.Harvest;
import ai.abstraction.Move;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
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
 * @author User
 * 
 */
public class UnitThinker {
	public MyDisappointingRoboticSon bot = null; /**< The parent bot */
    
    private Unit unit; /**< The Unit associated with this thinker */
    
    private UnitUtils units;
    
    private AbstractAction action; /**< The action to perform at the end of the tick */
    
    @FunctionalInterface
    public interface StrategyFunc {
    	void invoke();
    }
    
    public StrategyFunc strategy = () -> doNothingStrategy();
    
    // Instantiates the UnitThinker with the associated unit and bot dependencies
    public UnitThinker(Unit unit, MyDisappointingRoboticSon parent, UnitUtils units, int playerId) {
    	this.unit = unit;
    	this.bot = parent;
    	this.units = units;
    }
    
    public void tick() {
    	// Do the currently assigned strategy
    	strategy.invoke();
    }
    
    /**
     * \brief Does nothing (waits for 1 tick)
     */
    public void doNothingStrategy() {
    	action = new DoNothing(unit, 1);
    }
    
    /**
	 * \brief Sends the worker out to collect resources
	 */
	public void workerCollectStrategy() {
		// Go collect resources and stuff
		Unit closestResource = null, closestBase = null;

		// Find the closest relevant units
		closestResource = bot.findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> u.getType().isResource);
		closestBase = bot.findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> units.isBase(u) && !units.isEnemy(u));

		if (closestResource != null && closestBase != null) {
			if (unit.getResources() == 1) {
				if (MapUtils.distance(unit, closestBase) > 1) {
					// If there is not an immediately neighbouring base, go to the tile on the base
					// that is closest to a resource
					int targetBaseX = closestBase.getX(), targetBaseY = closestBase.getY();
					int neighbourPositions[] = new int[] { targetBaseX - 1, targetBaseY, targetBaseX + 1, targetBaseY,
							targetBaseX, targetBaseY - 1, targetBaseX, targetBaseY + 1 };

					// Find the tile closest to both the base and resource
					int closestPositionIndex = 0, closestPositionDistance = 99999;
					for (int i = 0; i < 4; i++) {
						int positionDistance = MapUtils.distance(closestResource, neighbourPositions[i * 2],
								neighbourPositions[i * 2 + 1]);

						if (positionDistance < closestPositionDistance) {
							closestPositionDistance = positionDistance;
							closestPositionIndex = i;
							break;
						}
					}

					// Begin the journey of a thousand tiles
					action = new Move(unit, neighbourPositions[closestPositionIndex * 2], neighbourPositions[closestPositionIndex * 2 + 1], bot.getPathFinding());
				} else {
					// We're next to a base! Drop our shizzle here
					action = new Harvest(unit, closestResource, closestBase, bot.getPathFinding());
				}

				// Also: Kill enemy workers if they meet me (self-defense!)
			} else {
				action = new Harvest(unit, closestResource, closestBase, bot.getPathFinding());
			}
		}
	}

	/**
	 * \brief Sends a worker to build barracks
	 */
	public void workerBuildBarracksStrategy() {
		// Only build if we're not already building/doing something
		if (bot.getUnitAction(unit) == null) {
			action = new Build(unit, units.barracks, unit.getX(), unit.getY(), bot.getPathFinding());
		}
	}
	
	// Sends a pair of brothers, one to lure an attacker in and the other to kill the attacker while the attacker is vulnerable
	/*public void evilBrotherStrategy(Unit brotherA, Unit brotherB) {
		Unit attacker, bait, victim;

		// Choose the victim bot
		Unit closestToA = findClosestUnit(brotherA.getX(), brotherA.getY(), (Unit u) -> u.getPlayer() != playerId && u.getType().canAttack);
		Unit closestToB = findClosestUnit(brotherB.getX(), brotherB.getY(), (Unit u) -> u.getPlayer() != playerId && u.getType().canAttack);
		
		if (closestToA == null) {
			// To achieve this strategy we need a victim
			return;
		}
		
		// Decide who should be the attacker and baiter
		if (timeToReach(brotherA, closestToA) < timeToReach(brotherB, closestToB)) {
			bait = brotherA;
			attacker = brotherB;
			victim = closestToA;
		} else {
			bait = brotherB;
			attacker = brotherA;
			victim = closestToB;
		}
		
		// If the enemy is moving to a tile next to attacker/baiter, attacker and baiter can be clearly defined
		int victimNextX = victim.getX(), victimNextY = victim.getY();
		UnitActionAssignment victimAssignment = gs.getActionAssignment(victim);
		UnitAction victimAction = victimAssignment != null ? victimAssignment.action : null;

		unitLabels.put(brotherA, getUnitAction(brotherA) + "/" + actions.get(brotherA));
		unitLabels.put(brotherB, getUnitAction(brotherB) + "/" + actions.get(brotherB));
		
		if (victimAction != null && victimAction.getType() == UnitAction.TYPE_MOVE) {
			// Get the victim's predicted movement
			victimNextX = victim.getX() + UnitAction.DIRECTION_OFFSET_X[victimAction.getDirection()];
			victimNextY = victim.getY() + UnitAction.DIRECTION_OFFSET_Y[victimAction.getDirection()];
			
			// Check if the point is a neighbour to one of the brother's positions
			boolean letsDoThis = false;
			if (unitDistance(brotherA, victimNextX, victimNextY) == 1) {
				// A is the bait: A should run!
				bait = brotherA;
				attacker = brotherB;
				letsDoThis = true;
			} else if (unitDistance(brotherB, victimNextX, victimNextY) == 1) {
				// B is the bait: B should run!
				bait = brotherB;
				attacker = brotherA;
				letsDoThis = true;
			}
			
			// Are we close enough to do the strategy?
			if (letsDoThis) {
				// Lure the victim in and attack them
				int victimTimeTilMove = timeToFinishAction(victim);
				
				// ===== BAIT ACTION =====
				// The bait will dodge just after the victim moves
				unitLabels.put(bait, "Dodging");
				dodgeStrategy(bait, victim);
				
				// ===== ATTACKER ACTION =====
				if (victimTimeTilMove < attacker.getMoveTime() - 1) {
					// Find the tile which neighbours the enemy
					int attackDirection = UnitAction.DIRECTION_NONE;
					
					for (int direction = 0; direction < 4; direction++) {
						int targetX = attacker.getX() + UnitAction.DIRECTION_OFFSET_X[direction], targetY = attacker.getY() + UnitAction.DIRECTION_OFFSET_Y[direction];

						if (distance(victimNextX, victimNextY, targetX, targetY) == 1 && gs.free(targetX, targetY)) {
							attackDirection = direction;
						}
					}

					// Let's go
					if (attackDirection != UnitAction.DIRECTION_NONE) {
						unitLabels.put(attacker, "Attacking");
						actions.put(attacker, new Step(attacker, attackDirection));
					} else {
						unitLabels.put(attacker, "Couldn't attack!");
					}
				}
				else
				{
					// Wait diligently
					unitLabels.put(attacker, "Waiting to attack");
					actions.put(attacker, new DoNothing(attacker, 1));
				}
			}
		}
		else if (unitDistance(victim, brotherA) == 1 && getUnitAction(brotherA) == null) {
			// Attack the victim - if this victim isn't able to dodge it
			unitLabels.put(brotherA, "Attacking neighbour!");
			attack(brotherA, victim);
		}
		else if (unitDistance(victim, brotherB) == 1 && getUnitAction(brotherB) == null) {
			// Presumably, we're ready to attack the victim now
			unitLabels.put(brotherB, "Attacking neighbour!");
			attack(brotherB, victim);
		}
		
		// move bait to interception position
		if (unitDistance(bait, victim) > 2) {
			move(bait, victim.getX(), victim.getY() - 1);
			move(attacker, victim.getX(), victim.getY() - 2);
		}
	}*/
	
	// Dodges nearby attacks with a delay ('hopeful attack period')
	// if enemyToDodge is null, the nearest enemy will be identified and dodged
	// if enemyToDodge is out of range, nothing will happen
	// The strategy automatically attacks enemies that are likely to kill the player
	// The dodgeStrategy will either override other strategies or leave them untouched
	public boolean dodgeStrategy(Unit enemyToDodge) {
		if (enemyToDodge == null) {
			// Decide an enemy to dodge
			return false; // todo
		}
		
		// Can we act now?
		if (bot.getUnitAction(unit) != null) {
			return false; // can't do anything yet
		}

		// Dodge the enemy if a) they are about to move next to us and b) we have time to run
		int enemyTimeTilMove = bot.timeToFinishAction(enemyToDodge);
		
		if (enemyTimeTilMove < unit.getMoveTime() - 1 && enemyTimeTilMove + enemyToDodge.getAttackTime() > unit.getMoveTime()) {
			// Run to a safe neighbouring tile
			int runDirection = bot.findSafestNeighbour(unit.getX(), unit.getY(), unit.getMoveTime());
			
			action = new Step(unit, runDirection);
			return true;
		}
		else
		{
			// We either haven't encountered an enemy yet, or we can't move in time to escape. Eye for an eye, find nearby enemies to attack
			// Don't attack them if they're about to run away. We ain't gonna get outplayed by our own strategy!
			return attackDangerousNeighbourStrategy();
		}
	}
	
	// Attacks any enemies that could kill this unit before this unit can run
	// todo: ranged unit support
	public boolean attackDangerousNeighbourStrategy() {
		if (bot.getUnitAction(unit) != null) {
			return false;
		}
		
		// Find a neighbouring enemy to attack
		List<Unit> enemyToAttack = bot.findUnits((Unit e) -> units.isEnemy(e) && MapUtils.distance(e, unit) <= unit.getAttackRange() + 1);
		boolean isInDanger = false;
		
		for (Unit enemy : enemyToAttack) {
			UnitAction enemyAction = bot.getUnitAction(enemy);
			int enemyX = bot.getUnitX(enemy, unit.getMoveTime()), enemyY = bot.getUnitY(enemy, unit.getMoveTime()); 
			
			// Check if this enemy could attack us here before we can run
			if (MapUtils.distance(unit, enemyX, enemyY) <= enemy.getAttackRange() && bot.timeToFinishAction(enemy) + enemy.getAttackTime() <= unit.getMoveTime()) {
				// It's officially unsafe to run (assuming the enemy attacks us)
				isInDanger = true;
				
				// Check if we can actually kill this enemy before they kill us.
				// If we can't, don't bother trying - we could possibly attack something else instead
				if (MapUtils.distance(unit, enemy) <= unit.getAttackRange() && enemy.getAttackTime() >= unit.getAttackTime()) {
					// Attack this enemy
					DebugUtils.setUnitLabel(unit, "DangNeighbor: Attack");
					action = new Attack(unit, enemy, bot.getPathFinding());
					return true;
				}
			}
		}
		
		// If no one was attacked, but there is a dangerous enemy approaching, wait for it to arrive and kill it
		if (isInDanger) {
			DebugUtils.setUnitLabel(unit, "DangNeighbor: In danger, waiting for attack");
			action = new DoNothing(unit, 1);
		}
		
		return isInDanger;
	}
	
	public void ninjaWarriorStrategy() {
		if (bot.getUnitAction(unit) != null) {
			return;
		}
		
		Unit closestEnemy = bot.findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> units.isEnemy(u));
		
		if (dodgeStrategy(closestEnemy)) {
			DebugUtils.setUnitLabel(unit, "Basic: Dodging!");
		} else {
			DebugUtils.setUnitLabel(unit, "Basic: Charrge!");
			action = new Attack(unit, closestEnemy, bot.getPathFinding());
		}
	}
	
	public void baseProduceCollectorStrategy() {
		// Drop a collector
		// Put a worker in the position closest to a resource
		Unit closestResource = bot.findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> u.getType().isResource);
		int trainX = 0, trainY = 0;

		if (closestResource != null) {
			trainX = closestResource.getX();
			trainY = closestResource.getY();
		}

		action = new TrainWithPreferredTile(unit, units.worker, trainX, trainY);
	}
	
	public void baseProduceRusherStrategy() {
		action = new TrainWithPreferredTile(unit, units.worker, 10, 10);
	}
	
	// AttackVulnerableNeighbourStrategy
	
	// Do a tree search to find an optimal direction to run away
	public void scoobyShaggyStrategy() {
		// Create a tree containing GameStates with positions of nearby units
		// Explore all possible combinations of moves for each opponent
		// Choose the move that optimises for 'number of free spaces for me to run in'
		
		/* create tree */
		
		/* for 'depth' game ticks: */
	}
	
	/**
	 * Returns the action to be performed by this bot
	 */
	public AbstractAction getAction() {
		return action;
	}
}
