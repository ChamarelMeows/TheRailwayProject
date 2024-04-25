package net.therailwayproject.alex;

import java.io.IOException;

import javax.swing.SwingUtilities;

public class Main {

	public static void main(String[] args) throws IOException {
        new Main(args);
    }
	
	public Main(String...args) {
		Thread speedCalculatorThread = new Thread(() -> {
            new SpeedCalculator();
        });
        speedCalculatorThread.start();

        SwingUtilities.invokeLater(StartupWindow::new);
	}
}