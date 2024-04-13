package net.therailwayproject.alex;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class MapViewer extends Application {

    private TextField startField;
    private TextField endField;
    private Button searchButton;
    private ProgressBar[] loadingBars;
    private SpeedCalculator sp;

    @Override
    public void start(Stage primaryStage) {
    	sp = SpeedCalculator.INSTANCE();
        primaryStage.setTitle("Map Application");

        // Text fields for start and end destinations
        startField = new TextField();
        startField.setPromptText("Enter start location");

        endField = new TextField();
        endField.setPromptText("Enter end location");

        // Button to initiate the search
        searchButton = new Button("Go");
        searchButton.setOnAction(event -> searchRoute());
        
        loadingBars = new ProgressBar[5];
        for (int i = 0; i < 5; i++) {
            loadingBars[i] = new ProgressBar();
            loadingBars[i].setProgress(0); // Initialize progress to 0
        }

        // Layout for the control panel
        VBox controlPanel = new VBox(10);
        controlPanel.setAlignment(Pos.CENTER);
        controlPanel.setPadding(new Insets(20));
        controlPanel.getChildren().addAll(startField, endField, searchButton);
        controlPanel.getChildren().addAll(loadingBars);

        // Set up the scene
        Scene scene = new Scene(controlPanel, 400, 200);
        primaryStage.setScene(scene);
        primaryStage.show();
        
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateProgressBars()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void updateProgressBars() {
        for (int i = 0; i < sp.progressBars.length && i < loadingBars.length; i++) {
            loadingBars[i].setProgress(sp.progressBars[i]);
        }
    }

	public void searchRoute() {
        if(sp.doneLoading) {
        	String startLocation = startField.getText();
            String endLocation = endField.getText();
//            RailwayTrack startTrack = sp.getTrackById(Integer.parseInt(startLocation));
//            RailwayTrack endTrack = sp.getTrackById(Integer.parseInt(endLocation));
            RailwayTrack startTrack = sp.getTrackById(sp.getStationByName(startLocation).getTracks().get(0));
            RailwayTrack endTrack = sp.getTrackById(sp.getStationByName(endLocation).getTracks().get(0));
            sp.outputToMap(startTrack, endTrack);
            try {
    			Desktop.getDesktop().browse(new File("res/index.html").toURI());
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
        } else {
        	System.out.println("The SpeedCalculator isn't loaded yet!");
        }
    }
}