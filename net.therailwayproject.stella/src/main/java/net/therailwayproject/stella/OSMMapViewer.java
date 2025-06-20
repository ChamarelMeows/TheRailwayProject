package net.therailwayproject.stella;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.jdesktop.swingx.JXMapKit;
import org.jdesktop.swingx.JXMapKit.DefaultProviders;
import org.jdesktop.swingx.JXMapViewer;
import org.jdesktop.swingx.mapviewer.GeoPosition;
import org.jdesktop.swingx.painter.Painter;

public class OSMMapViewer extends JFrame {

	private static final long serialVersionUID = 8618635083169161612L;
	private static OSMMapViewer osmMapViewer;
	private JButton searchButton;
	private JButton addStopButton;
	private JButton removeStopButton;
	private SpeedCalculator sp;
	private JXMapKit mapKit;
	private Painter<JXMapViewer> lineOverlay;
	private List<JTextField> stopFields;
	private List<String> suggestions;
	private JPanel controlPanel, stopsPanel;
	private List<VisiblePainter> overlays;
	private JScrollPane scrollPane;
	private StartupWindow startupWindow;
	private boolean doneSearching = true;
	public JMenuBar menuBar;
	public List<List<GeoPosition>> tracks;
	
	public static OSMMapViewer INSTANCE() {
		return osmMapViewer;
	}
	
