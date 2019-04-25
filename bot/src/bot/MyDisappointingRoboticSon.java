package bot;

import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Attack;
import ai.abstraction.Idle;
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
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.HashMap;

import rts.*;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;

import utilities.MapUtils;
import utilities.DebugUtils;
import utilities.UnitUtils;

@FunctionalInterface
interface UnitConditions {
	boolean meetsConditions(Unit u);
}

public class MyDisappointingRoboticSon extends AbstractionLayerAI {
	private UnitTypeTable utt;

	// Tick state variables
	private int playerId = 0; /**< ID of the current player */
	private Player player = null; /**< The current player */
	private GameState gs; /**< The current game state */
	private PhysicalGameState pgs; /**< The current physical game state */
	
	private UnitUtils units;
	private HashMap<Unit, UnitThinker> unitThinkers = new HashMap<Unit, UnitThinker>();
	
	public MyDisappointingRoboticSon(UnitTypeTable utt) {
		// Initialise parent
		super(new AStarPathFinding());

		// Initialise variables
		this.utt = utt;
		this.units = new UnitUtils(utt);
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

		// Refresh tick state variables
		this.player = gs.getPlayer(player);
		this.playerId = player;
		this.gs = gs;
		this.pgs = pgs;

		this.units.setCurrentPlayer(playerId);
		
		// Synchronise the unit thinkers with the units
		synchroniseUnitThinkers();

		// Begin an evil strategy!?
		List<Unit> targetedEnemies = new LinkedList<Unit>();
		// Note the concentration of DPS into a small area
		// 4 workers can distribute their DPS very effectively, and move fast

		// Find out how quickly an enemy could attack us
		List<Unit> closestBases = findUnits((Unit u) -> units.isBase(u) && u.getPlayer() == player);
		int closestEnemyTimeToBase = 0;

		if (closestBases.size() > 0) {
			Unit closestBaseToDefend = closestBases.get(0);

			if (closestBaseToDefend != null) {
				Unit closestEnemyUnitToBase = findSoonestUnit(closestBaseToDefend.getX(), closestBaseToDefend.getY(), (Unit u) -> u.getPlayer() != player && u.getPlayer() != -1);

				if (closestEnemyUnitToBase != null) {
					closestEnemyTimeToBase = MapUtils.timeToReach(closestEnemyUnitToBase, closestBaseToDefend);
					// debug("Closest enemy time to reach base: " + closestEnemyTimeToBase);
				}
			}
		}

		int numResources = 0;
		int numRanged = countMyUnits((Unit u) -> units.isRanged(u));
		int numBarracks = countMyUnits((Unit u) -> units.isBarracks(u));
		int numWorkers = countMyUnits((Unit u) -> units.isWorker(u));
		int numBases = countMyUnits((Unit u) -> units.isBase(u));
		boolean doBuildBarracks = numRanged < 1 && closestEnemyTimeToBase > 80 && numWorkers > 1;
		int numWorkersCollectingStuff = 0;
		int numUsableResources = gs.getPlayer(player).getResources();
		int numBrothers = 0;
		Unit brothers[] = new Unit[2];

		doBuildBarracks = false; // temp: disable barracks build
		
		for (Unit unit : pgs.getUnits()) {
			if (unit.getPlayer() == player) {
				UnitThinker thinker = unitThinkers.get(unit);
				
				if (units.isWorker(unit)) {
					if (numWorkersCollectingStuff < 1 && numBases > 0) {
						// This worker will collect resources for the base
						thinker.strategy = () -> thinker.workerCollectStrategy();

						numWorkersCollectingStuff++;
					} else if (numWorkersCollectingStuff == 1 && doBuildBarracks) {
						thinker.strategy = () -> thinker.workerBuildBarracksStrategy();
						
						numWorkersCollectingStuff++;
					} else if (numBrothers < 2) {
						brothers[numBrothers++] = unit;
					} else {
						// Attack the closest enemy
						Unit closestEnemy = findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> u.getPlayer() != player && u.getPlayer() != -1);

						if (closestEnemy != null && getUnitAction(unit) == null) {
							thinker.strategy = () -> thinker.ninjaWarriorStrategy();
							
							targetedEnemies.add(closestEnemy);
						}
					}
				}
				
				// Power-swarm strategy
				// Focus multiple workers on one ranged enemy
				// if we beat the ranged enemy with two workers, we're on equal footing
				
				if (units.isBase(unit)) {
					if (getUnitAction(unit) == null) {
						if (numUsableResources >= units.worker.cost && (numUsableResources > units.barracks.cost + 1 || !doBuildBarracks)) {
							// Produce a worker
							if (numWorkers == 0) {
								thinker.strategy = () -> thinker.baseProduceCollectorStrategy();
							} else {
								// Drop an attacker
								// Put a worker in the position closest to an enemy unit
								thinker.strategy = () -> thinker.baseProduceRusherStrategy();
							}
						} else {
							DebugUtils.setUnitLabel(unit, "Doing nothing for a while (res " + numUsableResources + ", " + doBuildBarracks + ")");
							
							thinker.strategy = () -> thinker.doNothingStrategy();
						}
					} else if (gs.getActionAssignment(unit).action.getType() != UnitAction.TYPE_PRODUCE) {
						DebugUtils.print("Base assignment " + gs.getActionAssignment(unit));
					}
				}

				if (units.isBarracks(unit) && getUnitAction(unit) == null) {
					if (gs.getPlayer(player).getResources() >= units.ranged.cost) {
						train(unit, units.ranged);
					}
				}

				if (units.isRanged(unit)) {
					Unit closestEnemy = findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> u.getPlayer() != player && u.getPlayer() != -1);
					attack(unit, closestEnemy);
				}
			}
		}
		
		// Test the brothers strategy
		if (numBrothers == 2) {
			//evilBrotherStrategy(brothers[0], brothers[1]);
		}
		
		// Tick the thinkers
		for (Unit key : unitThinkers.keySet()) {
			unitThinkers.get(key).tick(gs);
			
			actions.put(key, unitThinkers.get(key).getAction());
		}
		
		// Assign empty actions to units which do not have any action
		for (Unit u : pgs.getUnits()) {
			try {
				if (u.getPlayer() == playerId && getUnitAction(u) == null && (!actions.containsKey(u) || actions.get(u).completed(gs))) {
					actions.put(u, new DoNothing(u, 1));
				}
			}
			catch(Exception e) {
				String crap = e.getMessage();
				DebugUtils.print("Exception: " + crap);
			}
		}

		// Done! Play our moves!
		return translateActions(player, gs);
	}

	@Override
	public List<ParameterSpecification> getParameters() {
		return new ArrayList<>();
	}
	
	// Synchronises unit thinkers with the gamestate, map, etc
	private void synchroniseUnitThinkers() {
		// Since we can't tell when a bot is deleted, copy unit thinkers from the unit thinker map to a new one for each existing unit
		// The ones that have died will be left behind
		// Create a new unit thinker hash map
		HashMap<Unit, UnitThinker> newUnitThinkers = new HashMap<Unit, UnitThinker>();
		
		// Collect all the existing unit thinkers into the new list
		for (Unit u : gs.getUnits()) {
			if (u.getPlayer() == playerId) {
				if (unitThinkers.containsKey(u)) {
					// Move the existing thinker to the new list
					newUnitThinkers.put(u, unitThinkers.get(u));
				} else {
					// This is a new unit: Create a new unit thinker!
					newUnitThinkers.put(u, new UnitThinker(u, this, units, playerId));
				}
			}
		}
		
		// Reassign the unit thinker map with the new map
		unitThinkers = newUnitThinkers;
	}
	
	// Returns a list of units that match the condition specified
	public List<Unit> findUnits(UnitConditions conditions) {
		List<Unit> result = new LinkedList<Unit>();

		for (Unit u : pgs.getUnits()) {
			if (conditions.meetsConditions(u)) {
				result.add(u);
			}
		}

		return result;
	}

	// Returns the closest unit of the given conditions
	public Unit findClosestUnit(int x, int y, UnitConditions conditions) {
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
	public Unit findSoonestUnit(int x, int y, UnitConditions conditions) {
		int bestTravelTime = Integer.MAX_VALUE;
		Unit bestUnit = null;

		for (Unit unit : pgs.getUnits()) {
			// Verify the conditions
			if (conditions.meetsConditions(unit)) {
				int thisTravelTime = MapUtils.timeToReach(unit, x, y);

				if (thisTravelTime < bestTravelTime) {
					bestTravelTime = thisTravelTime;
					bestUnit = unit;
				}
			}
		}

		return bestUnit;
	}

	// Returns the direction of the safest tile next to the position
	public int findSafestNeighbour(int x, int y, int duration) {
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
	
	// Returns how long it would take to receive a certain amount of damage at the given tile, if every enemy unit attacked
	public int getDangerTime(int x, int y, int damageAmount) {
		if (damageAmount == 0) {
			return Integer.MAX_VALUE;
		}
		
		int danger = 0;
		
		for (Unit u : gs.getUnits()) {
			if (u.getPlayer() != playerId && u.getType().canAttack) {
				int currentTimePassed = 0;
				int enemyX = u.getX(), enemyY = u.getY();
				int timeToFinishCurrentAction = timeToFinishAction(u);
				
				if (timeToFinishCurrentAction > 0) {
					// Simulate enemy movement if necessary
					UnitAction enemyAction = getUnitAction(u);
					
					if (enemyAction.getType() == UnitAction.TYPE_MOVE) {
						enemyX += UnitAction.DIRECTION_OFFSET_X[enemyAction.getDirection()];
						enemyY += UnitAction.DIRECTION_OFFSET_Y[enemyAction.getDirection()];
					}
					
					currentTimePassed += timeToFinishCurrentAction;
				}
				
				// Check how long it would take to get in range of the player
				int distance = MapUtils.distance(enemyX, enemyY, x, y);
				int timeToArrive = Math.max(distance - u.getType().attackRange, 0) * u.getType().moveTime;
				
				// Update the danger level
				if (timeToArrive < duration) {
					danger = Math.max(danger, (duration - timeToArrive) / u.getAttackTime() * u.getType().maxDamage);
				}
			}
		}
		
		return danger;
	}
	
	// Returns the number of units meeting the specified conditions
	public int countMyUnits(UnitConditions conditions) {
		int count = 0;

		for (Unit u : pgs.getUnits()) {
			if (u.getPlayer() == playerId && conditions.meetsConditions(u)) {
				count++;
			}
		}

		return count;
	}
	
	// Returns the unit's current action, or null if there is no active action assigned
	public UnitAction getUnitAction(Unit u) {
		UnitActionAssignment assignment = gs.getActionAssignment(u);
		
		return assignment != null ? assignment.action : null;
	}
	
	// Returns the X position of a unit after the given time period
	public int getUnitX(Unit u, int period) {
		UnitAction action = getUnitAction(u);
		
		if (action != null && action.getType() == UnitAction.TYPE_MOVE && timeToFinishAction(u) <= period) {
			return u.getX() + UnitAction.DIRECTION_OFFSET_X[action.getDirection()];
		} else {
			return u.getX();	
		}
	}
	
	// Returns the Y position of a unit after the given time period
	public int getUnitY(Unit u, int period) {
		UnitAction action = getUnitAction(u);
		
		if (action != null && action.getType() == UnitAction.TYPE_MOVE && timeToFinishAction(u) <= period) {
			return u.getY() + UnitAction.DIRECTION_OFFSET_Y[action.getDirection()];
		} else {
			return u.getY();
		}
	}
	
	// Returns how long it would take for a unit to finish its current action
	public int timeToFinishAction(Unit u) {
		if (gs.getActionAssignment(u) == null) {
			return 0;
		}
		
		return gs.getActionAssignment(u).action.ETA(u) - (gs.getTime() - gs.getActionAssignment(u).time);
	}
}