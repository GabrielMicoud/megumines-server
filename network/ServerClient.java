package network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerClient implements Runnable{
	public Server server;
	public Socket socket;
	public Thread thread;
	public boolean gameStarted;
	DataInputStream dis = null;
	DataOutputStream dos = null;
	boolean connected;
	boolean[][] flags;
	int score;
	boolean lostTheGame = true;
	boolean named = false;
	String name;
	ScheduledExecutorService executorService;
	
	public ServerClient(Server server, Socket socket) {
		this.server = server;
		this.socket = socket;
		connected = true;
		try {
			dis = new DataInputStream(socket.getInputStream());
			dos = new DataOutputStream(socket.getOutputStream());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		thread = new Thread(this);
		thread.start();
		
	}
	
	public void writeUTF(String s) throws IOException {
		dos.writeUTF(s);
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		if (dis == null || dos == null) {
			System.out.println("error uninitialized input/output stream");
			return;
		}
		try {
			server.newPlayer(this);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		//Ask name
		executorService = Executors.newSingleThreadScheduledExecutor();
		executorService.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				if(!named) {
					try {
						dos.writeUTF("/whatsyourname");
						System.out.println("Asking name");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}, 0, 1, TimeUnit.SECONDS);
		
		//Listening to client
		while(connected) {
			try {
				String data = dis.readUTF();
				String[] splittedCommand = data.split(" ");
				String commande = splittedCommand[0];
				if(commande.equals("/name")) {
					if(!named) {
						StringBuffer sb = new StringBuffer();
						for(int i=1; i<splittedCommand.length; i++) {
							sb.append(splittedCommand[i] + " ");
						}
						name = sb.toString();
						named = true;
						server.sendNameToEveryone(this);
					}
				}
				if(commande.equals("/reset")) {
					server.resetGame();
				}
				else if(commande.equals("/play")) {
					int xPos = Integer.parseInt(splittedCommand[1]);
					int yPos = Integer.parseInt(splittedCommand[2]);
					server.play(xPos, yPos, this);
					System.out.println(name + " played at  x:" + xPos + " y: " + yPos);
				}
				else if(commande.equals("/flag")) {
					int xPos = Integer.parseInt(splittedCommand[1]);
					int yPos = Integer.parseInt(splittedCommand[2]);
					server.flag(xPos, yPos, this);
					System.out.println(name + " flagged  x:" + xPos + " y: " + yPos);
				}
				else if(commande.equals("/disconnect")) {
					server.reportDisconnection(this);
					connected = false;
				}
				
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				try {
					server.reportDisconnection(this);
					socket.close();
					connected = false;
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}
		
	}
	public String getName() {
		return name;
	}
	public boolean hasLost() {
		return lostTheGame;
	}
	
	public void setScore(int s) {
		score = s;
	}
	
	public int getScore() {
		return score;
	}
	
	public void justLost() {
		lostTheGame = true;
	}
	
	public void unLost() {
		lostTheGame = false;
	}
	
	public DataOutputStream getDos() {
		return dos;
	}
	
	public void stop() {
		
	}
	
}