	public OSMMapViewer() {
		osmMapViewer = this;
		sp = SpeedCalculator.INSTANCE();
        overlays = new ArrayList<>();
        tracks = new ArrayList<>(sp.tracks.size());
        setTitle("The Railway Project");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        createLocalTracks();
        
        controlPanel = new JPanel();
        controlPanel.setLayout(new BorderLayout());
        controlPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        stopsPanel = new JPanel();
        stopsPanel.setLayout(new BoxLayout(stopsPanel, BoxLayout.Y_AXIS));

        stopFields = new ArrayList<>();
        JTextField startField = createStopField("Enter start location");
        JTextField endField = createStopField("Enter end location");
        stopFields.add(startField);
        stopFields.add(endField);

        stopsPanel.add(startField);
        stopsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        stopsPanel.add(endField);
        stopsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        searchButton = new JButton("Go");
        searchButton.addActionListener(e -> {new Thread(() -> searchRoute()).start();});
        addStopButton = new JButton("Add Stop");
        addStopButton.addActionListener(e -> addStopField());
        removeStopButton = new JButton("Remove Stop");
        removeStopButton.addActionListener(e -> removeStopField());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(searchButton);
        buttonPanel.add(addStopButton);
        buttonPanel.add(removeStopButton);

        scrollPane = new JScrollPane(stopsPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                JViewport viewport = scrollPane.getViewport();
                Point pos = viewport.getViewPosition();
                int delta = e.getWheelRotation() * 16;
                pos.y += delta;
                if (pos.y < 0) {
                    pos.y = 0;
                } else if (pos.y > viewport.getView().getHeight() - viewport.getHeight()) {
                    pos.y = viewport.getView().getHeight() - viewport.getHeight();
                }
                viewport.setViewPosition(pos);
            }
        });
        scrollPane.setBorder(null);

        controlPanel.add(scrollPane, BorderLayout.CENTER);
        controlPanel.add(buttonPanel, BorderLayout.SOUTH);

        createMenuButtons();
        
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

		suggestions = new ArrayList<>();
		suggestions.addAll(sp.stations.stream().map(Station::getName).distinct().collect(Collectors.toList()));

		for (JTextField textField : stopFields) {
	        AutocompleteDocumentListener stopListener = new AutocompleteDocumentListener(textField);
	        stopListener.setSuggestions(suggestions);
	        textField.getDocument().addDocumentListener(stopListener);
	    }
		
		VisiblePainter routeOverlay = new LineOverlay(() -> new PaintingLogic() {
		    @Override
		    public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
		    	if(!doneSearching)
					return;
		        g = (Graphics2D) g.create();
		        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		        Rectangle rect = mapKit.getMainMap().getViewportBounds();
		        g.translate(-rect.x, -rect.y);

		        g.setColor(Color.GREEN);
		        g.setStroke(new BasicStroke(2));

		        int lastX = -1;
		        int lastY = -1;

		        Rectangle visibleRect = map.getViewportBounds();
		        double marginDegrees = 0.01;
			    double marginPixels = marginDegrees * map.getTileFactory().getInfo().getLongitudeDegreeWidthInPixels(map.getZoom());

			    visibleRect.setBounds((int) (visibleRect.x - marginPixels), (int) (visibleRect.y - marginPixels),
			                          (int) (visibleRect.width + 2 * marginPixels), (int) (visibleRect.height + 2 * marginPixels));
		        for (GeoPosition gp : sp.routeCords) {
		            Point2D pt = mapKit.getMainMap().getTileFactory().geoToPixel(gp, mapKit.getMainMap().getZoom());
		            if (visibleRect.contains(pt)) {
		                if (lastX != -1 && lastY != -1) {
		                    g.drawLine(lastX, lastY, (int) pt.getX(), (int) pt.getY());
		                }
		                lastX = (int) pt.getX();
		                lastY = (int) pt.getY();
		            }
		        }

		        g.dispose();
		    }
		});
		VisiblePainter stationsOverlay = new LineOverlay(() -> new PaintingLogic() {
		    @Override
		    public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
		        g = (Graphics2D) g.create();
		        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		        Rectangle rect = mapKit.getMainMap().getViewportBounds();
		        g.translate(-rect.x, -rect.y);

		        g.setColor(Color.RED);

		        Rectangle visibleRect = map.getViewportBounds();

		        List<Rectangle> drawnCircles = new ArrayList<>();
		        int circleRadius = 5;
		        int maxStationsToRender = 500;
		        int renderedCount = 0;

		        for (Station s : sp.stations) {
		            if (renderedCount >= maxStationsToRender) {
		                break;
		            }

		            GeoPosition gp = new GeoPosition(s.getLat(), s.getLon());
		            Point2D pt = mapKit.getMainMap().getTileFactory().geoToPixel(gp, mapKit.getMainMap().getZoom());

		            if (visibleRect.contains(pt)) {
		                int circleX = (int) pt.getX() - circleRadius;
		                int circleY = (int) pt.getY() - circleRadius;
		                int diameter = circleRadius * 2;

		                Rectangle circleBounds = new Rectangle(circleX, circleY, diameter, diameter);

		                boolean overlaps = false;
		                for (Rectangle drawn : drawnCircles) {
		                    if (drawn.intersects(circleBounds)) {
		                        overlaps = true;
		                        break;
		                    }
		                }

		                if (!overlaps) {
		                    g.drawOval(circleX, circleY, diameter, diameter);
		                    drawnCircles.add(circleBounds);
		                    renderedCount++;
		                }
		            }
		        }

		        g.dispose();
		    }
		});
		VisiblePainter tracksOverlay = new LineOverlay(() -> new PaintingLogic() {
			public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
				g = (Graphics2D) g.create();
			    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			    Rectangle rect = mapKit.getMainMap().getViewportBounds();
			    g.translate(-rect.x, -rect.y);

			    g.setColor(Color.RED);
			    g.setStroke(new BasicStroke(2));

			    Rectangle visibleRect = map.getViewportBounds();
			    double marginDegrees = 0.01;
			    int zoom = map.getZoom();
			    double marginPixels = marginDegrees * map.getTileFactory().getInfo().getLongitudeDegreeWidthInPixels(zoom);

			    visibleRect.setBounds((int) (visibleRect.x - marginPixels), (int) (visibleRect.y - marginPixels),
			                          (int) (visibleRect.width + 2 * marginPixels), (int) (visibleRect.height + 2 * marginPixels));
			    
		    	for (List<GeoPosition> t : tracks) {
		    		if(t.size() > mapKit.getMainMap().getZoom()*3 || mapKit.getMainMap().getZoom() < 5) {
		    			Point2D startPtGeo = mapKit.getMainMap().getTileFactory().geoToPixel(t.get(0), mapKit.getMainMap().getZoom());
				        if(visibleRect.contains(startPtGeo)) {
				        	for (int i = 1; i < t.size(); i++) {
					            Point2D endPtGeo = mapKit.getMainMap().getTileFactory().geoToPixel(t.get(i), mapKit.getMainMap().getZoom());
					            g.drawLine((int) startPtGeo.getX(), (int) startPtGeo.getY(), (int) endPtGeo.getX(), (int) endPtGeo.getY());
					            startPtGeo = endPtGeo;
					        }
				        }
		    		}
			    }
			    g.dispose();
			}
        });
		VisiblePainter loadingTracksOverlay = new LineOverlay(() -> new PaintingLogic() {
			public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
				if(!doneSearching)
					return;
				g = (Graphics2D) g.create();
			    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			    Rectangle rect = mapKit.getMainMap().getViewportBounds();
			    g.translate(-rect.x, -rect.y);

			    g.setColor(Color.YELLOW);
			    g.setStroke(new BasicStroke(2));

			    Rectangle visibleRect = map.getViewportBounds();
			    double marginDegrees = 0.01;
			    int zoom = map.getZoom();
			    double marginPixels = marginDegrees * map.getTileFactory().getInfo().getLongitudeDegreeWidthInPixels(zoom);

			    visibleRect.setBounds((int) (visibleRect.x - marginPixels), (int) (visibleRect.y - marginPixels),
			                          (int) (visibleRect.width + 2 * marginPixels), (int) (visibleRect.height + 2 * marginPixels));
			    
		    	for (RailwayTrack rt : sp.searchedTracks) {
		    		if(rt.getLength() > mapKit.getMainMap().getZoom()*sp.routeCords.size() / 60 || mapKit.getMainMap().getZoom() < 7) {
		    			WayNode node1 = sp.longToWayNode(rt.getNodes().get(0));
				        GeoPosition startGeo = new GeoPosition(node1.getLatitude(), node1.getLongitude());
				        Point2D startPtGeo = mapKit.getMainMap().getTileFactory().geoToPixel(startGeo, mapKit.getMainMap().getZoom());

				        if(visibleRect.contains(startPtGeo)) {
				        	for (int i = 1; i < rt.getNodes().size(); i++) {
				        		WayNode node2 = sp.longToWayNode(rt.getNodes().get(i));
					        	GeoPosition endPoint = new GeoPosition(node2.getLatitude(), node2.getLongitude());
					            Point2D endPtGeo = mapKit.getMainMap().getTileFactory().geoToPixel(endPoint, mapKit.getMainMap().getZoom());
					            g.drawLine((int) startPtGeo.getX(), (int) startPtGeo.getY(), (int) endPtGeo.getX(), (int) endPtGeo.getY());
					            startPtGeo = endPtGeo;
					        }
				        }
		    		}
			    }
			    g.dispose();
			}
        });
		CompositePainter compositePainter = new CompositePainter(routeOverlay, stationsOverlay, tracksOverlay, loadingTracksOverlay);
		overlays.addAll(Arrays.asList(compositePainter.painters));
		
		mapKit.getMainMap().setOverlayPainter(compositePainter);
	}
	
	public void createLocalTracks() {
		tracks = new ArrayList<>(sp.tracks.size());
		for(int i = 0; i < sp.tracks.size(); i++) {
			tracks.add(new ArrayList<>());
			for(long n : sp.tracks.get(i).getNodes()) {
				WayNode wn = sp.longToWayNode(n);
				tracks.get(i).add(new GeoPosition(wn.getLatitude(), wn.getLongitude()));
			}
		}
	}

	private void openHtml() {
		File dir = new File("res/index.html");
		if(dir.exists()) {
			try {
				Desktop.getDesktop().browse(dir.toURI());
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			JOptionPane.showMessageDialog(this, "No html created yet, please make a route!");
		}
	}
	
	private void exportGPX() {
		if(doneSearching) {
			Exporter ex = new Exporter();
			ex.exportAsGPX(sp.routeCords, "res/gpxExport.gpx");
		}
	}
	
	private void loadData() {
		sp.doneLoading = false;
		startupWindow = new StartupWindow();
		startupWindow.setVisible(false);
		Thread inputThread = new Thread(new Runnable() {
	        @Override
	        public void run() {
	            JFrame inputFrame = new JFrame("Coordinate Input");
	            inputFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	            CoordinateInputPanel cip = new CoordinateInputPanel();
	            inputFrame.add(cip);
	            inputFrame.pack();
	            inputFrame.setLocationRelativeTo(null);
	            inputFrame.setVisible(true);
	            inputFrame.addWindowListener(new WindowListener() {

					@Override
					public void windowOpened(WindowEvent e) {
						
					}

					@Override
					public void windowClosing(WindowEvent e) {
						menuBar.getMenu(0).getItem(0).setEnabled(true);
					}

					@Override
					public void windowClosed(WindowEvent e) {
						
					}

					@Override
					public void windowIconified(WindowEvent e) {
						
					}

					@Override
					public void windowDeiconified(WindowEvent e) {
						
					}

					@Override
					public void windowActivated(WindowEvent e) {
						
					}

					@Override
					public void windowDeactivated(WindowEvent e) {
						
					}
	            });
	        }
	    });
	    inputThread.start();
	}
	
	private JTextField createStopField(String tooltip) {
        JTextField field = new JTextField(1);
        field.setMaximumSize(new Dimension(200, 50));
        field.setPreferredSize(new Dimension(200, 50));
        field.setToolTipText(tooltip);
        return field;
    }
	
	private void createMenuButtons() {
	    JMenu fileMenu = new JMenu("File");
	    JMenu editMenu = new JMenu("Edit");
	    JMenu settingsMenu = new JMenu("Settings");
	    
	    menuBar = new JMenuBar();
	    
        JMenuItem fileItem1 = new JMenuItem("Exit");
        JMenuItem fileItem2 = new JMenuItem("Open html");
        JMenuItem fileItem3 = new JMenuItem("Download data");
        JMenuItem fileItem4 = new JMenuItem("Export as GPX");
        fileItem1.addActionListener(e -> System.exit(0));
        fileItem2.addActionListener(e -> openHtml());
        fileItem3.addActionListener(e -> loadData());
        fileItem4.addActionListener(e -> exportGPX());
        fileMenu.add(fileItem3);
        fileMenu.add(fileItem2);
        fileMenu.add(fileItem4);
        fileMenu.add(fileItem1);
        
        JMenuItem editItem1 = new JMenuItem("Reset map");
        editItem1.addActionListener(e -> {
        	for(VisiblePainter p : overlays) {
        		p.setVisible(false);
        	}
        	for (Component menuItem : settingsMenu.getMenuComponents()) {
                if (menuItem instanceof JCheckBoxMenuItem) {
                    ((JCheckBoxMenuItem) menuItem).setSelected(false);
                }
            }
        	sp.routeCords.clear();
        	sp.searchedTracks.clear();
        	mapKit.getMainMap().repaint();
        });
        editMenu.add(editItem1);

        JCheckBoxMenuItem settingItem1 = new JCheckBoxMenuItem("Show stations");
        JCheckBoxMenuItem settingItem2 = new JCheckBoxMenuItem("Show tracks");
        JCheckBoxMenuItem settingItem3 = new JCheckBoxMenuItem("Show searched tracks");
        settingItem1.addActionListener(e -> {overlays.get(1).toggleVisible(); mapKit.getMainMap().repaint();});
        settingItem2.addActionListener(e -> {overlays.get(2).toggleVisible(); mapKit.getMainMap().repaint();});
        settingItem3.addActionListener(e -> {overlays.get(3).toggleVisible(); mapKit.getMainMap().repaint();});
        settingsMenu.add(settingItem1);
        settingsMenu.add(settingItem2);
        settingsMenu.add(settingItem3);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(settingsMenu);

        setJMenuBar(menuBar);
	}

	private void addStopField() {
	    if (stopFields.size() >= 2) {
	        int insertIndex = stopFields.size() - 1;
	        JTextField stopField = new JTextField(1);
	        AutocompleteDocumentListener stopListener = new AutocompleteDocumentListener(stopField);
	        stopListener.setSuggestions(suggestions);
	        stopField.getDocument().addDocumentListener(stopListener);
	        stopField.setMaximumSize(new Dimension(200, 50));
	        stopField.setPreferredSize(new Dimension(200, 50));
	        stopField.setToolTipText("Enter stop location");
	        stopFields.add(insertIndex, stopField);
	        stopsPanel.add(stopField, insertIndex * 2);
	        stopsPanel.add(Box.createRigidArea(new Dimension(0, 10)), insertIndex * 2 + 1);
	        revalidate();
	        repaint();
	        scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());
	    } else {
	        JOptionPane.showMessageDialog(this, "Add start and end locations first!");
	    }
	}

	private void removeStopField() {
	    if (stopFields.size() > 2) {
	        int removeIndex = stopFields.size() - 2;
	        JTextField stopField = stopFields.remove(removeIndex);
	        stopsPanel.remove(stopField);
	        stopsPanel.remove(removeIndex * 2);
	        revalidate();
	        repaint();
	    } else {
	        JOptionPane.showMessageDialog(this, "No stops to remove!");
	    }
	}

	public void searchRoute() {
		if (sp.doneLoading && doneSearching) {
			doneSearching = false;
			sp.searchedTracks.clear();
			String startLocation = stopFields.get(0).getText();
			String endLocation = stopFields.get(stopFields.size()-1).getText();
			List<Station> startStations = sp.getStationByName(startLocation);
			Station startStation = null;
			RailwayTrack startTrack = null;
			if(startStations == null) {
				startTrack = sp.getTrackById(Integer.parseInt(startLocation), true);
				if(startTrack == null) {
					doneSearching = true;
					return;
				}
			} else if(startStations.size() > 1) {
				startStation = chooseStation(startStations);
			} else {
				startStation = startStations.get(0);
			}
			
			if(startTrack == null) {
				if(startStation != null) {
					startTrack = sp.getTrackById(startStation.getTracks().get(0), false);
				} else {
					doneSearching = true;
					return;
				}
			}
			List<Station> endStations = sp.getStationByName(endLocation);
			Station endStation = null;
			RailwayTrack endTrack = null;
			if(endStations == null) {
				endTrack = sp.getTrackById(Integer.parseInt(endLocation), true);
				if(endTrack == null) {
					doneSearching = true;
					return;
				}
			} else if(endStations.size() > 1) {
				endStation = chooseStation(endStations);
			} else {
				endStation = endStations.get(0);
			}
			
			if(endTrack == null) {
				if(endStation != null) {
					endTrack = sp.getTrackById(endStation.getTracks().get(0), false);
				} else {
					doneSearching = true;
					return;
				}
			}
			
			if(startTrack == endTrack && stopFields.size() == 2) {
				JOptionPane.showMessageDialog(this, "Please enter a different end location!");
				return;
			}
			if(overlays.get(3).isVisible()) {
				ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

		        executor.scheduleAtFixedRate(() -> {
		            if (doneSearching) {
		                executor.shutdown();
		                return;
		            }
		            mapKit.getMainMap().repaint();

		        }, 0, 16, TimeUnit.MILLISECONDS);
			}
			if (stopFields.size() > 2) {
				List<String> stopLocations = stopFields.stream().map(JTextField::getText).collect(Collectors.toList());
				stopLocations.remove(0);
				stopLocations.remove(stopLocations.get(stopLocations.size()-1));
				List<RailwayTrack> stopTracks = new ArrayList<>();
				for (String s : stopLocations) {
					List<Station> stopStations = sp.getStationByName(s);
					Station stopStation = null;
					RailwayTrack stopTrack = null;
					if(stopStations == null) {
						stopTrack = sp.getTrackById(Integer.parseInt(s), true);
						if(stopTrack == null) {
							doneSearching = true;
							return;
						}
					} else if(stopStations.size() > 1) {
						stopStation = chooseStation(stopStations);
					} else {
						stopStation = stopStations.get(0);
					}
					
					if(stopTrack == null) {
						if(stopStation != null) {
							stopTrack = sp.getTrackById(stopStation.getTracks().get(0), false);
						} else {
							doneSearching = true;
							return;
						}
					}
				    stopTracks.add(stopTrack);
				}
				sp.outputToMap(startTrack, endTrack, stopTracks);
			} else {
				sp.outputToMap(startTrack, endTrack, null);
			}
			overlays.get(0).setVisible(true);
			mapKit.setCenterPosition(sp.routeCords.get(sp.routeCords.size()/2));
			for (int zoom = 1; zoom >= 1; zoom++) {
			    mapKit.setZoom(zoom);

			    Rectangle2D visibleArea = mapKit.getMainMap().getViewportBounds();
			    Point2D firstPoint = mapKit.getMainMap().getTileFactory().geoToPixel(sp.routeCords.get(0), zoom);
			    Point2D lastPoint = mapKit.getMainMap().getTileFactory().geoToPixel(sp.routeCords.get(sp.routeCords.size()-1), zoom);

			    if (visibleArea.contains(firstPoint) && visibleArea.contains(lastPoint)) {
			        break;
			    }
			}
			doneSearching = true;
			mapKit.getMainMap().repaint();
		} else {
			JOptionPane.showMessageDialog(this, "The SpeedCalculator isn't loaded yet!");
		}
	}
	
	private Station chooseStation(List<Station> stations) {
        String[] stationDescriptions = new String[stations.size()];
        for (int i = 0; i < stations.size(); i++) {
            Station s = stations.get(i);
            stationDescriptions[i] = s.toString();
        }

        String selectedStationDescription = (String) JOptionPane.showInputDialog(
            null,
            "Multiple stations found with the name \"" + stations.get(0).getName() + "\". Please select one:",
            "Select Station",
            JOptionPane.PLAIN_MESSAGE,
            null,
            stationDescriptions,
            stationDescriptions[0]
        );

        if (selectedStationDescription != null) {
            for (Station s : stations) {
                if (selectedStationDescription.contains(s.toString())) {
                	return s;
                }
            }
        }
		return null;
	}
	
	private class AutocompleteDocumentListener implements DocumentListener, FocusListener {

		private final JTextField textField;
		private final JWindow suggestionsWindow;
		private final List<String> suggestions;

		public AutocompleteDocumentListener(JTextField textField) {
	        this.textField = textField;
	        this.suggestionsWindow = new JWindow();
	        this.suggestions = new ArrayList<>();
	        this.suggestionsWindow.setOpacity(0.75f);

	        textField.getDocument().addDocumentListener(this);
	        textField.addFocusListener(this);
	        textField.setFocusTraversalKeysEnabled(false);
	    }

		@Override
		public void insertUpdate(DocumentEvent e) {
			updateSuggestions();
		}

		@Override
		public void removeUpdate(DocumentEvent e) {
			updateSuggestions();
		}

		@Override
		public void changedUpdate(DocumentEvent e) {
			updateSuggestions();
		}

		private void updateSuggestions() {
			String input = textField.getText().toLowerCase();
			List<String> matches = sp.suggestSimilarWords(input,
					sp.stations.stream().map(Station::getName).distinct().collect(Collectors.toList()));

			showSuggestions(matches);
		}

		private void showSuggestions(List<String> matches) {
			suggestionsWindow.getContentPane().removeAll();
			suggestionsWindow.getContentPane()
					.setLayout(new BoxLayout(suggestionsWindow.getContentPane(), BoxLayout.Y_AXIS));

			for (String match : matches) {
				JLabel label = new JLabel(match);
				label.setOpaque(true);
				label.setBackground(textField.getBackground());
				label.setForeground(textField.getForeground());
				label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
				label.addMouseListener(new SuggestionClickListener(label));
				suggestionsWindow.getContentPane().add(label);
			}

			suggestionsWindow.pack();
			suggestionsWindow.setLocation(textField.getLocationOnScreen().x,
					textField.getLocationOnScreen().y + textField.getHeight());
			suggestionsWindow.setVisible(true);
		}

		public void setSuggestions(List<String> suggestions) {
			this.suggestions.clear();
			this.suggestions.addAll(suggestions);
		}

		private class SuggestionClickListener extends MouseAdapter {
			private final JLabel label;

			public SuggestionClickListener(JLabel label) {
				this.label = label;
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				textField.setText(label.getText());
				suggestionsWindow.setVisible(false);
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				label.setBackground(textField.getSelectionColor());
				label.setForeground(textField.getSelectedTextColor());
			}

			@Override
			public void mouseExited(MouseEvent e) {
				label.setBackground(textField.getBackground());
				label.setForeground(textField.getForeground());
			}
		}

		@Override
		public void focusGained(FocusEvent e) {
			
		}

		@Override
		public void focusLost(FocusEvent e) {
			suggestionsWindow.setVisible(false);
		}
	}
	
	private class CoordinateInputPanel extends JPanel {
		private static final long serialVersionUID = 9187332222036322673L;
	    private JTextField lat1Field, lat2Field, lon1Field, lon2Field;
	    private JComboBox<String> countryComboBox;
	    private JButton okButton;

	    private double lat1, lat2, lon1, lon2;

	    public CoordinateInputPanel() {
	    	menuBar.getMenu(0).getItem(0).setEnabled(false);
	        setLayout(new GridLayout(6, 2));

	        add(new JLabel("Latitude 1:"));
	        lat1Field = new JTextField();
	        add(lat1Field);

	        add(new JLabel("Latitude 2:"));
	        lat2Field = new JTextField();
	        add(lat2Field);

	        add(new JLabel("Longitude 1:"));
	        lon1Field = new JTextField();
	        add(lon1Field);

	        add(new JLabel("Longitude 2:"));
	        lon2Field = new JTextField();
	        add(lon2Field);

	        add(new JLabel("Select Country:"));
	        countryComboBox = new JComboBox<>(getCountryListWithRailways());
	        countryComboBox.insertItemAt("", 0);
	        countryComboBox.setSelectedIndex(0);
	        add(countryComboBox);
	        
	        okButton = new JButton("OK");
	        okButton.addActionListener(new ActionListener() {
	            @Override
	            public void actionPerformed(ActionEvent e) {
	                try {
	                	String country = String.valueOf(countryComboBox.getSelectedItem());
	                	boolean isCountry = country != "";
	                	if(!isCountry) {
	                		lat1 = Double.parseDouble(lat1Field.getText());
		                    lat2 = Double.parseDouble(lat2Field.getText());
		                    lon1 = Double.parseDouble(lon1Field.getText());
		                    lon2 = Double.parseDouble(lon2Field.getText());
	                	}
	                    String coordinates = "(" + Math.min(lon1, lon2) + "," + Math.min(lat1, lat2) + "," + Math.max(lon1, lon2) + "," + Math.max(lat1, lat2) + ")";
	                    SwingUtilities.getWindowAncestor(CoordinateInputPanel.this).dispose();
	                    startupWindow.setVisible(true);
	                    Thread dataThread = new Thread(new Runnable() {
	                        @Override
	                        public void run() {
	                        	if(isCountry) {
	                        		sp.downloadData(country, true);
	    	                	} else {
	    	                		sp.downloadData(coordinates, false);
	    	                	}
	                        	createLocalTracks();
	                        	if(sp.tracks.size() == 0)
	                        		JOptionPane.showMessageDialog(CoordinateInputPanel.this, "No tracks found for this area.");
	                            startupWindow.dispose();
	                            menuBar.getMenu(0).getItem(0).setEnabled(true);
	                        }
	                    });
	                    dataThread.start();
	                } catch (NumberFormatException ex) {
	                    JOptionPane.showMessageDialog(CoordinateInputPanel.this, "Invalid input. Please enter numeric values.");
	                }
	            }
	        });

	        add(okButton);
	    }

	    private String[] getCountryListWithRailways() {
	        List<String> countries = new ArrayList<>();

	        Locale[] locales = Locale.getAvailableLocales();

	        for (Locale locale : locales) {
	            String countryName = locale.getDisplayCountry();

	            if (!countryName.isEmpty() && !countries.contains(countryName)) {
	                countries.add(countryName);
	            }
	        }

	        countries.sort(String::compareToIgnoreCase);

	        return countries.toArray(new String[0]);
	    }
	}
}
