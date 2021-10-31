package display;


import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import network.Server;
import util.Colors;

public class ServerFrame extends JFrame {

	private static final int HEIGHT = 300;
	private static final int WIDTH = 500;
	private static final int GRID_LINES = 10;
	private JPanel panel;
	/**
	 * 
	 */
	private static final long serialVersionUID = -6377437611832432328L;
	private Server server;
	private ArrayList<JLabel> lines = new ArrayList<JLabel>();

	public ServerFrame(Server server) {
		this.server = server;
		panel = new JPanel();
		setTitle("Megumines server");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		panel.setBackground(Colors.BACKGROUND.color);
		panel.setPreferredSize(new Dimension(WIDTH, HEIGHT));
		panel.setBorder(new CompoundBorder(panel.getBorder(), new EmptyBorder(20,20,20,20)));
		panel.setLayout(new GridLayout(GRID_LINES, 1));
		add(panel);
	}
	
	public void refresh() {
		pack();
		setVisible(true);
	}
	
	public void newLine(String text) {
		if(lines.size() >= GRID_LINES) {
			lines.remove(lines.get(0));
			panel.remove(lines.get(0));
		}
		JLabel jl = new JLabel(text);
		jl.setForeground(Color.WHITE);
		lines.add(jl);
		panel.add(jl);
		refresh();
	}

}
