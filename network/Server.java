package network;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.Timer;

import util.Champ;
import util.Colors;
import util.Level;
import util.State;

/**
 * Classe Serveur, qui héberge la partie en ligne. Le serveur vérifie périodiquement si le jeu est bien initialisé.
 * Il contient un thread d'écoute des clients.
 * @author Gabriel Micoud
 *
 */

public class Server implements Runnable {
	ServerSocket ss;
	Thread mainThread;
	ArrayList<ServerClient> serverClients; 
	ArrayList<Integer> scores;
	public int port;
	public int maxPlayers = 5;
	boolean running;
	boolean playingNow = false; // se met en true après le premier clic, et se remet en false lors d'une victoire ou d'une défaite
	boolean standby = true; // se met à false dès qu'on joue, se remet à true quand on appuie sur reset
	int actualPlayers = 0;
	static Champ c;
	int seconds;
	int elapsedTime;
	Timer timer;
	State[][] states;
	Colors[][] colors;
	ScheduledExecutorService executorService;
	
	/**
	 * Fonction principale du serveur. Elle crée le serveur.
	 * @param strings
	 */
	
	public static void main(String...strings) {
		try {
			new Server(2000);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Constructeur du serveur. Il initialise les variables et configure la grille de départ.
	 * Un timer est également créé. Lorsqu'il est exécuté, il envoie une commande time au client, pour incrémenter son compteur.
	 * @param port
	 * @throws IOException
	 */
	
	public Server(int port) throws IOException {
		elapsedTime = 0;
		seconds = 0;
		c = new Champ(Level.HARD);
		states = new State[c.getDimX()][c.getDimY()];
		colors = new Colors[c.getDimX()][c.getDimY()];
		for (int x = 0; x < c.getDimX(); x++)
			for (int y = 0; y < c.getDimY(); y++) {
				states[x][y] = State.HIDDEN;
				colors[x][y] = Colors.DEFAULT;
			}
		timer = new Timer(1000, new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				elapsedTime += 1000;
				seconds = (elapsedTime/1000) % 1000;
				for (ServerClient client : serverClients)
					try {
						client.getDos().writeUTF("/timer " + String.format("%03d", seconds));
					} catch (IOException e1) {
						e1.printStackTrace();
					}
			}
			
		});
		this.port = port;
		ss = new ServerSocket(port);
		running = false;
		start();
	}
	
