package net.therailwayproject.stella;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

public class StartupWindow extends JFrame {

	private static final long serialVersionUID = 5349661659780305469L;
	private JProgressBar loadingBar;
	private JLabel msgLabel;

	public StartupWindow() {
	    setTitle("Loading Data");
	    setSize(400, 200);
	    setLayout(new BorderLayout());

	    loadingBar = new JProgressBar();
	    loadingBar.setStringPainted(true);
	    loadingBar.setPreferredSize(new Dimension(loadingBar.getPreferredSize().width, 50));
	    add(loadingBar, BorderLayout.PAGE_START);

	    msgLabel = new JLabel();
	    msgLabel.setHorizontalAlignment(JLabel.CENTER);
	    add(msgLabel, BorderLayout.CENTER);

	    setLocationRelativeTo(null);
	    setVisible(true);
	    Timer timer = new Timer();
	    timer.schedule(new TimerTask() {
	        @Override
	        public void run() {
	            loadingBar.setValue((int) (SpeedCalculator.INSTANCE().progress * 100));
	            msgLabel.setText(SpeedCalculator.INSTANCE().progressMsg);
	            if (SpeedCalculator.INSTANCE().doneLoading) {
	                closeStartupWindow();
	                if (OSMMapViewer.INSTANCE() == null) {
	                    new OSMMapViewer();
	                }
	                timer.cancel();
	            }
	        }
	    }, 0, 1);
	}

    public void closeStartupWindow() {
        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            dispose();
        });
    }
}