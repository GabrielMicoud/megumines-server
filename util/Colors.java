package util;

import java.awt.Color;

//couleurs désaturées
public enum Colors {
	CUSTOM_1("#2471A3"), CUSTOM_2("#9C640C"), CUSTOM_3("#943126"), CUSTOM_4("#196F3D"), CUSTOM_5("#979A9A"), BACKGROUND("#424949"), DEFAULT();
	
	String defaultColor = "#616A6B";
	public Color color;
	
	Colors(String hex) {
		this.color = Color.decode(hex);
	}
	
	Colors(){
		this.color = Color.decode(defaultColor);
	}
}
