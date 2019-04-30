package bot;

import ai.GameEvaluator;
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

public class MyDisappointingRoboticSon extends AbstractionLayerAI {
	private UnitTypeTable utt; /**< Unit type table stored for clones */

	// Tick state variables
	private int playerId = 0; /**< ID of the current player */
	private Player player = null; /**< The current player */
	private GameState gs; /**< The current game state */
	private PhysicalGameState pgs; /**< The current physical game state */
	
	private UnitUtils units; /**< Unit utilities */
	private HashMap<Unit, UnitThinker> unitThinkers = new HashMap<Unit, UnitThinker>(); /** UnitThinkers associated with each unit */
	
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
		Unit ourBase = units.findClosestUnit(0, 0, (Unit u) -> units.isBase(u));
		GameEvaluator eval = new GameEvaluator(playerId, gs, units);
		boolean doBuildBarracks = ourBase != null && eval.numRanged < 1 && MapUtils.getDangerTime(ourBase.getX(), ourBase.getY(), 1, units) > 80 && eval.numResources - eval.numUnavailableResources > units.barracks.cost;
		int numWorkersCollectingStuff = 0;
		int numUsableResources = gs.getPlayer(player).getResources();
		int numBrothers = 0;
		Unit brothers[] = new Unit[2];
		
		List<Unit> targetedEnemies = new LinkedList<Unit>();

		doBuildBarracks = false; // temp: disable barracks build
		
		for (Unit unit : pgs.getUnits()) {
			if (unit.getPlayer() == player) {
				UnitThinker thinker = unitThinkers.get(unit);
				
				if (units.isWorker(unit)) {
					if (numWorkersCollectingStuff < 1 && eval.numBase > 0) {
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
						Unit closestEnemy = units.findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> u.getPlayer() != player && u.getPlayer() != -1);

						if (closestEnemy != null && units.getAction(unit) == null) {
							thinker.strategy = () -> thinker.ninjaWarriorStrategy();
							
							targetedEnemies.add(closestEnemy);
						}
					}
				}
				
				if (units.isBase(unit)) {
					if (units.getAction(unit) == null) {
						if (numUsableResources >= units.worker.cost && (numUsableResources > units.barracks.cost + 1 || !doBuildBarracks)) {
							// Produce a worker
							if (eval.numWorker == 0) {
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

				if (units.isBarracks(unit) && units.getAction(unit) == null) {
					if (gs.getPlayer(player).getResources() >= units.ranged.cost) {
						train(unit, units.ranged);
					}
				}

				if (units.isRanged(unit)) {
					Unit closestEnemy = units.findClosestUnit(unit.getX(), unit.getY(), (Unit u) -> u.getPlayer() != player && u.getPlayer() != -1);
					attack(unit, closestEnemy);
				}
			}
		}
		
		// BROTHERS STRATEGY
		if (numBrothers == 2) {
			Unit closestEnemy = units.findClosestUnit(brothers[0].getX(), brothers[0].getY(), (Unit u) -> units.isEnemy(u));
			UnitThinker brotherA = unitThinkers.get(brothers[0]);
			UnitThinker brotherB = unitThinkers.get(brothers[1]);
			
			brotherA.strategy = () -> brotherA.brotherStrategy(brotherB.getUnit(), closestEnemy);
			brotherB.strategy = () -> brotherB.brotherStrategy(brotherA.getUnit(), closestEnemy);
		}
		
		// Tick the thinkers
		for (Unit key : unitThinkers.keySet()) {
			unitThinkers.get(key).tick(gs);
			
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
					newUnitThinkers.put(u, new UnitThinker(u, units));
				}
			}
		}
		
		// Reassign the unit thinker map with the new map
		unitThinkers = newUnitThinkers;
	}
}