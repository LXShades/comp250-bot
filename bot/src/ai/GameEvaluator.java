package ai;

import rts.GameState;
import rts.units.Unit;
import rts.units.UnitType;
import rts.UnitAction;
import utilities.UnitUtils;

/**
 * Builds a broad evaluation of the game state including
 *  - Number of units per player
 * \author Louis
 *
 */
public class GameEvaluator {
	private int playerId;
	
	// Unit counts for this player
	public int numBarracks = 0; /**< Number of barracks */
	public int numBuildingBarracks = 0; /**< Number of barracks being built */
	
	public int numBase = 0; /**< Number of bases */
	public int numBuildingBase = 0; /**< Number of bases being built */
	
	public int numWorker = 0; /**< Number of workers */
	public int numBuildingWorker = 0; /**< Number of workers being built */
	
	public int numLight = 0;/**< Number of light units */
	public int numBuildingLight; /**< Number of light units being built */
	
	public int numHeavy = 0; /**< Number of heavy units */
	public int numBuildingHeavy = 0; /**< Number of heavy units being built */
	
	public int numRanged = 0; /**< Number of ranged units */
	public int numBuildingRanged = 0; /**< Number of ranged units being built */
	
	public int numUnits = 0; /**< Number of units owned by this player total */
	public int numBuildingUnits = 0; /**< Number of units being built */
	
	public int numAvailableResources = 0; /**< The number of resources available to the player */
	public int numTotalResources = 0; /**< The number of resources total */
	
	/**
	 * Instantiates a GameEvaluator with a basic evaluation of the player's state
	 * \param playerId the player to evaluate
	 * \param gs the gamestate to evaluate
	 * \param units a set of unit utilities
	 */
	public GameEvaluator(int playerId, GameState gs, UnitUtils units) {
		this.playerId = playerId;
		
		this.numTotalResources = this.numAvailableResources = gs.getPlayer(playerId).getResources();
		
		// Count units
		for (Unit u : gs.getUnits()) {
			if (u.getPlayer() != playerId) {
				continue;
			}
			
			// Check types and add to counters 
			UnitType type = u.getType();
			UnitAction action = units.getAction(u);
			int actionType = action != null ? action.getType() : UnitAction.TYPE_NONE;
			
			if (units.isBase(u)) {
				numBase++;
				
				// Check if it is building a worker
				if (actionType == UnitAction.TYPE_PRODUCE) {
					numBuildingWorker++;
					
					// Subtract from available resources
					numAvailableResources -= units.worker.cost;
				}
			}
			else if (units.isBarracks(u)) {
				numBarracks++;
				
				if (actionType == UnitAction.TYPE_PRODUCE) {
					// Add units being currently built
					if (action.getUnitType() == units.light) {
						numBuildingLight++;
					} else if (action.getUnitType() == units.heavy) {
						numBuildingHeavy++;
					} else if (action.getUnitType() == units.ranged) {
						numBuildingRanged++;
					}
					
					// Subtract from available resources
					numAvailableResources -= action.getUnitType().cost;
				}
			}
			else if (units.isWorker(u)) {
				numWorker++;
			}
			else if (units.isLight(u)) {
				numLight++;
			}
			else if (units.isHeavy(u)) {
				numHeavy++;
			}
			else if (units.isRanged(u)) {
				numRanged++;
			}
		}
	}
}
