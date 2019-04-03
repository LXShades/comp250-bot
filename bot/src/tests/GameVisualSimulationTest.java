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
import ai.abstraction.pathfinding.BFSPathFinding;
import ai.mcts.naivemcts.NaiveMCTS;
import bot.*;
import gui.PhysicalGameStatePanel;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.OutputStreamWriter;
import javax.swing.JFrame;
import rts.GameState;
import rts.PhysicalGameState;
import rts.PlayerAction;
import rts.units.UnitTypeTable;
import util.XMLWriter;

/**
 *
 * @author santi
 */
public class GameVisualSimulationTest implements KeyListener {
	private boolean fastForward;
	private boolean pause;
	private boolean slowDown;
	
	public GameVisualSimulationTest() throws Exception {
        UnitTypeTable utt = new UnitTypeTable();
        PhysicalGameState pgs = PhysicalGameState.load("../microrts/maps/16x16/basesWorkers16x16.xml", utt);
//        PhysicalGameState pgs = MapGenerator.basesWorkers8x8Obstacle();

        GameState gs = new GameState(pgs, utt);
        int MAXCYCLES = 5000;
        int PERIOD = 20;
        boolean gameover = false;
        
        //AI ai1 = new WorkerRush(utt, new BFSPathFinding());
        AI ai1 = new MyDisappointingRoboticSon(utt);
        AI ai2 = new WorkerRush(utt);

        JFrame w = PhysicalGameStatePanel.newVisualizer(gs,640,640,false,PhysicalGameStatePanel.COLORSCHEME_BLACK);
//        JFrame w = PhysicalGameStatePanel.newVisualizer(gs,640,640,false,PhysicalGameStatePanel.COLORSCHEME_WHITE);

        w.addKeyListener(this);
        
        
        long nextTimeToUpdate = System.currentTimeMillis() + PERIOD;
        do{
            int speed = PERIOD;
            
            if (slowDown) {
            	speed *= 3;
            }
            if (fastForward) {
            	speed /= 3;
            }
            if (pause) {
            	speed = 0;
            }
            
            if (System.currentTimeMillis()>=nextTimeToUpdate) {
            	if (!pause) {
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
}
