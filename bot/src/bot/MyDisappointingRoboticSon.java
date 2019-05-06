package bot;

import ai.GameEvaluator;
import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Attack;
import ai.abstraction.Idle;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
import bot.UnitThinker.StrategyFunc;
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

/**
 * \brief A disappointing robotic son who coordinates his own legion of workers and ranged units to their doom against ever-advancing warbots
 * \author Louis
 *
 */
public class MyDisappointingRoboticSon extends AbstractionLayerAI {
	private UnitTypeTable utt; /** < Unit type table stored for clones */

	// Tick state variables
	private int playerId = 0; /** < ID of the current player */
	private Player player = null; /** < The current player */
	private GameState gs; /** < The current game state */
	private PhysicalGameState pgs; /** < The current physical game state */

	private UnitUtils units; /** < Unit utilities */
	private HashMap<Unit, UnitThinker> unitThinkers = new HashMap<Unit, UnitThinker>(); /**< UnitThinkers associated with each unit */

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

		this.units.tick(playerId, gs);

		// Synchronise the unit thinkers with the units
		synchroniseUnitThinkers();

		// Begin an evil strategy!?
		GameEvaluator eval = new GameEvaluator(playerId, gs, units);

		// Assign default action to all units
		for (Unit unit : units.myUnits) {
			unitThinkers.get(unit).strategy = null;
		}
		
		// --- Coordinate the workers ---
		coordinateWorkers(eval);

		// --- Coordinate the other attackers ---
		coordinateAttackers(eval);
		
		// --- Coordinate the producers ---
		coordinateProducers(eval);

		// Tick the thinkers
		boolean[] blockedTiles = new boolean[pgs.getWidth() * pgs.getHeight()];
		
		for (Unit key : unitThinkers.keySet()) {
			unitThinkers.get(key).tick(gs, blockedTiles);

			actions.put(key, unitThinkers.get(key).getAction());
		}
		
