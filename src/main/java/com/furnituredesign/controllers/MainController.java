package com.furnituredesign.controllers;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import com.furnituredesign.models.*;
import com.furnituredesign.services.DesignService;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.SubScene;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.PointLight;
import javafx.scene.transform.Rotate;
import javafx.scene.shape.Cylinder;
import javafx.scene.Node;

public class MainController {
    @FXML
    private TextField roomWidthField;
    @FXML
    private TextField roomLengthField;
    @FXML
    private TextField roomHeightField;
    @FXML
    private ColorPicker wallColorPicker;
    @FXML
    private ColorPicker floorColorPicker;
    @FXML
    private ComboBox<String> furnitureTypeCombo;
    @FXML
    private ListView<Furniture> furnitureListView;
    @FXML
    private Canvas designCanvas;
    @FXML
    private ColorPicker furnitureColorPicker;
    @FXML
    private SubScene room3DSubScene;
    @FXML
    private ToggleButton toggle2DView;
    @FXML
    private ToggleButton toggle3DView;
    @FXML
    private Label statusLabel;

    private final DesignService designService = new DesignService();
    private Room currentRoom;
    private List<Furniture> furnitureList = new ArrayList<>();
    private boolean is3DView = false;
    private Furniture selectedFurniture = null;
    private double dragOffsetX, dragOffsetY;
    private PerspectiveCamera camera3D;
    private double anchorX, anchorY;
    private double anchorAngleX = -20, anchorAngleY = -20;
    private double cameraAngleX = -20, cameraAngleY = -20;
    private double cameraPanX = 0, cameraPanY = 0;
    private boolean panning = false;
    private Rotate rotateX = new Rotate(-20, Rotate.X_AXIS);
    private Rotate rotateY = new Rotate(-20, Rotate.Y_AXIS);

