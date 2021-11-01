package util;
import java.util.*;
/**
 * Champ.java Minesweeper
 * @author Mi-kun
 *
 */

//SI LA PARTIE EST EN LIGNE, LIER LE CHAMP AU SERVEUR, SINON AU CLIENT
public class Champ {
	//variables
	private final static int [] LEVELSIZEX = {10, 17, 30};
	private final static int [] LEVELSIZEY = {10, 17, 17};
	private final static int [] NBMINES = {15, 45, 90};
	private boolean[][] champMines;
	private Random alea = new Random();
	private int numberOfMines = 0;
	private int numberOfMinesInGame = 0;
	
	/**
	 * constructor
	 */
	public Champ() {
		this(Level.EASY);
	}
	public Champ(Level level) {
		if(level != Level.CUSTOM) {
			champMines = new boolean[LEVELSIZEX[level.ordinal()]][LEVELSIZEY[level.ordinal()]];
			numberOfMines = NBMINES[level.ordinal()];
			numberOfMinesInGame = numberOfMines;
			placeMines();
		}
		
	}
	public Champ(Level level, int dimension, int nbMines) {
		this(level);
		if(level == Level.CUSTOM) {
			champMines = new boolean[dimension][dimension];
			if(nbMines <= dimension*dimension) {
				numberOfMines = nbMines;
				placeMines();
			}
			else {
				numberOfMines = NBMINES[Level.EASY.ordinal()];
				placeMines();
			}
			numberOfMinesInGame = numberOfMines;
		}
	}
	
	/**
	 * Display field
	 */
	public void affText() {
		String row;
		for (int i=0; i<champMines.length;i++) {
			row = "";
			for (int j=0; j<champMines[0].length; j++) {
				row += champMines[i][j]?"x ": this.detectMines(i,j) + " "; //ternaire, si mine, alors x, sinon le nb de mines autour
			}
			System.out.println(row);
		}
	}
	
	public int detectMines(int x, int y) {
		int nbMines = 0;
		for (int i = Math.max(x-1, 0); i <= Math.min(x+1, this.champMines.length-1); i++) {
			for(int j = Math.max(y-1, 0); j<= Math.min(y+1, this.champMines[0].length-1); j++) {
				if(this.champMines[i][j]) nbMines += 1;
			}
		}
		return nbMines;
	}
	
	/**
	 * set mines
	 * @param nbMines d�finit le nombre de mines � placer
	 */
	public void placeMines() {
		int nbMines = numberOfMines;
		for(int i = 0; i < champMines.length; i++) {
			for(int j = 0; j < champMines[0].length; j++) {
				champMines[i][j] = false;
			}
		}
		for(int k=nbMines; k!=0;) { //on voit si une mine est d�j� plac�e
			int x = alea.nextInt(champMines.length);
			int y = alea.nextInt(champMines[0].length);
			if(!champMines[x][y]) {
				champMines[x][y] = true;
				k --;
			}
		}
	}
	
	public void changeMine(int x, int y) {
		champMines[x][y] = false; //on enl�ve la mine du premier clic
		for(int i = 0; i < champMines.length; i++) {
			for(int j = 0; j < champMines[0].length; j++) {
				if(champMines[i][j] == false) {
					champMines[i][j] = true;
					return;
				}
			}
		}
	}
	
	public boolean[][] getChamp() {
		return this.champMines;
	}
	
	public int getDimX() {
		return this.champMines.length;
	}
	
	public int getDimY() {
		return this.champMines[0].length;
	}
	
	public boolean isMine(int x, int y) {
		return champMines[x][y];
	}
	
	public int getNbMines() {
		return numberOfMines;
	}
	
	public int getCurrentNbMines() {
		return numberOfMinesInGame;
	}
	public void decrementMines() {
		numberOfMinesInGame --;
	}
	
	public void incrementMines() {
		numberOfMinesInGame ++;
	}
}