		// Done! Play our moves!
		return translateActions(player, gs);
	}

	@Override
	public List<ParameterSpecification> getParameters() {
		return new ArrayList<>();
	}

	/**
	 * \brief Synchronises the unit thinkers with this bot's units
	 */
	private void synchroniseUnitThinkers() {
		// Since we can't tell when a bot is deleted, copy unit thinkers from the unit
		// thinker map to a new one for each existing unit
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
					newUnitThinkers.put(u, new UnitThinker(u, units));
				}
			}
		}

		// Reassign the unit thinker map with the new map
		unitThinkers = newUnitThinkers;
	}
	
	/**
	 * \brief Coordinates the workers
	 * \param eval a GameEvaluator representing the current state of the game
	 */
	private void coordinateWorkers(GameEvaluator eval) {
		Unit closestResource = units.myBase != null ? units.findClosestUnit(units.myBase.getX(), units.myBase.getY(), (Unit u) -> units.isResource(u)) : null;
		boolean isSafeToBuildBarracks 
						= units.myBase != null ? MapUtils.getDangerTime(units.myBase.getX(), units.myBase.getY(), 1, units) > 80 : false
						  || !eval.doesPathToEnemyExist;
		boolean wannaBuildBarracks = units.myBase != null && isSafeToBuildBarracks && eval.numBarracks == 0 && eval.numBuildingBarracks == 0;
		boolean canBuildBarracks = wannaBuildBarracks && eval.numAvailableResources > units.barracks.cost;
		int numCollectorsRequired = 1;
		
		// Decide number of workers to have
		if (wannaBuildBarracks && eval.numAvailableResources < units.barracks.cost + 5) {
			// We want to build barracks, so get more workers in there
			numCollectorsRequired = 2;
		}

		if (eval.numBarracks > 0 && eval.numAvailableResources < 5) {
			// We want enough resources to build more things
			numCollectorsRequired = 2;
		}
		
		// Sort the worker list from closest to base to furthest
		if (units.myBase != null && closestResource != null) {
			// Sort them in order of distance from our base, if possible (closest to furthest)
			HashMap<Unit, UnitThinker> tempThinkers = unitThinkers;
			units.myWorkers.sort((Unit a, Unit b) -> 
				  (MapUtils.distance(a, units.myBase) + MapUtils.distance(a, closestResource) - a.getResources() * 2)
				- (MapUtils.distance(b, units.myBase) + MapUtils.distance(b, closestResource) - b.getResources() * 2));
		} else {
			// Until a backup (rebuild the base!) strategy is created, don't send any collectors if we lose our base or resources
			numCollectorsRequired = 0;
		}
		
		// See if we have a builder
		for (Unit worker : units.myWorkers) {
			if (unitThinkers.get(worker).role.equals("build")) {
				if (canBuildBarracks) {
					eval.numBuildingBarracks++;					
				} else {
					unitThinkers.get(worker).role = "";
				}
			}
		}
		
		// When a ranged warrior enters the scene, let workers retreat to only the Brothers strategy, which only kills when safe
		
		// Command the rest of the workers
		for (Unit worker : units.myWorkers) {
			UnitThinker thinker = unitThinkers.get(worker);
			
			// Assign collectors
			if (numCollectorsRequired > 0 && !thinker.role.equals("build")) {
				thinker.strategy = () -> thinker.workerCollectStrategy();
				thinker.role = "collect";
				
				numCollectorsRequired--;
			}

			// Assign barracks builders
			else if ((canBuildBarracks && eval.numBuildingBarracks == 0) || thinker.role.equals("build")) {
				thinker.strategy = () -> thinker.workerBuildBarracksStrategy();
				thinker.role = "build";
				
				// Update evaluation
				eval.numAvailableResources -= units.barracks.cost;
				eval.numBuildingBarracks++;
			}
			
			// Assign ninjas
			else if (eval.doesPathToEnemyExist) {
				// Attack the closest enemy
				// Todo attack enemies close to the base?
				Unit closestEnemy = units.findClosestUnit(worker.getX(), worker.getY(), (Unit u) -> u.getPlayer() != playerId && u.getPlayer() != -1);
	
				if (closestEnemy != null) {
					//Unit enemyToTarget = units.enemyBase;
					Unit enemyToTarget = closestEnemy;
					thinker.strategy = () -> thinker.ninjaWarriorStrategy(enemyToTarget);
					thinker.role = "attack";
					//thinker.strategy = () -> thinker.driveByStrategy(enemyToTarget);
				}
			}

			// Assign empty action (vacate the base)
			else {
				thinker.strategy = () -> thinker.vacateBaseStrategy();
				thinker.role = "vacate";
			}
		}
	}
	
	/**
	 * \brief Coordinates attacking units
	 * \param eval a GameEvaluator representing the current game state
	 */
	private void coordinateAttackers(GameEvaluator eval) {
		// Sort units by reverse distance from the base
		units.myUnits.sort((Unit a, Unit b) -> MapUtils.distance(b, units.myBase) - MapUtils.distance(a, units.myBase));
		
		// Order basic attackers to attack
		for (Unit attacker : units.myUnits) {
			// Only order idle attackers to attack
			UnitThinker thinker = unitThinkers.get(attacker);
			if (!attacker.getType().canAttack || thinker.strategy != null) {
				continue;
			}

			if (units.isRanged(attacker)) {
				// I'm a ranged warrior, I'm here to eat butt and kick popcorn
				thinker.strategy = () -> thinker.rangedTempStrategy();
			}
		}

		// Testing: Try advanced strategies with frontmost workers
		int numBrothersRequired = (eval.numWorker + eval.numRanged) >= 5 ? 0 : 0;
		int numDriveBysRequired = (eval.numWorker + eval.numRanged) >= 5 ? 3 : 0;  
		
		// Brothers go first
		ArrayList<UnitThinker> brothers = new ArrayList<UnitThinker>();
		
		for (Unit attacker : units.myUnits) {
			UnitThinker thinker = unitThinkers.get(attacker);
			
			if (attacker.getType().canAttack && (!units.isWorker(attacker) || thinker.role.equals("attack"))) {
				if (numBrothersRequired > 0) {
					numBrothersRequired--;
					
					brothers.add(thinker);
				} else if (numDriveBysRequired > 0) {
					Unit closestEnemy = units.findClosestUnit(attacker.getX(), attacker.getY(), (Unit u) -> units.isEnemy(u));
					numDriveBysRequired--;
					
					thinker.strategy = () -> thinker.driveByStrategy(closestEnemy);
				}
			}
		}
		
		// Assign brothers
		for (int i = 0; i < brothers.size() - 1; i += 2) {
			UnitThinker broA = brothers.get(i), broB = brothers.get(i + 1);
			Unit closestEnemy = units.findClosestUnit(broA.getUnit().getX(), broA.getUnit().getY(), (Unit u) -> units.isEnemy(u));

			broA.strategy = () -> broA.brotherStrategy(broB.getUnit(), closestEnemy);
			broB.strategy = () -> broB.brotherStrategy(broA.getUnit(), closestEnemy);
		}
	}
	
	/**
	 * \brief Coordinates unit producers such as bases and barracks
	 * \param eval a GameEvaluator representing the current game state
	 */
	private void coordinateProducers(GameEvaluator eval) {
		for (Unit unit : units.myUnits) {
			UnitThinker thinker = unitThinkers.get(unit);

			if (units.isBase(unit)) {
				if (eval.numAvailableResources >= units.worker.cost && (eval.doesPathToEnemyExist || eval.numWorker < 5)) {
					if (eval.numWorker < 2 /* temp */) {
						// Produce a worker
						thinker.strategy = () -> thinker.produceCollectorStrategy();
					} else {
						// Produce a rusher
						// Put a worker in the position closest to an enemy unit
						thinker.strategy = () -> thinker.produceRusherStrategy(units.worker);
					}
				}
			}

			if (units.isBarracks(unit) && units.getAction(unit) == null) {
				if (eval.numAvailableResources >= units.ranged.cost) {
					thinker.strategy = () -> thinker.produceRusherStrategy(units.ranged);

					eval.numAvailableResources -= units.ranged.cost;
				}
			}
		}
	}
}