    @FXML
    public void initialize() {
        // Initialize Furniture Types
        furnitureTypeCombo.getItems().addAll(
                "Chair", "Table", "Sofa", "Bed", "Cabinet", "BookShelf");

        // Set Default Colors
        wallColorPicker.setValue(Color.WHITE);
        floorColorPicker.setValue(Color.LIGHTGRAY);

        // Add listener for floor color change
        floorColorPicker.setOnAction(e -> {
            if (currentRoom != null) {
                currentRoom.setFloorColor(floorColorPicker.getValue().toString());
                if (is3DView) {
                    build3DRoomScene();
                }
                redraw();
            }
        });

        // Set up List
        furnitureListView.setMaxHeight(Double.MAX_VALUE);
        furnitureListView.setMaxWidth(Double.MAX_VALUE);

        // Initialize canvas
        designCanvas.widthProperty().addListener((obs, oldVal, newVal) -> redraw());
        designCanvas.heightProperty().addListener((obs, oldVal, newVal) -> redraw());

        // Mouse events for dragging furniture
        designCanvas.setOnMousePressed(e -> {
            double mouseX = e.getX();
            double mouseY = e.getY();
            selectedFurniture = null;
            for (Furniture furniture : furnitureList) {
                double fx = furniture.getX();
                double fy = furniture.getY();
                double fw = 50, fh = 50; // pixel size for drawing
                if (mouseX >= fx && mouseX <= fx + fw && mouseY >= fy && mouseY <= fy + fh) {
                    selectedFurniture = furniture;
                    dragOffsetX = mouseX - fx;
                    dragOffsetY = mouseY - fy;
                    break;
                }
            }
        });
        designCanvas.setOnMouseDragged(e -> {
            if (selectedFurniture != null) {
                double mouseX = e.getX();
                double mouseY = e.getY();
                // Room bounds in pixels
                double minX = 50, minY = 50;
                double maxX = designCanvas.getWidth() - 100;
                double maxY = designCanvas.getHeight() - 100;
                double newX = mouseX - dragOffsetX;
                double newY = mouseY - dragOffsetY;
                // Clamp to room
                newX = Math.max(minX, Math.min(newX, minX + maxX - 50));
                newY = Math.max(minY, Math.min(newY, minY + maxY - 50));
                selectedFurniture.setX(newX);
                selectedFurniture.setY(newY);
                redraw();
            }
        });
        designCanvas.setOnMouseReleased(e -> selectedFurniture = null);

        // Zoom for 3D view
        room3DSubScene.setOnScroll(e -> {
            if (is3DView && camera3D != null) {
                double delta = e.getDeltaY();
                double newZ = camera3D.getTranslateZ() + (delta > 0 ? 50 : -50);
                newZ = Math.max(-10000, Math.min(-200, newZ));
                camera3D.setTranslateZ(newZ);
            }
        });
        // Mouse  rotation and panning
        room3DSubScene.setOnMousePressed(e -> {
            anchorX = e.getSceneX();
            anchorY = e.getSceneY();
            anchorAngleX = cameraAngleX;
            anchorAngleY = cameraAngleY;
            panning = e.isSecondaryButtonDown() || e.isShiftDown();
        });
        room3DSubScene.setOnMouseDragged(e -> {
            if (is3DView && camera3D != null) {
                double dx = e.getSceneX() - anchorX;
                double dy = e.getSceneY() - anchorY;
                if (panning) {
                    cameraPanX += dx * 0.5;
                    cameraPanY += dy * 0.5;
                    camera3D.setTranslateX(cameraPanX);
                    camera3D.setTranslateY(
                            -cameraPanY - (currentRoom != null ? (currentRoom.getHeight() * 100) / 4 : 0));
                    anchorX = e.getSceneX();
                    anchorY = e.getSceneY();
                } else {
                    cameraAngleY = anchorAngleY + dx * 0.3;
                    cameraAngleX = anchorAngleX - dy * 0.3;
                    rotateY.setAngle(cameraAngleY);
                    rotateX.setAngle(cameraAngleX);
                }
            }
        });

        // Sync color changes to 3D
        wallColorPicker.setOnAction(e -> {
            if (is3DView)
                build3DRoomScene();
        });
        
        // Configure toggle buttons for 2D/3D view
        setupViewToggles();
        
        // Set default status
        updateStatus("Ready");
    }
    
    private void setupViewToggles() {
        // Create a toggle group to ensure only one button can be selected
        ToggleGroup viewToggleGroup = new ToggleGroup();
        toggle2DView.setToggleGroup(viewToggleGroup);
        toggle3DView.setToggleGroup(viewToggleGroup);
        
        // Set 2D view as default
        toggle2DView.setSelected(true);
        
        // Add listeners for toggle buttons
        toggle2DView.setOnAction(e -> {
            if (toggle2DView.isSelected()) {
                handle2DView();
            }
        });
        
        toggle3DView.setOnAction(e -> {
            if (toggle3DView.isSelected()) {
                handle3DView();
            }
        });
    }
    
