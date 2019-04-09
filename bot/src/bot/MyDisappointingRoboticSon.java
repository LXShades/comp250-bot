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
import java.util.concurrent.Callable;

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

		for (Unit unit : pgs.getUnits()) {
			if (unit.getPlayer() == player) {
				if (unit.getType() == worker) {
					if (numWorkersCollectingStuff < 1 && numBases > 0) {
						workerCollectStrategy(unit);

						numWorkersCollectingStuff++;
					} else if (numWorkersCollectingStuff == 1 && doBuildBarracks) {
						workerBuildBarracksStrategy(unit);
						
						//debug("build time remaining " + timeToFinishAction(unit));
						
						numWorkersCollectingStuff++;
					} else {
						// Attack the closest enemy
						Unit closestEnemy = findClosestUnit(unit.getX(), unit.getY(),
								(Unit u) -> u.getPlayer() != player && u.getPlayer() != -1);

						if (closestEnemy != null) {
							attack(unit, closestEnemy);
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
						if (numUsableResources >= worker.cost
								&& (numUsableResources > barracks.cost + 1 || !doBuildBarracks)) {
							// Produce a worker
							if (numWorkers == 0) {
								// Put a worker in the position closest to a resource
								Unit closestResource = findClosestUnit(unit.getX(), unit.getY(),
										(Unit u) -> u.getType().isResource);
								int trainX = 0, trainY = 0;

								if (closestResource != null) {
									trainX = closestResource.getX();
									trainY = closestResource.getY();
								}

								debug("Dropping a worker for " + trainX + ", " + trainY);
								actions.put(unit, new TrainWithPreferredTile(unit, worker, trainX, trainY));
							} else {
								// Put a worker in the position closest to an enemy unit
								actions.put(unit, new TrainWithPreferredTile(unit, worker, 10, 10));
								debug("Dropping an attacker");
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
	/*private void brotherStrategy(Unit brotherA, Unit brotherB) {
		// ===== evil brothers strategy =====
		// wait for enemy about to move to adjacent tiles
		// if brother B is in valid position, prepare to move back
		// brother B prepares to move into place
	}*/
	
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

	// Rerturns the closest unit of the given conditions
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

	// attacking worker:
	// pick a target to attack
	// but attack anything along the way, or avoid them for a limited time (if chase
	// is detected, maybe stall them?

	private void debug(String text) {
		System.out.print(text + "\n");
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

	// Returns the distance between two units, in the number of steps it would take
	// to reach
	private int unitDistance(Unit a, Unit b) {
		return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
	}

	// Returns the distance between a unit and a position
	private int unitDistance(Unit a, int x, int y) {
		return Math.abs(a.getX() - x) + Math.abs(a.getY() - y);
	}
	
	private int timeToFinishAction(Unit u) {
		if (gs.getActionAssignment(u) == null) {
			return 0;
		}
		
		return gs.getActionAssignment(u).action.ETA(u) - (gs.getTime() - gs.getActionAssignment(u).time);
	}
}