package tests;
 /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


import ai.core.AI;
import ai.RandomBiasedAI;
import ai.abstraction.HeavyRush;
import ai.abstraction.LightRush;
import ai.abstraction.WorkerRush;
import ai.abstraction.RangedRush;
import ai.abstraction.pathfinding.BFSPathFinding;
import ai.mcts.naivemcts.NaiveMCTS;
import bot.*;
import gui.PhysicalGameStatePanel;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.io.OutputStreamWriter;
import javax.swing.JFrame;
import java.awt.event.WindowListener;
import rts.GameState;
import rts.PhysicalGameState;
import rts.PlayerAction;
import rts.units.UnitTypeTable;
import util.XMLWriter;
import utilities.DebugUtils;

/**
 *
 * @author santi
 */
public class GameVisualSimulationTest implements KeyListener, WindowListener {
	private boolean fastForward;
	private boolean pause;
	private boolean slowDown;
	
	private boolean gameover = false;
	
	public GameVisualSimulationTest() throws Exception {
        UnitTypeTable utt = new UnitTypeTable();
        //PhysicalGameState pgs = PhysicalGameState.load("../microrts/maps/8x8/bases8x8.xml", utt);
        //PhysicalGameState pgs = MapGenerator.basesWorkers8x8Obstacle();
        //PhysicalGameState pgs = PhysicalGameState.load("../microrts/maps/12x12/basesWorkers12x12.xml", utt);
        PhysicalGameState pgs = PhysicalGameState.load("../microrts/maps/24x24/basesWorkers24x24.xml", utt);

        GameState gs = new GameState(pgs, utt);
        int MAXCYCLES = 5000;
        int PERIOD = 20;
        
        //AI ai1 = new WorkerRush(utt, new BFSPathFinding());
        AI ai1 = new MyDisappointingRoboticSon(utt);
        AI ai2 = new WorkerRush(utt);
        //AI ai2 = new LightRush(utt);

        JFrame w = PhysicalGameStatePanel.newVisualizer(gs,640,640,false,PhysicalGameStatePanel.COLORSCHEME_BLACK);
//        JFrame w = PhysicalGameStatePanel.newVisualizer(gs,640,640,false,PhysicalGameStatePanel.COLORSCHEME_WHITE);
       
        // Make the world easier for myself, Louis, the almighty creator of the finest and highly tested robots.
        w.addWindowListener(this); // let me close the window without it still running!
        w.addKeyListener(this);    // let me fast-forward, slow down, and pause the game!
        PhysicalGameStatePanel.unitLabels = DebugUtils.getUnitLabels();
        
        long nextTimeToUpdate = System.currentTimeMillis() + PERIOD;
        boolean doFrameStep = false;
        do{
        	// Control simulation speed (fast-forward/slow)
            int speed = PERIOD;
            
            if (slowDown) {
            	speed *= 3;
            }
            
            if (fastForward) {
            	speed /= 3;
            } else {
            	doFrameStep = false;
            }

            // Toggle pause
        	if (pause) {
        		if (DebugUtils.isPaused()) {
        			DebugUtils.unpause();
        		} else {
        			DebugUtils.pause();
        		}
        		
        		pause = false;
        	}
            
            if (DebugUtils.isPaused()) {
            	speed = 0;
            	
            	// Advance one frame if right key is pressed
            	if (fastForward && !doFrameStep) {
            		doFrameStep = true;
            		speed = 1;
            	}
            }
            
            // Simulate!
            if (System.currentTimeMillis()>=nextTimeToUpdate) {
            	if (speed != 0) {
	                PlayerAction pa1 = ai1.getAction(0, gs);
	                PlayerAction pa2 = ai2.getAction(1, gs);
	                gs.issueSafe(pa1);
	                gs.issueSafe(pa2);
	
	                // simulate:
	                gameover = gs.cycle();
            	}
                
                w.repaint();
                nextTimeToUpdate=System.currentTimeMillis() + speed;
            } else {
                try {
                    Thread.sleep(1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }while(!gameover && gs.getTime()<MAXCYCLES);
        
        System.out.println("Game Over");
	}
	
    public static void main(String args[]) throws Exception {
    	new GameVisualSimulationTest();
    }

	@Override
	public void keyPressed(KeyEvent arg0) {
		// TODO Auto-generated method stub
		int keyCode = arg0.getKeyCode();
		
		if (keyCode == KeyEvent.VK_RIGHT) {
			fastForward = true;
		}
		if (keyCode == KeyEvent.VK_LEFT) {
			slowDown = true;
		}
		if (keyCode == KeyEvent.VK_DOWN) {
			pause = true;
		}
	}

	@Override
	public void keyReleased(KeyEvent arg0) {
		// TODO Auto-generated method stub
		int keyCode = arg0.getKeyCode();

		if (keyCode == KeyEvent.VK_RIGHT) {
			fastForward = false;
		}
		if (keyCode == KeyEvent.VK_LEFT) {
			slowDown = false;
		}
		if (keyCode == KeyEvent.VK_DOWN) {
			pause = false;
		}
		
	}

	@Override
	public void keyTyped(KeyEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowActivated(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowClosed(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowClosing(WindowEvent arg0) {
		gameover = true;
	}

	@Override
	public void windowDeactivated(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowDeiconified(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowIconified(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowOpened(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}    
}
