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
 * Classe Serveur, qui h�berge la partie en ligne. Le serveur v�rifie p�riodiquement si le jeu est bien initialis�.
 * Il contient un thread d'�coute des clients.
 * @author Gabriel Micoud
 *
 */

public class Server implements Runnable {
	ServerSocket ss;
	Thread mainThread;
	ArrayList<ServerClient> serverClients;
	public static final int DEFAULT_PORT = 2000;
	int port;
	public int maxPlayers = 5;
	boolean running;
	boolean playingNow = false; // se met en true apr�s le premier clic, et se remet en false lors d'une victoire ou d'une d�faite
	boolean standby = true; // se met � false d�s qu'on joue, se remet � true quand on appuie sur reset
	int actualPlayers = 0;
	static Champ c;
	int seconds;
	int elapsedTime;
	Timer timer;
	State[][] states;
	Colors[][] colors;
	ScheduledExecutorService executorService;
	
	/**
	 * Fonction principale du serveur. Elle cr�e le serveur.
	 * @param strings
	 */
	
	public static void main(String...strings) {
		try {
			new Server(DEFAULT_PORT);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Constructeur du serveur. Il initialise les variables et configure la grille de d�part.
	 * Un timer est �galement cr��. Lorsqu'il est ex�cut�, il envoie une commande time au client, pour incr�menter son compteur.
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
	 * D�marrage du serveur. Initialisation de la liste des interfaces vers les clients et de la liste des scores.
	 * Le serveur v�rifie toutes les 10 secondes si la partie est bloqu�e, et la d�bloque ensuite.
	 */
	public void start() {
		System.out.println("Server was created. Listening to PORT " + port);
		serverClients = new ArrayList<ServerClient>();
		//scores = new ArrayList<Integer>();
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
	 * Lorsque le client se d�connecte, le serveur le supprime de sa liste, et notifie les autres joueurs.
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
		//scores.remove(serverClients.indexOf(serverClient));
		serverClients.remove(serverClient);
		System.out.println(serverClient.getName() + " exited the game, there are now " + serverClients.size() + " connected.");
	}
	
	/**
	 * Arr�t du serveur.
	 */
	
	public void stop() {
		running = false;
		mainThread.stop();
		
		for (ServerClient serverClient : serverClients) {
			serverClient.stop();
		}
	}
	
	/**
	 * V�rification du jeu:
	 * - Si tout le monde a perdu : tout le monde peut reset le jeu.
	 * - Si le jeu n'est pas reset, et si personne n'est connect�: reset automatique.
	 */
	
	public void verifyGame() {
		//System.out.println("Verifying game. Number of clients : " + serverClients.size() + " Number of people playing : " + actualPlayers);
		if(actualPlayers <= 0 && serverClients.size() > 0) {
			timer.stop();
			playingNow = false; //envoyer � tous les clients qu'ils peuvent appuyer sur le Reset + message affichant les scores d�finitifs
		}
		if(serverClients.size() <= 0 && standby == false) {
			timer.stop();
			setGame(); // quand il n'y a personne et que le jeu n'est pas r�initialis�, on le relance
		}
	}
	
	/**
	 * reset de la partie. La fonction ne marche que lorsque tout le monde a perdu, ou lorsque la grille a �t� nettoy�e.
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
		c = new Champ(Level.HARD);
		states = new State[c.getDimX()][c.getDimY()];
		elapsedTime = 0;
		seconds = 0;
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
		standby = true;
	}
	
	/**
	 * Calcul du score: 
	 * - Lorsqu'un joueur clique sur une case libre, son score est mis � jour, et il est envoy� � tous les autres joueurs.
	 * - Lorsque toutes les cases libres sont d�couvertes, le jeu se termine par une victoire.
	 * @param serverClient
	 * @param xPos
	 * @param yPos
	 */
	public void score(ServerClient serverClient, int xPos, int yPos) {
		int revealedCases = 0;
		int hiddenCases = 0;
		int s;
		int index;
		s = serverClient.getScore() + c.detectMines(xPos, yPos) + 1;
		index = serverClients.indexOf(serverClient);
		System.out.println("Score and index : " + s + " " + index);
		serverClient.setScore(s);
		//scores.set(index, s); //on a un point m�me si on clique dans le vide, et plus sinon
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
	 * Cette fonction est le c�ur du jeu en ligne. Elle prend en compte les coordonn�es, puis la pr�sence ou non d'une mine pour renvoyer un �tat � tous les joueurs.
	 * Au premier clic, s'il y a une mine, elle est d�plac�e. Puis le timer est lanc�.
	 * Ensuite, si un joueur tombe sur une mine, la case n'est pas r�v�l�e pour tous les joueurs, mais la carte est r�v�l�e pour le joueur perdu.
	 * @param xPos
	 * @param yPos
	 * @param serverClient
	 * @throws IOException
	 */
	public void play(int xPos, int yPos, ServerClient serverClient) throws IOException {
		System.out.println(states[xPos][yPos]);
		if((states[xPos][yPos] != State.HIDDEN && states[xPos][yPos] != State.FLAGGED) || serverClient.hasLost()) return; //lorsqu'on joue (clic gauche), la case doit �tre cach�e ou flagg�e
		else {
			if(!playingNow) {
				//partie commenc�e, on refuse la connection d'autres gens
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
							deadStates[x][y] = State.values()[proximityMines]; // l'index est de z�ro � huit
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
				for (ServerClient client : serverClients) client.getDos().writeUTF("/reveal " + xPos + " " + yPos + " " + states[xPos][yPos].name() + " " + colors[xPos][yPos].name()); //r�v�ler la case pour tous les clients
				score(serverClient, xPos, yPos);
				//dans une partie en r�seau, on ne rejoue pas autour.
			}
		}
		verifyGame();
	}
	
	/**
	 * broadcast du flag � tous les joueurs
	 * @param xPos
	 * @param yPos
	 * @param serverClient
	 * @throws IOException
	 */
	public void flag(int xPos, int yPos, ServerClient serverClient) throws IOException {
			serverClient.setFlag(xPos, yPos, !serverClient.getFlag(xPos, yPos));
			if(serverClient.getFlag(xPos, yPos)){
				serverClient.getDos().writeUTF("/flag " + xPos + " " + yPos + " " + serverClient.getNbFlags());
			} else {
				serverClient.getDos().writeUTF("/unflag " + xPos + " " + yPos + " " + serverClient.getNbFlags());
			}
	}
	
	/**
	 * Cette fonction est appel�e lors de la connexion, et � chaque nouvelle partie.
	 * Elle envoie les param�tres appropri�s au joueur, et le refuse si la partie est en cours, ou si la partie est pleine.
	 * @param serverClient
	 * @throws IOException
	 */
	public void newPlayer(ServerClient serverClient) throws IOException {
		if(serverClients.size() > maxPlayers) {
			serverClient.getDos().writeUTF("/maxplayers");
			serverClients.remove(serverClient);
			System.out.println(serverClient.getName() + " could not enter the game because the party is already full.");
		}
		if(standby == false) {
			serverClient.getDos().writeUTF("/alreadystarted");
			serverClients.remove(serverClient);
			System.out.println(serverClient.getName() + " could not enter the game because the game already started.");
		}
		else {
			//envoyer la grille actualis�e au client
			serverClient.getDos().writeUTF("/success"); //connection succeeded, sending idle face
			serverClient.getDos().writeUTF("/setGrid " + c.getDimX() + " " + c.getDimY());
			serverClient.setScore(0);
			serverClient.unLost();
			for(ServerClient client : serverClients) {
				if(client != serverClient) {
					String name = client.getName();
					int index = serverClients.indexOf(client);
					serverClient.getDos().writeUTF("/setNames " + index + " " + name);
				}
				serverClient.getDos().writeUTF("/score " + client.getScore() + " " + serverClients.indexOf(client) + " .");
			}
			for(int x=0; x < c.getDimX(); x++) {
				for(int y=0; y < c.getDimY(); y++) {
					serverClient.getDos().writeUTF("/reveal " + x + " " + y + " " + states[x][y].name() + " " + colors[x][y].name());
				}
			}
			actualPlayers ++;
		}
	}
	
	/**
	 * broadcast du nom � tous les joueurs.
	 * @param serverClient
	 * @throws IOException
	 */
	public void sendNameToEveryone(ServerClient serverClient) throws IOException {
		for (ServerClient client : serverClients) client.getDos().writeUTF("/connected " + serverClients.indexOf(serverClient) + " " + serverClient.getName());
		System.out.println(actualPlayers + " people playing now.");
	}
	
	/**
	 * Obtention du champ de bool�ens.
	 * @return
	 */
	public Champ getChamp() {
		return c;
	}
	
	/**
	 * thread d'�coute des connexions entrantes.
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
				//scores.add(playerScore);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
}
