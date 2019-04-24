package utilities;

import java.util.HashMap;

import rts.units.Unit;

public class DebugUtils {
	private static boolean isPaused = false;
	
	private static HashMap<Unit, String> unitLabels = new HashMap<Unit, String>();
	
	// Prints a debug message
	public static void print(String text) {
		System.out.print(text + "\n");
	}
	
	public static void setUnitLabel(Unit u, String label) {
		unitLabels.put(u, label);
	}
	
	// Returns the map of debug unit labels (displayed above the units)
	public static HashMap<Unit, String> getUnitLabels() {
		return unitLabels;
	}
	
	// Pauses the simulation, only works in custom GameVisualSimulationTest
	public static void pause() {
		isPaused = true;

		print("Paused");
	}
	
	// Pauses the simulation, displaying a custom pause message
	public static void pause(String pauseMessage) {
		isPaused = true;

		print(pauseMessage);
	}
	
	// Unpauses the simulation
	public static void unpause() {
		isPaused = false;
	}
	
	// Returns whether the simulation is paused by the bot
	public static boolean isPaused() {
		return isPaused;
	}
}
