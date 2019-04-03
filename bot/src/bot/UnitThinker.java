package bot;

import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Attack;
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

public class UnitThinker {
	public MyDisappointingRoboticSon parent = null;
	
	// Bot type info because we need this again I guess
    private UnitType worker;
    private UnitType base;
    private UnitType resource;
    private UnitType barracks;
    private UnitType ranged;
	
	// Called after strategies have been selected
	public void tick(int player, PhysicalGameState pgs) {
		
	}
}
