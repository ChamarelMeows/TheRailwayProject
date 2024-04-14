package net.therailwayproject.alex;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.Timer;

import org.jdesktop.swingx.JXMapKit;
import org.jdesktop.swingx.JXMapKit.DefaultProviders;
import org.jdesktop.swingx.JXMapViewer;
import org.jdesktop.swingx.mapviewer.GeoPosition;
import org.jdesktop.swingx.painter.Painter;

public class OSMMapViewer extends JFrame {

	private JTextField startField;
    private JTextField endField;
    private JButton searchButton;
    private JProgressBar[] loadingBars;
    private SpeedCalculator sp;
    private JXMapKit mapKit;
    private Painter<JXMapViewer> lineOverlay;
    
	public OSMMapViewer() {
		sp = SpeedCalculator.INSTANCE();
		setTitle("Map Application");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        startField = new JTextField(20);
        startField.setToolTipText("Enter start location");

        endField = new JTextField(20);
        endField.setToolTipText("Enter end location");

        searchButton = new JButton("Go");
        searchButton.addActionListener(e -> searchRoute());

        loadingBars = new JProgressBar[5];
        for (int i = 0; i < 5; i++) {
            loadingBars[i] = new JProgressBar();
            loadingBars[i].setValue(0);
            controlPanel.add(loadingBars[i]);
            controlPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        }

        controlPanel.add(startField);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        controlPanel.add(endField);
        controlPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        controlPanel.add(searchButton);

        add(controlPanel, BorderLayout.WEST);
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (mapKit != null && lineOverlay != null) {
                    mapKit.getMainMap().setOverlayPainter(null);
                }
            }
        });

        mapKit = new JXMapKit();
        mapKit.setDefaultProvider(DefaultProviders.OpenStreetMaps);
        mapKit.setCenterPosition(new GeoPosition(51.5074, -0.1278));
        mapKit.setZoom(5);
        mapKit.getMainMap().setOverlayPainter(null);
        add(mapKit, BorderLayout.CENTER);

        setSize(800, 600);
        setVisible(true);
        
        Timer timer = new Timer(1000, e -> updateProgressBars());
        timer.start();
    }
	
	public void updateProgressBars() {
        for (int i = 0; i < loadingBars.length; i++) {
            loadingBars[i].setValue((int)(sp.progressBars[i]*100));
        }
    }

    public void searchRoute() {
        if (sp.doneLoading) {
            String startLocation = startField.getText();
            String endLocation = endField.getText();
            RailwayTrack startTrack = sp.getTrackById(sp.getStationByName(startLocation).getTracks().get(0));
            RailwayTrack endTrack = sp.getTrackById(sp.getStationByName(endLocation).getTracks().get(0));
            sp.outputToMap(startTrack, endTrack);
            try {
                Desktop.getDesktop().browse(new File("res/index.html").toURI());
                
                mapKit.getMainMap().setOverlayPainter(null);
                
                lineOverlay = new Painter<JXMapViewer>() {
                    @Override
                    public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
                        g = (Graphics2D) g.create();
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        Rectangle rect = mapKit.getMainMap().getViewportBounds();
                        g.translate(-rect.x, -rect.y);

                        g.setColor(Color.RED);
                        g.setStroke(new BasicStroke(2));

                        int lastX = -1;
                        int lastY = -1;
                        for (GeoPosition gp : sp.routeCords) {
                            Point2D pt = mapKit.getMainMap().getTileFactory().geoToPixel(gp, mapKit.getMainMap().getZoom());
                            if (lastX != -1 && lastY != -1) {
                                g.drawLine(lastX, lastY, (int) pt.getX(), (int) pt.getY());
                            }
                            lastX = (int) pt.getX();
                            lastY = (int) pt.getY();
                        }

                        g.dispose();
                    }
                };

                mapKit.getMainMap().setOverlayPainter(lineOverlay);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            JOptionPane.showMessageDialog(this, "The SpeedCalculator isn't loaded yet or map is not initialized!");
        }
    }
}