    private void updateStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText("Status: " + message);
        }
    }

    @FXML
    private void handleCreateRoom() {
        try {
            // Validate input fields
            if (roomWidthField.getText().isEmpty() ||
                    roomLengthField.getText().isEmpty() ||
                    roomHeightField.getText().isEmpty()) {
                showError("Please enter all room dimensions");
                return;
            }

            double width = Double.parseDouble(roomWidthField.getText());
            double length = Double.parseDouble(roomLengthField.getText());
            double height = Double.parseDouble(roomHeightField.getText());

            // Validate dimensions
            if (width <= 0 || length <= 0 || height <= 0) {
                showError("Room dimensions must be greater than 0");
                return;
            }

            // Create new room
            currentRoom = new Room(width, length, height);
            furnitureList.clear();
            furnitureListView.getItems().clear();

            // Apply colors
            currentRoom.setWallColor(wallColorPicker.getValue().toString());
            currentRoom.setFloorColor(floorColorPicker.getValue().toString());

            // Redraw the canvas
            redraw();

            // Show success message
            showSuccess("Room created successfully!");
            updateStatus("Room created: " + width + "m × " + length + "m × " + height + "m");
        } catch (NumberFormatException e) {
            showError("Please enter valid numbers for room dimensions");
        }
    }

    @FXML
    private void handleAddFurniture() {
        if (currentRoom == null) {
            showError("Please create a room first");
            return;
        }
        String type = furnitureTypeCombo.getValue();
        if (type == null) {
            showError("Please select a furniture type");
            return;
        }
        Furniture furniture = new Furniture(type);
        // Place in center of room area (in pixels)
        furniture.setX(50 + (designCanvas.getWidth() - 100) / 2 - 25);
        furniture.setY(50 + (designCanvas.getHeight() - 100) / 2 - 25);
        // Set color from color picker
        if (furnitureColorPicker != null && furnitureColorPicker.getValue() != null) {
            furniture.setColor(furnitureColorPicker.getValue().toString());
        }
        furnitureList.add(furniture);
        furnitureListView.getItems().add(furniture);
        redraw();
        updateStatus("Added " + type + " to the room");
    }

    @FXML
    private void handleRemoveFurniture() {
        Furniture selected = furnitureListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            furnitureList.remove(selected);
            furnitureListView.getItems().remove(selected);
            redraw();
            updateStatus("Removed " + selected.getType() + " from the room");
        }
    }

    @FXML
    private void handleSaveDesign() {
        if (currentRoom == null) {
            showError("No design to save");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Design");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Design Files", "*.json"));

        File file = fileChooser.showSaveDialog(designCanvas.getScene().getWindow());
        if (file != null) {
            designService.saveDesign(currentRoom, furnitureList, file);
            updateStatus("Design saved");
        }
    }

    @FXML
    private void handleLoadDesign() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Design");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Design Files", "*.json"));

        File file = fileChooser.showOpenDialog(designCanvas.getScene().getWindow());
        if (file != null) {
            Design design = designService.loadDesign(file);
            if (design != null) {
                currentRoom = design.getRoom();
                furnitureList = design.getFurniture();
                furnitureListView.getItems().setAll(furnitureList);
                redraw();
                updateStatus("Design loaded");
            }
        }
    }

    @FXML
    private void handle2DView() {
        is3DView = false;
        designCanvas.setVisible(true);
        room3DSubScene.setVisible(false);
        toggle2DView.setSelected(true);
        toggle3DView.setSelected(false);
        redraw();
        updateStatus("Switched to 2D View");
    }

    @FXML
    private void handle3DView() {
        is3DView = true;
        designCanvas.setVisible(false);
        room3DSubScene.setVisible(true);
        toggle2DView.setSelected(false);
        toggle3DView.setSelected(true);
        build3DRoomScene();
        updateStatus("Switched to 3D View");
    }

    @FXML
    private void handleApplyShading() {
        if (is3DView) {
            // Apply advanced shading to 3D view
            build3DRoomScene();
            updateStatus("Applied shading to 3D view");
        }
    }

    @FXML
    private void handleResetView() {
        if (is3DView && camera3D != null) {
            // Reset camera position and rotation
            cameraAngleX = -20;
            cameraAngleY = -20;
            cameraPanX = 0;
            cameraPanY = 0;
            rotateX.setAngle(cameraAngleX);
            rotateY.setAngle(cameraAngleY);
            camera3D.setTranslateX(cameraPanX);
            camera3D.setTranslateY(cameraPanY);
            camera3D.setTranslateZ(-1000);
            updateStatus("View reset");
        }
    }

    @FXML
    private void handleExit() {
        System.exit(0);
    }

    @FXML
    private void handleNewDesign() {
        // Clear the current room and furniture
        currentRoom = null;
        furnitureList.clear();
        furnitureListView.getItems().clear();
        roomWidthField.clear();
        roomLengthField.clear();
        roomHeightField.clear();
        wallColorPicker.setValue(Color.WHITE);
        floorColorPicker.setValue(Color.LIGHTGRAY);
        redraw();
        updateStatus("Created new design");
    }

    private void redraw() {
        if (currentRoom == null)
            return;

        GraphicsContext gc = designCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, designCanvas.getWidth(), designCanvas.getHeight());

        if (is3DView) {
            draw3DView(gc);
        } else {
            draw2DView(gc);
        }
    }

    private void draw2DView(GraphicsContext gc) {
        // Fill background with white
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, designCanvas.getWidth(), designCanvas.getHeight());

        // Calculate room dimensions to match 3D view
        double scale = 100; // Same scale as 3D view
        double roomW = currentRoom.getWidth() * scale;
        double roomL = currentRoom.getLength() * scale;

        // Center the room in the canvas
        double startX = (designCanvas.getWidth() - roomW) / 2;
        double startY = (designCanvas.getHeight() - roomL) / 2;

        // Fill room with wall color if a room exists
        if (currentRoom != null) {
            Color wallColor = Color.WHITE;
            try {
                wallColor = Color.web(currentRoom.getWallColor());
            } catch (Exception ignored) {
            }
            gc.setFill(wallColor);
            gc.fillRect(startX, startY, roomW, roomL);
        }

        // Draw room outline
        gc.setStroke(Color.BLACK);
        gc.strokeRect(startX, startY, roomW, roomL);

        // Draw furniture at their (x, y) with specific shapes
        for (Furniture furniture : furnitureList) {
            Color color = Color.GRAY;
            try {
                color = Color.web(furniture.getColor());
            } catch (Exception ignored) {
            }
            gc.setFill(color);
            String type = furniture.getType().toLowerCase();
            double x = furniture.getX();
            double y = furniture.getY();
            switch (type) {
                case "chair":
                    // Circle for chair
                    gc.fillOval(x, y, 30, 30);
                    gc.setStroke(Color.BLACK);
                    gc.strokeOval(x, y, 30, 30);
                    break;
                case "table":
                    // Standard rectangle 50x30
                    gc.fillRect(x, y, 50, 30);
                    gc.setStroke(Color.BLACK);
                    gc.strokeRect(x, y, 50, 30);
                    break;
                case "sofa":
                    // Rounded rectangle 60x30 with rounded edges
                    gc.fillRoundRect(x, y, 60, 30, 15, 15);
                    gc.setStroke(Color.BLACK);
                    gc.strokeRoundRect(x, y, 60, 30, 15, 15);
                    break;
                case "bed":
                    // Large rectangle 70x40
                    gc.fillRect(x, y, 70, 40);
                    gc.setStroke(Color.BLACK);
                    gc.strokeRect(x, y, 70, 40);
                    break;
                case "cabinet":
                    // Tall rectangle 30x50
                    gc.fillRect(x, y, 30, 50);
                    gc.setStroke(Color.BLACK);
                    gc.strokeRect(x, y, 30, 50);
                    break;
                case "bookshelf":
                    // Very narrow and tall rectangle (1:5 ratio)
                    gc.fillRect(x, y, 15, 75);
                    gc.setStroke(Color.BLACK);
                    gc.strokeRect(x, y, 15, 75);
                    break;
                default:
                    // Default square 40x40
                    gc.fillRect(x, y, 40, 40);
                    gc.setStroke(Color.BLACK);
                    gc.strokeRect(x, y, 40, 40);
            }
        }
    }

    private void draw3DView(GraphicsContext gc) {
        // Basic 3D representation using perspective projection
        // This is a simplified version - in a real application, you'd use JavaFX 3D or
        // JMonkeyEngine
        gc.setFill(wallColorPicker.getValue());
        gc.fillRect(50, 50, designCanvas.getWidth() - 100, designCanvas.getHeight() - 100);

        // Draw furniture in 3D
        for (Furniture furniture : furnitureList) {
            // Simplified 3D furniture representation
            gc.setFill(Color.GRAY);
            gc.fillRect(100, 100, 50, 50);
        }
    }

    private void build3DRoomScene() {
        if (currentRoom == null)
            return;
        double width = currentRoom.getWidth();
        double length = currentRoom.getLength();
        double height = currentRoom.getHeight();
        double scale = 100;
        double roomW = width * scale;
        double roomL = length * scale;
        double roomH = height * scale;

        Group root3D = new Group();

        // Floor with selected floor color
        Box floor = new Box(roomW, 5, roomL);
        PhongMaterial floorMat = new PhongMaterial();
        Color selectedFloorColor = floorColorPicker.getValue();
        floorMat.setDiffuseColor(selectedFloorColor);
        floor.setMaterial(floorMat);
        floor.setTranslateY(roomH / 2);
        root3D.getChildren().add(floor);

        // Furniture
        for (Furniture furniture : furnitureList) {
            String type = furniture.getType().toLowerCase();
            double fw = 40, fl = 40, fh = 40;

            // Create appropriate 3D shape based on furniture type
            javafx.scene.Node furnitureShape;
            switch (type) {
                case "chair":
                    // Create a group for the chair
                    Group chairGroup = new Group();

                    // Seat - a flat cylinder
                    Cylinder seat = new Cylinder(15, 5);
                    PhongMaterial seatMat = new PhongMaterial();
                    try {
                        seatMat.setDiffuseColor(Color.web(furniture.getColor()));
                    } catch (Exception e) {
                        seatMat.setDiffuseColor(Color.BROWN);
                    }
                    seat.setMaterial(seatMat);
                    seat.setTranslateY(0); // Seat height from center

                    // Legs - 4 cylinders
                    int legHeight = 25;
                    double legOffset = 10;

                    Cylinder leg1 = new Cylinder(2, legHeight);
                    leg1.setMaterial(new PhongMaterial(Color.SADDLEBROWN));
                    leg1.setTranslateX(-legOffset);
                    leg1.setTranslateZ(-legOffset);
                    leg1.setTranslateY(legHeight / 2.0 + 2.5);

                    Cylinder leg2 = new Cylinder(2, legHeight);
                    leg2.setMaterial(new PhongMaterial(Color.SADDLEBROWN));
                    leg2.setTranslateX(legOffset);
                    leg2.setTranslateZ(-legOffset);
                    leg2.setTranslateY(legHeight / 2.0 + 2.5);

                    Cylinder leg3 = new Cylinder(2, legHeight);
                    leg3.setMaterial(new PhongMaterial(Color.SADDLEBROWN));
                    leg3.setTranslateX(-legOffset);
                    leg3.setTranslateZ(legOffset);
                    leg3.setTranslateY(legHeight / 2.0 + 2.5);

                    Cylinder leg4 = new Cylinder(2, legHeight);
                    leg4.setMaterial(new PhongMaterial(Color.SADDLEBROWN));
                    leg4.setTranslateX(legOffset);
                    leg4.setTranslateZ(legOffset);
                    leg4.setTranslateY(legHeight / 2.0 + 2.5);

                    // Optional: simple backrest (Box or Cylinder)
                    Box backrest = new Box(30, 20, 2);
                    backrest.setMaterial(new PhongMaterial(Color.SADDLEBROWN));
                    backrest.setTranslateY(-10);
                    backrest.setTranslateZ(-13); // Move behind the seat

                    // Add parts to the group
                    chairGroup.getChildren().addAll(seat, leg1, leg2, leg3, leg4, backrest);

                    // Set shape and dimensions
                    furnitureShape = chairGroup;
                    fw = fl = 30;
                    fh = 35; // 30 (legs + seat) + small backrest
                    break;
                case "table":
                    // Create a group for the table
                    Group tableGroup = new Group();

                    // Table top with beveled edges
                    Box tableTop = new Box(50, 4, 30);
                    PhongMaterial tableMat = new PhongMaterial();
                    try {
                        tableMat.setDiffuseColor(Color.web(furniture.getColor()));
                    } catch (Exception e) {
                        tableMat.setDiffuseColor(Color.GRAY);
                    }
                    tableTop.setMaterial(tableMat);
                    tableTop.setTranslateY(-10); // Raise it up to rest on legs

                    // Add beveled edges using thin boxes
                    double bevelSize = 1;
                    Box topBevel = new Box(52, bevelSize, 32);
                    Box bottomBevel = new Box(52, bevelSize, 32);
                    topBevel.setMaterial(tableMat);
                    bottomBevel.setMaterial(tableMat);
                    topBevel.setTranslateY(-12);
                    bottomBevel.setTranslateY(-8);

                    // Table legs with more detail
                    PhongMaterial legMat = new PhongMaterial(Color.DARKGRAY);
                    double tableLegHeight = 20;
                    double halfWidth = 23; // Slightly inset from edges
                    double halfLength = 13;

                    // Create decorative leg tops
                    double legTopSize = 4;
                    Box frontLeftLegTop = new Box(legTopSize, 2, legTopSize);
                    Box frontRightLegTop = new Box(legTopSize, 2, legTopSize);
                    Box backLeftLegTop = new Box(legTopSize, 2, legTopSize);
                    Box backRightLegTop = new Box(legTopSize, 2, legTopSize);

                    frontLeftLegTop.setMaterial(legMat);
                    frontRightLegTop.setMaterial(legMat);
                    backLeftLegTop.setMaterial(legMat);
                    backRightLegTop.setMaterial(legMat);

                    // Position leg tops
                    frontLeftLegTop.setTranslateX(-halfWidth);
                    frontLeftLegTop.setTranslateZ(-halfLength);
                    frontLeftLegTop.setTranslateY(-1);

                    frontRightLegTop.setTranslateX(halfWidth);
                    frontRightLegTop.setTranslateZ(-halfLength);
                    frontRightLegTop.setTranslateY(-1);

                    backLeftLegTop.setTranslateX(-halfWidth);
                    backLeftLegTop.setTranslateZ(halfLength);
                    backLeftLegTop.setTranslateY(-1);

                    backRightLegTop.setTranslateX(halfWidth);
                    backRightLegTop.setTranslateZ(halfLength);
                    backRightLegTop.setTranslateY(-1);

                    // Create legs with slightly tapered design
                    Cylinder frontLeftLeg = new Cylinder(1.5, tableLegHeight);
                    Cylinder frontRightLeg = new Cylinder(1.5, tableLegHeight);
                    Cylinder backLeftLeg = new Cylinder(1.5, tableLegHeight);
                    Cylinder backRightLeg = new Cylinder(1.5, tableLegHeight);

                    frontLeftLeg.setMaterial(legMat);
                    frontRightLeg.setMaterial(legMat);
                    backLeftLeg.setMaterial(legMat);
                    backRightLeg.setMaterial(legMat);

                    // Position legs
                    frontLeftLeg.setTranslateX(-halfWidth);
                    frontLeftLeg.setTranslateZ(-halfLength);
                    frontLeftLeg.setTranslateY(tableLegHeight / 2);

                    frontRightLeg.setTranslateX(halfWidth);
                    frontRightLeg.setTranslateZ(-halfLength);
                    frontRightLeg.setTranslateY(tableLegHeight / 2);

                    backLeftLeg.setTranslateX(-halfWidth);
                    backLeftLeg.setTranslateZ(halfLength);
                    backLeftLeg.setTranslateY(tableLegHeight / 2);

                    backRightLeg.setTranslateX(halfWidth);
                    backRightLeg.setTranslateZ(halfLength);
                    backRightLeg.setTranslateY(tableLegHeight / 2);

                    // Add all parts to the group
                    tableGroup.getChildren().addAll(
                            tableTop, topBevel, bottomBevel,
                            frontLeftLegTop, frontRightLegTop, backLeftLegTop, backRightLegTop,
                            frontLeftLeg, frontRightLeg, backLeftLeg, backRightLeg);

                    // Set the table as the furniture shape
                    furnitureShape = tableGroup;
                    fw = 50;
                    fl = 30;
                    fh = 25;
                    break;
                case "sofa":
                    Group sofaGroup = new Group();

                    // Seat base
                    Box sofaBase = new Box(60, 15, 30);
                    sofaBase.setTranslateY(5);

                    // Backrest
                    Box sofaBackrest = new Box(60, 15, 5);
                    sofaBackrest.setTranslateY(-5);
                    sofaBackrest.setTranslateZ(-12.5);

                    // Armrests
                    Box leftArm = new Box(5, 15, 30);
                    leftArm.setTranslateX(-27.5);
                    leftArm.setTranslateY(5);

                    Box rightArm = new Box(5, 15, 30);
                    rightArm.setTranslateX(27.5);
                    rightArm.setTranslateY(5);

                    PhongMaterial sofaMat = new PhongMaterial();
                    try {
                        sofaMat.setDiffuseColor(Color.web(furniture.getColor()));
                    } catch (Exception e) {
                        sofaMat.setDiffuseColor(Color.DARKSLATEGRAY);
                    }
                    sofaBase.setMaterial(sofaMat);
                    sofaBackrest.setMaterial(sofaMat);
                    leftArm.setMaterial(sofaMat);
                    rightArm.setMaterial(sofaMat);

                    sofaGroup.getChildren().addAll(sofaBase, sofaBackrest, leftArm, rightArm);
                    furnitureShape = sofaGroup;
                    fw = 60;
                    fl = 30;
                    fh = 25;
                    break;
                case "bed":
                    Group bedGroup = new Group();

                    // Bed base
                    Box bedBase = new Box(70, 10, 40);
                    bedBase.setTranslateY(5);

                    // Headboard
                    Box headboard = new Box(70, 15, 3);
                    headboard.setTranslateZ(-18.5);
                    headboard.setTranslateY(-2.5);

                    // Pillow area
                    Box pillow = new Box(60, 5, 10);
                    pillow.setTranslateZ(-10);
                    pillow.setTranslateY(-7.5);

                    PhongMaterial bedMat = new PhongMaterial();
                    try {
                        bedMat.setDiffuseColor(Color.web(furniture.getColor()));
                    } catch (Exception e) {
                        bedMat.setDiffuseColor(Color.LIGHTGRAY);
                    }

                    bedBase.setMaterial(bedMat);
                    headboard.setMaterial(bedMat);

                    PhongMaterial pillowMat = new PhongMaterial(Color.WHITE);
                    pillow.setMaterial(pillowMat);

                    bedGroup.getChildren().addAll(bedBase, headboard, pillow);
                    furnitureShape = bedGroup;
                    fw = 70;
                    fl = 40;
                    fh = 20;
                    break;
                case "cabinet":
                    Group cabinetGroup = new Group();

                    // Main box
                    Box cabinetBody = new Box(30, 50, 20);

                    // Vertical divider line (visual)
                    Box divider = new Box(1, 48, 1);
                    divider.setTranslateZ(-9.5);
                    divider.setMaterial(new PhongMaterial(Color.BLACK));

                    PhongMaterial cabinetMat = new PhongMaterial();
                    try {
                        cabinetMat.setDiffuseColor(Color.web(furniture.getColor()));
                    } catch (Exception e) {
                        cabinetMat.setDiffuseColor(Color.SADDLEBROWN);
                    }

                    cabinetBody.setMaterial(cabinetMat);
                    cabinetGroup.getChildren().addAll(cabinetBody, divider);
                    furnitureShape = cabinetGroup;
                    fw = 30;
                    fl = 20;
                    fh = 50;
                    break;
                case "bookshelf":
                    Group shelfGroup = new Group();

                    // Frame
                    Box frame = new Box(15, 60, 20);

                    // Add 3 horizontal shelves
                    for (int i = -1; i <= 1; i++) {
                        Box shelf = new Box(13, 1, 18);
                        shelf.setTranslateY(i * 15); // evenly spaced
                        shelfGroup.getChildren().add(shelf);
                    }

                    PhongMaterial shelfMat = new PhongMaterial();
                    try {
                        shelfMat.setDiffuseColor(Color.web(furniture.getColor()));
                    } catch (Exception e) {
                        shelfMat.setDiffuseColor(Color.BURLYWOOD);
                    }

                    frame.setMaterial(shelfMat);
                    for (Node node : shelfGroup.getChildren()) {
                        if (node instanceof Box && node != frame) {
                            ((Box) node).setMaterial(shelfMat);
                        }
                    }

                    shelfGroup.getChildren().add(frame);
                    furnitureShape = shelfGroup;
                    fw = 15;
                    fl = 20;
                    fh = 60;
                    break;
                default:
                    // Default box
                    Box defaultBox = new Box(40, 40, 40);
                    PhongMaterial defaultMat = new PhongMaterial();
                    try {
                        defaultMat.setDiffuseColor(Color.web(furniture.getColor()));
                    } catch (Exception e) {
                        defaultMat.setDiffuseColor(Color.GRAY);
                    }
                    defaultBox.setMaterial(defaultMat);
                    furnitureShape = defaultBox;
                    fw = fl = fh = 40;
            }

            // Calculate furniture position to match 2D view
            double canvasWidth = designCanvas.getWidth();
            double canvasHeight = designCanvas.getHeight();
            double startX = (canvasWidth - roomW) / 2;
            double startY = (canvasHeight - roomL) / 2;

            // Map 2D coordinates to 3D space
            double px = furniture.getX() - startX - (roomW / 2) + fw / 2;
            double pz = furniture.getY() - startY - (roomL / 2) + fl / 2;

            // Place furniture exactly on the floor surface
            furnitureShape.setTranslateX(px);
            furnitureShape.setTranslateY(roomH / 2 - fh / 2 + 2.5); // +2.5 to place on floor surface
            furnitureShape.setTranslateZ(pz);
            root3D.getChildren().add(furnitureShape);
        }

        // Camera setup with better initial position
        camera3D = new PerspectiveCamera(true);
        camera3D.setTranslateZ(-roomL * 1.5); // Move camera further back
        camera3D.setTranslateY(-roomH / 3); // Adjust height
        camera3D.setTranslateX(roomW / 4); // Move slightly to the right
        camera3D.setNearClip(0.1);
        camera3D.setFarClip(10000.0);
        camera3D.setFieldOfView(45); // Wider field of view

        // Reset rotation and pan with better initial angles
        cameraAngleX = -30; // Look down more
        cameraAngleY = -45; // Look at the corner
        cameraPanX = 0;
        cameraPanY = 0;
        rotateX = new Rotate(cameraAngleX, Rotate.X_AXIS);
        rotateY = new Rotate(cameraAngleY, Rotate.Y_AXIS);
        camera3D.getTransforms().setAll(rotateY, rotateX);

        // Add multiple lights for better visibility
        PointLight light1 = new PointLight(Color.WHITE);
        light1.setTranslateX(0);
        light1.setTranslateY(-roomH / 2);
        light1.setTranslateZ(-roomL / 2);
        root3D.getChildren().add(light1);

        PointLight light2 = new PointLight(Color.WHITE);
        light2.setTranslateX(roomW / 4);
        light2.setTranslateY(-roomH / 3);
        light2.setTranslateZ(-roomL / 4);
        root3D.getChildren().add(light2);

        // Set up SubScene with better background
        room3DSubScene.setRoot(root3D);
        room3DSubScene.setCamera(camera3D);
        room3DSubScene.setFill(Color.rgb(240, 240, 240)); // Lighter background
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
