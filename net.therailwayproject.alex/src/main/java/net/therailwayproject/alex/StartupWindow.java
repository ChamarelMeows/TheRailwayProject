package net.therailwayproject.alex;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

public class StartupWindow extends JFrame {

	private JProgressBar[] loadingBars;

    public StartupWindow() {
        setTitle("Loading Data");
        setLayout(new BorderLayout());

        loadingBars = new JProgressBar[4];

        JPanel progressBarPanel = new JPanel(new GridLayout(5, 1));
        progressBarPanel.add(new JLabel(SpeedCalculator.INSTANCE().loadingFromFile ? "Loading track data" : "Downloading track data"));
        for (int i = 0; i < loadingBars.length; i++) {
            loadingBars[i] = new JProgressBar();
            loadingBars[i].setStringPainted(true);
            progressBarPanel.add(loadingBars[i]);
        }
        add(progressBarPanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateProgressBars();
                if (SpeedCalculator.INSTANCE().doneLoading) {
                    closeStartupWindow();
                    new OSMMapViewer();
                    timer.cancel();
                }
            }
        }, 0, 1000);
    }

    public void updateProgressBars() {
        for (int i = 0; i < loadingBars.length; i++) {
            loadingBars[i].setValue((int) (SpeedCalculator.INSTANCE().progressBars[i] * 100));
        }
    }

    public void closeStartupWindow() {
        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            dispose();
        });
    }
}