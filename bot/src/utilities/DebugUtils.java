package utilities;

import java.util.HashMap;

import rts.units.Unit;

/**
 * \brief A set of debugging utilities for testing purposes
 * \author Louis
 *
 */
public class DebugUtils {
	private static boolean isPaused = false; /**< Whether the game is paused. Only works with visualisation mods */
	
	private static HashMap<Unit, String> unitLabels = new HashMap<Unit, String>(); /**< Visual per-unit labels for debugging */
	
	/**
	 * \brief Prints a debug message in the console
	 * \param text message to print
	 */
	public static void print(String text) {
		System.out.print(text + "\n");
	}
	
	/**
	 * \brief Sets the label of a unit. Only works with visualisation mods
	 * \param u the unit to set the label
	 * \param label the label text to assign
	 */
	public static void setUnitLabel(Unit u, String label) {
		unitLabels.put(u, label);
	}

	/**
	 * \brief Returns the map of debug labels by unit
	 */
	public static HashMap<Unit, String> getUnitLabels() {
		return unitLabels;
	}
	
	/**
	 * \brief Pauses the simulation. Only works with visualisation mods
	 */
	public static void pause() {
		isPaused = true;

		print("Paused");
	}
	
	/**
	 * \brief Pauses the simulation, showing a custom message. Only works with visualisation mods
	 * \param pauseMessage A message to show while paused
	 */
	public static void pause(String pauseMessage) {
		isPaused = true;

		print(pauseMessage);
	}
	
	/**
	 * \brief Unpauses the simulation. Only works with visualisation mods
	 */
	public static void unpause() {
		isPaused = false;
	}
	
	/**
	 * \brief Returns whether the game is paused. Only works with visualisation mods
	 * \return whether the game is paused
	 */
	public static boolean isPaused() {
		return isPaused;
	}
}
