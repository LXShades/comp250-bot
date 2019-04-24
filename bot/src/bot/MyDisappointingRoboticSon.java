package bot;

import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Attack;
import ai.abstraction.Idle;
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
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.HashMap;

import rts.*;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;

@FunctionalInterface
interface UnitConditions {
	boolean meetsConditions(Unit u);
}

public class MyDisappointingRoboticSon extends AbstractionLayerAI {
	private UnitTypeTable utt;

	// Unit type information
	private UnitType worker;
	private UnitType base;
	private UnitType resource;
	private UnitType barracks;
	private UnitType light;
	private UnitType heavy;
	private UnitType ranged;

	// Tick state variables
	private int playerId = 0; // ID of the current player
	private Player player = null; // Current player
	private GameState gs; // Current game state
	private PhysicalGameState pgs; // Current physical game state
	
	// Debug variables
	private boolean isPaused; // whether GameVisualSimulationTest should pause
	
	private HashMap<Unit, String> unitLabels = new HashMap<Unit, String>();

	public MyDisappointingRoboticSon(UnitTypeTable utt) {
		// Initialise parent
		super(new AStarPathFinding());

		// Initialise variables
		this.utt = utt;

		// Prefetch game unit type information
		worker = utt.getUnitType("Worker");
		base = utt.getUnitType("Base");
		resource = utt.getUnitType("Resources");
		barracks = utt.getUnitType("Barracks");
		light = utt.getUnitType("Light");
		heavy = utt.getUnitType("Heavy");
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

		// Set tick state variables
		this.player = gs.getPlayer(player);
		this.playerId = player;
		this.gs = gs;
		this.pgs = pgs;

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

		List<Unit> targetedEnemies = new LinkedList<Unit>();
		// Note the concentration of DPS into a small area
		// 4 workers can distribute their DPS very effectively, and move fast

		// Find out how quickly an enemy could attack us
		List<Unit> closestBases = findUnits((Unit u) -> u.getType() == base && u.getPlayer() == player);
		int closestEnemyTimeToBase = 0;

		if (closestBases.size() > 0) {
			Unit closestBaseToDefend = closestBases.get(0);

			if (closestBaseToDefend != null) {
				Unit closestEnemyUnitToBase = findSoonestUnit(closestBaseToDefend.getX(), closestBaseToDefend.getY(),
						(Unit u) -> u.getPlayer() != player && u.getPlayer() != -1);

				if (closestEnemyUnitToBase != null) {
					closestEnemyTimeToBase = timeToReach(closestEnemyUnitToBase, closestBaseToDefend);
					// debug("Closest enemy time to reach base: " + closestEnemyTimeToBase);
				}
			}
		}

		int numResources = 0;
		int numRanged = countMyUnits((Unit u) -> u.getType() == ranged);
		int numBarracks = countMyUnits((Unit u) -> u.getType() == barracks);
		int numWorkers = countMyUnits((Unit u) -> u.getType() == worker);
		int numBases = countMyUnits((Unit u) -> u.getType() == base);
		boolean doBuildBarracks = numRanged < 1 && closestEnemyTimeToBase > 80 && numWorkers > 1;
		int numWorkersCollectingStuff = 0;
		int numUsableResources = gs.getPlayer(player).getResources();
		int numBrothers = 0;
		Unit brothers[] = new Unit[2];

		doBuildBarracks = false; // temp: disable barracks build
		
		for (Unit unit : pgs.getUnits()) {
			if (unit.getPlayer() == player) {
				if (unit.getType() == worker) {
					if (numWorkersCollectingStuff < 1 && numBases > 0) {
						workerCollectStrategy(unit);

						numWorkersCollectingStuff++;
					} else if (numWorkersCollectingStuff == 1 && doBuildBarracks) {
						workerBuildBarracksStrategy(unit);
						
						numWorkersCollectingStuff++;
					} else if (numBrothers < 2) {
						brothers[numBrothers++] = unit;
					} else {
						// Attack the closest enemy
						Unit closestEnemy = findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> u.getPlayer() != player && u.getPlayer() != -1);

						if (closestEnemy != null && getUnitAction(unit) == null) {
							if (dodgeStrategy(unit, closestEnemy)) {
								unitLabels.put(unit, "Basic: Dodging!");
							} else {
								unitLabels.put(unit, "Basic: Charrge!");
								attack(unit, closestEnemy);
							}
							targetedEnemies.add(closestEnemy);
						}
					}
				}
				
				// Power-swarm strategy
				// Focus multiple workers on one ranged enemy
				// if we beat the ranged enemy with two workers, we're on equal footing
				
				if (unit.getType() == base) {
					if (gs.getActionAssignment(unit) == null) {
						if (gs.getTime() < 2) {
							actions.put(unit, new DoNothing(unit, 3));
							continue;
						}
						if (numUsableResources >= worker.cost && (numUsableResources > barracks.cost + 1 || !doBuildBarracks)) {
							// Produce a worker
							if (numWorkers == 0) {
								// Drop a collector
								// Put a worker in the position closest to a resource
								Unit closestResource = findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> u.getType().isResource);
								int trainX = 0, trainY = 0;

								if (closestResource != null) {
									trainX = closestResource.getX();
									trainY = closestResource.getY();
								}

								actions.put(unit, new TrainWithPreferredTile(unit, worker, trainX, trainY));
							} else {
								// Drop an attacker
								// Put a worker in the position closest to an enemy unit
								actions.put(unit, new TrainWithPreferredTile(unit, worker, 10, 10));
							}
						} else {
							debug("Doing nothing for a while (res " + numUsableResources + ", " + doBuildBarracks
									+ ")");
							actions.put(unit, new DoNothing(unit, 5));
						}
					} else if (gs.getActionAssignment(unit).action.getType() != UnitAction.TYPE_PRODUCE) {
						debug("Base assignment " + gs.getActionAssignment(unit));
					}
				}

				if (unit.getType() == barracks && gs.getActionAssignment(unit) == null) {
					if (gs.getPlayer(player).getResources() >= ranged.cost) {
						train(unit, ranged);
					}
				}

				if (unit.getType() == ranged) {
					Unit closestEnemy = findClosestUnit(unit.getX(), unit.getY(),
							(Unit u) -> u.getPlayer() != player && u.getPlayer() != -1);
					attack(unit, closestEnemy);
					debug("I'm Ranged. My closest enemy is " + closestEnemy);
				}
			}
		}
		
		// Test the brothers strategy
		if (numBrothers == 2) {
			evilBrotherStrategy(brothers[0], brothers[1]);
		}
		

		// Assign empty actions to units which do not have any action
		for (Unit u : pgs.getUnits()) {
			if (u.getPlayer() == playerId && getUnitAction(u) == null && (!actions.containsKey(u) || actions.get(u).completed(gs))) {
				actions.put(u, new DoNothing(u, 1));
			}
		}

		return translateActions(player, gs);
	}

	@Override
	public List<ParameterSpecification> getParameters() {
		return new ArrayList<>();
	}
	
	// Sends the worker out to collect resources
	private void workerCollectStrategy(Unit unit) {
		// Go collect resources and stuff
		Unit closestResource = null, closestBase = null;

		// Find the closest relevant units
		closestResource = findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> u.getType().isResource);
		closestBase = findClosestUnit(unit.getX(), unit.getY(),
				(Unit u) -> u.getType() == base && u.getPlayer() == playerId);

		if (closestResource != null && closestBase != null) {
			if (unit.getResources() == 1) {
				if (unitDistance(unit, closestBase) > 1) {
					// If there is not an immediately neighbouring base, go to the tile on the base
					// that is closest to a resource
					int targetBaseX = closestBase.getX(), targetBaseY = closestBase.getY();
					int neighbourPositions[] = new int[] { targetBaseX - 1, targetBaseY, targetBaseX + 1, targetBaseY,
							targetBaseX, targetBaseY - 1, targetBaseX, targetBaseY + 1 };

					// Find the tile closest to both the base and resource
					int closestPositionIndex = 0, closestPositionDistance = 99999;
					for (int i = 0; i < 4; i++) {
						int positionDistance = unitDistance(closestResource, neighbourPositions[i * 2],
								neighbourPositions[i * 2 + 1]);

						if (positionDistance < closestPositionDistance) {
							closestPositionDistance = positionDistance;
							closestPositionIndex = i;
							break;
						}
					}

					// Begin the journey of a thousand tiles
					move(unit, neighbourPositions[closestPositionIndex * 2],
							neighbourPositions[closestPositionIndex * 2 + 1]);
				} else {
					// We're next to a base! Drop our shizzle here
					harvest(unit, closestResource, closestBase);
				}

				// Also: Kill enemy workers if they meet me (self-defense!)
			} else {
				harvest(unit, closestResource, closestBase);
			}
		}
	}

	// Sends a worker to build barracks
	private void workerBuildBarracksStrategy(Unit worker) {
		List<Integer> reservedPositions = new LinkedList<Integer>();
		buildIfNotAlreadyBuilding(worker, barracks, worker.getX(), worker.getY(), reservedPositions, player, pgs);
	}
	
	// Sends a pair of brothers, one to lure an attacker in and the other to kill the attacker
	private void evilBrotherStrategy(Unit brotherA, Unit brotherB) {
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
	}
	
	// Dodges nearby attacks with a delay ('hopeful attack period')
	// if enemyToDodge is null, the nearest enemy will be identified and dodged
	// if enemyToDodge is out of range, nothing will happen
	// The strategy automatically attacks enemies that are likely to kill the player
	// The dodgeStrategy will either override other strategies or leave them untouched
	private boolean dodgeStrategy(Unit u, Unit enemyToDodge) {
		if (enemyToDodge == null) {
			// Decide an enemy to dodge
			return false; // todo
		}
		
		// Can we act now?
		if (getUnitAction(u) != null) {
			return false; // can't do anything yet
		}

		// Dodge the enemy if a) they are about to move next to us and b) we have time to run
		int enemyTimeTilMove = timeToFinishAction(enemyToDodge);
		
		if (enemyTimeTilMove < u.getMoveTime() - 1 && enemyTimeTilMove + enemyToDodge.getAttackTime() > u.getMoveTime()) {
			// Run to a safe neighbouring tile
			int runDirection = findSafestNeighbour(u.getX(), u.getY(), u.getMoveTime());
			
			actions.put(u, new Step(u, runDirection));
			return true;
		}
		else
		{
			// We either haven't encountered an enemy yet, or we can't move in time to escape. Eye for an eye, find nearby enemies to attack
			// Don't attack them if they're about to run away. We ain't gonna get outplayed by our own strategy!
			return attackDangerousNeighbourStrategy(u);
		}
	}
	
	// Attacks any enemies that could kill this unit before this unit can run
	// todo: ranged unit support
	private boolean attackDangerousNeighbourStrategy(Unit u) {
		if (getUnitAction(u) != null) {
			return false;
		}
		
		// Find a neighbouring enemy to attack
		List<Unit> enemyToAttack = findUnits((Unit e) -> e.getPlayer() != playerId && unitDistance(e, u) <= u.getAttackRange() + 1);
		boolean isInDanger = false;
		
		for (Unit enemy : enemyToAttack) {
			UnitAction enemyAction = getUnitAction(enemy);
			int enemyActionDuration = timeToFinishAction(enemy);
			int enemyX = getUnitX(enemy, u.getMoveTime()), enemyY = getUnitY(enemy, u.getMoveTime()); 
			
			// Check if this enemy could attack us here before we can run
			if (unitDistance(u, enemyX, enemyY) <= enemy.getAttackRange() && timeToFinishAction(enemy) + enemy.getAttackTime() <= u.getMoveTime()) {
				// It's officially unsafe to run (assuming the enemy attacks us)
				isInDanger = true;
				
				// Check if we can actually kill this enemy before they kill us.
				// If we can't, don't bother trying - we will lose time
				if (unitDistance(u, enemy.getX(), enemy.getY()) <= u.getAttackRange() && enemy.getAttackTime() >= u.getAttackTime()) {
					// Attack this enemy
					unitLabels.put(u, "DangNeighbor: Attack");
					attack(u, enemy);
					return true;
				}
			}
		}
		
		// If no one was attacked, but there is a dangerous enemy approaching, wait for it to arrive and kill it
		if (isInDanger) {
			unitLabels.put(u, "DangNeighbor: In danger, waiting for attack");
			actions.put(u, new DoNothing(u, 1));
		}
		
		return isInDanger;
	}
	
	// AttackVulnerableNeighbourStrategy
	
	// Do a tree search to find an optimal direction to run away
	private void scoobyShaggyStrategy() {
		// Create a tree containing GameStates with positions of nearby units
		// Explore all possible combinations of moves for each opponent
		// Choose the move that optimises for 'number of free spaces for me to run in'
		
		/* create tree */
		
		/* for 'depth' game ticks: */
	}
	
	// Returns a list of units that match the condition specified
	private List<Unit> findUnits(UnitConditions conditions) {
		List<Unit> result = new LinkedList<Unit>();

		for (Unit u : pgs.getUnits()) {
			if (conditions.meetsConditions(u)) {
				result.add(u);
			}
		}

		return result;
	}

	// Returns the closest unit of the given conditions
	private Unit findClosestUnit(int x, int y, UnitConditions conditions) {
		int closestDistance = Integer.MAX_VALUE;
		Unit closestUnit = null;

		// Find the unit closest to the supplied position
		for (Unit unit : pgs.getUnits()) {
			// Ensure the unit meets the supplied conditions
			if (conditions.meetsConditions(unit)) {
				/*
				 * Since only horizontal and vertical movements are possible, distance is always
				 * equal to xDifference + yDifference
				 */
				int thisDistance = Math.abs(unit.getX() - x) + Math.abs(unit.getY() - y);

				if (thisDistance < closestDistance) {
					closestUnit = unit;
					closestDistance = thisDistance;
				}
			}
		}

		return closestUnit;
	}

	// Returns the unit that can reach the given position soonest
	private Unit findSoonestUnit(int x, int y, UnitConditions conditions) {
		int bestTravelTime = Integer.MAX_VALUE;
		Unit bestUnit = null;

		for (Unit unit : pgs.getUnits()) {
			// Verify the conditions
			if (conditions.meetsConditions(unit)) {
				int thisTravelTime = timeToReach(unit, x, y);

				if (thisTravelTime < bestTravelTime) {
					bestTravelTime = thisTravelTime;
					bestUnit = unit;
				}
			}
		}

		return bestUnit;
	}

	// Returns the direction of the safest tile next to the position
	private int findSafestNeighbour(int x, int y, int duration) {
		int safestDangerLevel = Integer.MAX_VALUE;  // 'dangerLevel' is determined by 'how quickly could you die by standing in this spot'
		int safestTile = UnitAction.DIRECTION_DOWN;
		
		// Iterate every neighbouring tile
		for (int i = 0; i < 4; i++) {
			int targetX = x + UnitAction.DIRECTION_OFFSET_X[i], targetY = y + UnitAction.DIRECTION_OFFSET_Y[i];
			// Skip if we can't move here
			if (targetX >= pgs.getWidth() || targetX < 0 || targetY >= pgs.getHeight() || targetY < 0 || !gs.free(targetX, targetY)) {
				continue;
			}
			
			int dangerLevel = getDangerLevel(x, y, duration);
			
			if (dangerLevel < safestDangerLevel) {
				safestDangerLevel = dangerLevel;
				safestTile = i;
			}
		}
		
		// Return the direction
		return safestTile;
	}
	
	// Returns how much damage you could receive from being on a specific tile, assuming one bot attacked it
	private int getDangerLevel(int x, int y, int duration) {
		int danger = 0;
		
		for (Unit u : gs.getUnits()) {
			if (u.getPlayer() != playerId && u.getType().canAttack) {
				// Check how long it would take to get here
				int distance = distance(getUnitX(u, duration), getUnitY(u, duration), x, y);
				int timeToArrive = Math.max(distance - u.getType().attackRange, 0) * u.getType().moveTime;
				
				// Let the unit finish its current action first
				timeToArrive += timeToFinishAction(u);
				
				// Update the danger level
				if (timeToArrive < duration) {
					danger = Math.max(danger, (duration - timeToArrive) / u.getAttackTime() * u.getType().maxDamage);
				}
			}
		}
		
		return danger;
	}
	
	// Returns the number of units meeting the specified conditions
	private int countMyUnits(UnitConditions conditions) {
		int count = 0;

		for (Unit u : pgs.getUnits()) {
			if (u.getPlayer() == playerId && conditions.meetsConditions(u)) {
				count++;
			}
		}

		return count;
	}

	// Returns the time it takes for a unit to reach another unit, assuming the
	// other unit is still
	private int timeToReach(Unit source, Unit target) {
		return timeToReach(source, target.getX(), target.getY());
	}

	// Returns the time it takes for a unit to reach a position
	private int timeToReach(Unit source, int destinationX, int destinationY) {
		// Make sure this unit can actually move!
		if (!source.getType().canMove) {
			return Integer.MAX_VALUE;
		}

		// Find the length of the path to this destination (todo pathfinding)
		return unitDistance(source, destinationX, destinationY) * source.getType().moveTime;
	}

	// Returns the distance between two locations
	private int distance(int aX, int aY, int bX, int bY) {
		return Math.abs(aX - bX) + Math.abs(aY - bY);
	}
	
	// Returns the distance between two units, in the number of steps it would take to reach
	private int unitDistance(Unit a, Unit b) {
		return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
	}

	// Returns the distance between a unit and a position, in the number of steps it would take to reach
	private int unitDistance(Unit a, int x, int y) {
		return Math.abs(a.getX() - x) + Math.abs(a.getY() - y);
	}
	
	// Returns the unit's current action, or null if there is no active action assigned
	private UnitAction getUnitAction(Unit u) {
		UnitActionAssignment assignment = gs.getActionAssignment(u);
		
		return assignment != null ? assignment.action : null;
	}
	
	// Returns the X position of a unit after the given time period
	private int getUnitX(Unit u, int period) {
		UnitAction action = getUnitAction(u);
		
		if (action != null && action.getType() == UnitAction.TYPE_MOVE) {
			return u.getX() + UnitAction.DIRECTION_OFFSET_X[action.getDirection()];
		} else {
			return u.getX();	
		}
	}
	
	// Returns the Y position of a unit after the given time period
	private int getUnitY(Unit u, int period) {
		UnitAction action = getUnitAction(u);
		
		if (action != null && action.getType() == UnitAction.TYPE_MOVE) {
			return u.getY() + UnitAction.DIRECTION_OFFSET_Y[action.getDirection()];
		} else {
			return u.getY();	
		}
	}
	
	// Returns how long it would take for a unit to finish its current action
	private int timeToFinishAction(Unit u) {
		if (gs.getActionAssignment(u) == null) {
			return 0;
		}
		
		return gs.getActionAssignment(u).action.ETA(u) - (gs.getTime() - gs.getActionAssignment(u).time);
	}

	// ========== Debugging functions ==========
	// Prints a debug message
	private void debug(String text) {
		System.out.print(text + "\n");
	}
	
	// pauses the simulation, only affects custom GameVisualSimulationTest
	public void pause() {
		isPaused = true;

		debug("Paused");
	}
	
	// Pauses the simulation, displaying a custom pause message
	public void pause(String pauseMessage) {
		isPaused = true;

		debug(pauseMessage);
	}
	
	public void unpause() {
		isPaused = false;
	}
	
	public boolean isPaused() {
		return isPaused;
	}
	
	// Returns the unit label hashmap
	public HashMap<Unit, String> getUnitLabels() {
		return unitLabels;
	}
}