	/**
	 * Démarrage du serveur. Initialisation de la liste des interfaces vers les clients et de la liste des scores.
	 * Le serveur vérifie toutes les 10 secondes si la partie est bloquée, et la débloque ensuite.
	 */
	public void start() {
		System.out.println("Server was created. Listening to PORT " + port);
		serverClients = new ArrayList<ServerClient>();
		scores = new ArrayList<Integer>();
		running = true;
		mainThread = new Thread(this);
		mainThread.start();
		
		//Ask if there is still someone playing
		executorService = Executors.newSingleThreadScheduledExecutor();
		executorService.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				verifyGame();
			}
		}, 0, 10, TimeUnit.SECONDS);
	}
	
	/**
	 * Lorsque le client se déconnecte, le serveur le supprime de sa liste, et notifie les autres joueurs.
	 * @param serverClient
	 */
	public void reportDisconnection(ServerClient serverClient) {
		if(!serverClient.hasLost()) actualPlayers --;
		for (ServerClient client : serverClients) {
			int index = serverClients.indexOf(serverClient);
			String name = serverClient.getName();
			try {
				client.getDos().writeUTF("/removePlayer " + index + " " + name);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		scores.remove(serverClients.indexOf(serverClient));
		serverClients.remove(serverClient);
		System.out.println(serverClient.getName() + " exited the game, there are now " + serverClients.size() + " connected.");
	}
	
	/**
	 * Arrêt du serveur.
	 */
	
	public void stop() {
		running = false;
		mainThread.stop();
		
		for (ServerClient serverClient : serverClients) {
			serverClient.stop();
		}
	}
	
	/**
	 * Vérification du jeu:
	 * - Si tout le monde a perdu : tout le monde peut reset le jeu.
	 * - Si le jeu n'est pas reset, et si personne n'est connecté: reset automatique.
	 */
	
	public void verifyGame() {
		//System.out.println("Verifying game. Number of clients : " + serverClients.size() + " Number of people playing : " + actualPlayers);
		if(actualPlayers <= 0 && serverClients.size() > 0) {
			playingNow = false; //envoyer à tous les clients qu'ils peuvent appuyer sur le Reset + message affichant les scores définitifs
			timer.stop();
		}
		if(serverClients.size() <= 0 && standby == false) {
			setGame(); // quand il n'y a personne et que le jeu n'est pas réinitialisé, on le relance
			timer.stop();
		}
	}
	
	/**
	 * reset de la partie. La fonction ne marche que lorsque tout le monde a perdu, ou lorsque la grille a été nettoyée.
	 */
	
	public void resetGame() {
		if(playingNow == false && standby == false) {
			setGame();
		}
	}
	
	/**
	 * initialisation de la grille
	 */
	
	public void setGame() {
		System.out.println("Game restarted");
		standby = true;
		c = new Champ(Level.HARD);
		states = new State[c.getDimX()][c.getDimY()];
		elapsedTime = 0;
		seconds = 0;
		for(int i=0; i< scores.size(); i++) {
			scores.set(i,0); //reset les scores
		}
		for (int x = 0; x < c.getDimX(); x++)
			for (int y = 0; y < c.getDimY(); y++) {
				states[x][y] = State.HIDDEN;
				colors[x][y] = Colors.DEFAULT;
			}
		for (ServerClient client : serverClients)
			try {
				newPlayer(client);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		actualPlayers = serverClients.size();
		
	}
	
	/**
	 * Calcul du score: 
	 * - Lorsqu'un joueur clique sur une case libre, son score est mis à jour, et il est envoyé à tous les autres joueurs.
	 * - Lorsque toutes les cases libres sont découvertes, le jeu se termine par une victoire.
	 * @param serverClient
	 * @param xPos
	 * @param yPos
	 */
	
	public void score(ServerClient serverClient, int xPos, int yPos) {
		int revealedCases = 0;
		int hiddenCases = 0;
		int s;
		int index;
		s = scores.get(serverClients.indexOf(serverClient)) + c.detectMines(xPos, yPos) + 1;
		index = serverClients.indexOf(serverClient);
		System.out.println("Score and index : " + s + " " + index);
		scores.set(index, s); //on a un point même si on clique dans le vide, et plus sinon
		for(ServerClient client : serverClients) {
			try {
				client.getDos().writeUTF("/score " + s + " " + index + " " + serverClient.getName());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		for (int x=0; x<c.getDimX(); x++) {
			for (int y=0; y<c.getDimY(); y++) {
				if(states[x][y] == State.HIDDEN || states[x][y] == State.FLAGGED) hiddenCases ++;
			}
		}
		revealedCases = c.getDimX() * c.getDimY() - hiddenCases;
		if(revealedCases >= c.getDimX() * c.getDimY() - c.getNbMines()) {
			//victory
			timer.stop();
			System.out.println("Game ended.");
			for (ServerClient client : serverClients)
				try {
					client.getDos().writeUTF("/victory");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			playingNow = false;
		}
	}
	
	/**
	 * Cette fonction est le cœur du jeu en ligne. Elle prend en compte les coordonnées, puis la présence ou non d'une mine pour renvoyer un état à tous les joueurs.
	 * Au premier clic, s'il y a une mine, elle est déplacée. Puis le timer est lancé.
	 * Ensuite, si un joueur tombe sur une mine, la case n'est pas révélée pour tous les joueurs, mais la carte est révélée pour le joueur perdu.
	 * @param xPos
	 * @param yPos
	 * @param serverClient
	 * @throws IOException
	 */
	
	public void play(int xPos, int yPos, ServerClient serverClient) throws IOException {
		System.out.println(states[xPos][yPos]);
		if((states[xPos][yPos] != State.HIDDEN && states[xPos][yPos] != State.FLAGGED) || serverClient.hasLost()) return; //lorsqu'on joue (clic gauche), la case doit être cachée ou flaggée
		else {
			if(!playingNow) {
				//partie commencée, on refuse la connection d'autres gens
				if (c.isMine(xPos, yPos)) {
					c.changeMine(xPos, yPos);
					System.out.println("Mine moved.");
					play(xPos, yPos, serverClient);
				}
				timer.start();
				playingNow = true;
				standby = false;
			}
			//Une mine
			if (c.isMine(xPos, yPos)) {
				State[][] deadStates = new State[c.getDimX()][c.getDimY()];
				for (int x = 0; x < c.getDimX(); x++) {
					for (int y = 0; y < c.getDimY(); y++){
						if (c.isMine(x, y)) {
							deadStates[x][y] = State.BOMB;
						}
						else {
							int proximityMines = c.detectMines(x, y);
							deadStates[x][y] = State.values()[proximityMines]; // l'index est de zéro à huit
						}
						serverClient.getDos().writeUTF("/reveal " + x + " " + y + " " + deadStates[x][y].name() + " " + Colors.DEFAULT.name());
					}
				}
				deadStates[xPos][yPos] = State.EXPLODED;
				serverClient.getDos().writeUTF("/reveal " + xPos + " " + yPos + " " + deadStates[xPos][yPos].name() + " " + Colors.values()[serverClients.indexOf(serverClient)].name());
				serverClient.getDos().writeUTF("/gameover");
				if(!serverClient.hasLost()) {
					actualPlayers --;
					serverClient.justLost();
				}
				System.out.println("explosion");
				
			}
			//Pas de mine
			else {
				int proximityMines = c.detectMines(xPos, yPos);
				colors[xPos][yPos] = Colors.values()[serverClients.indexOf(serverClient)];
				states[xPos][yPos] = State.values()[proximityMines];
				for (ServerClient client : serverClients) client.getDos().writeUTF("/reveal " + xPos + " " + yPos + " " + states[xPos][yPos].name() + " " + colors[xPos][yPos].name()); //révéler la case pour tous les clients
				score(serverClient, xPos, yPos);
				//dans une partie en réseau, on ne rejoue pas autour.
			}
		}
		verifyGame();
	}
	
	/**
	 * broadcast du flag à tous les joueurs
	 * @param xPos
	 * @param yPos
	 * @param serverClient
	 * @throws IOException
	 */
	
	public void flag(int xPos, int yPos, ServerClient serverClient) throws IOException {
		if(states[xPos][yPos] == State.HIDDEN) {
			if(c.isMine(xPos, yPos)) {
				c.decrementMines();
				System.out.println("Mines remaining : " + c.getCurrentNbMines());
			}
			states[xPos][yPos] = State.FLAGGED;
			
		}
		else if (states[xPos][yPos] == State.FLAGGED) {
			if(c.isMine(xPos, yPos)) {
				c.incrementMines();
				System.out.println("Mines remaining : " + c.getCurrentNbMines());
			}
			states[xPos][yPos] = State.HIDDEN;
		}
		for (ServerClient client : serverClients) client.getDos().writeUTF("/reveal " + xPos + " " + yPos + " " + states [xPos][yPos] + " " + colors[xPos][yPos]);
	}
	
	/**
	 * Cette fonction est appelée lors de la connexion, et à chaque nouvelle partie.
	 * Elle envoie les paramètres appropriés au joueur, et le refuse si la partie est en cours, ou si la partie est pleine.
	 * @param serverClient
	 * @throws IOException
	 */
	
	public void newPlayer(ServerClient serverClient) throws IOException {
		if(serverClients.size() > maxPlayers) {
			serverClient.getDos().writeUTF("/maxplayers");
			scores.remove(serverClients.indexOf(serverClient));
			serverClients.remove(serverClient);
			System.out.println(serverClient.getName() + " could not enter the game because the party is already full.");
		}
		if(standby == false) {
			serverClient.getDos().writeUTF("/alreadystarted");
			scores.remove(serverClients.indexOf(serverClient));
			serverClients.remove(serverClient);
			System.out.println(serverClient.getName() + " could not enter the game because the game already started.");
		}
		else {
			//envoyer la grille actualisée au client
			serverClient.getDos().writeUTF("/success"); //connection succeeded, sending idle face
			serverClient.getDos().writeUTF("/setGrid " + c.getDimX() + " " + c.getDimY());
			for(ServerClient client : serverClients) {
				if(client != serverClient) {
					String name = client.getName();
					int index = serverClients.indexOf(client);
					serverClient.getDos().writeUTF("/setNames " + index + " " + name);
				}
				serverClient.getDos().writeUTF("/score " + scores.get(serverClients.indexOf(client)) + " " + serverClients.indexOf(client) + " .");
			}
			serverClient.unLost();
			for(int x=0; x < c.getDimX(); x++) {
				for(int y=0; y < c.getDimY(); y++) {
					serverClient.getDos().writeUTF("/reveal " + x + " " + y + " " + states[x][y].name() + " " + colors[x][y].name());
				}
			}
			serverClient.unLost();
			actualPlayers ++;
		}
	}
	
	/**
	 * broadcast du nom à tous les joueurs.
	 * @param serverClient
	 * @throws IOException
	 */
	public void sendNameToEveryone(ServerClient serverClient) throws IOException {
		for (ServerClient client : serverClients) client.getDos().writeUTF("/connected " + serverClients.indexOf(serverClient) + " " + serverClient.getName());
		System.out.println(actualPlayers + " people playing now.");
	}
	
	/**
	 * Obtention du champ de booléens.
	 * @return
	 */
	public Champ getChamp() {
		return c;
	}
	
	/**
	 * thread d'écoute des connexions entrantes.
	 */
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		try {
			ss.setReuseAddress(true);
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		while (running) {
			try {
				System.out.println("Waiting for new players ...");
				Socket s = ss.accept();
				serverClients.add(new ServerClient(this, s));
				int playerScore = 0;
				scores.add(playerScore);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
}
