package Swiftbot;

import java.util.*;
import java.io.*;
import swiftbot.SwiftBotAPI;
import java.awt.image.BufferedImage;

public class SwiftBotShapeDrawer {

    // Constants for minimum and maximum side lengths of shapes
    private static final int MIN_SIDE_LENGTH = 15; // Minimum allowed side length in cm
    private static final int MAX_SIDE_LENGTH = 85; // Maximum allowed side length in cm

    // Variables to track total drawing time, shape count, and log shapes
    private static double totalTime = 0; // Total time taken to draw all shapes
    private static int shapeCount = 0; // Total number of shapes drawn
    private static List<String> shapeLog = new ArrayList<>(); // Log of all shapes drawn

    // Counters for each type of shape
    private static int squareCount = 0, triangleCount = 0, circleCount = 0;

    // Variables to track the largest shape drawn
    private static double largestArea = 0; // Area of the largest shape
    private static String largestShape = ""; // Type of the largest shape

    // SwiftBot API instance
    private static SwiftBotAPI swiftBot;

    // Calibration constants for SwiftBot movement
    private static final int MOVE_TIME_PER_CM = 100; // Time (ms) to move 1 cm (calibrated experimentally)
    private static final int TURN_TIME_90_DEGREES = 643; // Time (ms) to turn 90 degrees (calibrated experimentally)
    private static final int TURN_TIME_120_DEGREES = 666; // Time (ms) to turn 120 degrees (calibrated experimentally)

    // Motor speed adjustments to ensure the SwiftBot moves straight
    private static final int LEFT_MOTOR_SPEED = 50; // Speed for the left motor
    private static final int RIGHT_MOTOR_SPEED = 50; // Speed for the right motor

    // Scale factor to reduce travel distance to minimize drift
    private static final double TRAVEL_SCALE_FACTOR = 0.6; // Reduce travel distance by 40%

    // Static block to initialize the SwiftBot API
    static {
        try {
            swiftBot = new SwiftBotAPI(); // Initialize SwiftBot API
            System.out.println("SwiftBot API initialized successfully.");
        } catch (Exception e) {
            System.out.println("SwiftBot API failed to initialize. Ensure I2C is enabled.");
            System.out.println("Run the following command: sudo raspi-config nonint do_i2c 0");
            System.exit(1); // Exit the program if initialization fails
        }
    }

    // Main method to run the program
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        // Add button listeners for 'Y' and 'X'
        System.out.println("\nPress 'Y' to scan another QR code or 'X' to exit.");
        swiftBot.enableButton(swiftbot.Button.Y, () -> {
            System.out.println("\nRestarting...");
            swiftBot.disableButton(swiftbot.Button.Y); // Disable the 'Y' button after pressing
            // Continue the program to scan a new QR code
        });

        swiftBot.enableButton(swiftbot.Button.X, () -> {
            System.out.println("\nThanks for using SwiftBot Shape Drawer!");
            saveLogToFile(); // Save logs to a file before exiting
            System.exit(0); // Terminate the program
        });

        while (running) {
            displayHeader("SwiftBot Shape Drawing Program");
            System.out.println("Welcome! This program reads QR codes to draw squares and triangles.");
            System.out.println("\nHow to Use:");
            System.out.println("  - Scan a QR Code containing shape information.");
            System.out.println("  - Input Examples:");
            System.out.println("       S-30 (Square of 30 cm)");
            System.out.println("       T-20-30-40 (Triangle with sides 20, 30, 40 cm)");
            System.out.println("  - Multiple shapes can be specified using '&' as a delimiter.");
            System.out.println("       Example: S-30&T-20-30-40");
            System.out.println("\nImportant:");
            System.out.println("  - Side lengths must be between 15 cm - 85 cm.");
            System.out.println("  - You have 60 seconds to display a QR code.\n");
            System.out.println("========================================================================================\n");

            // Start by scanning a QR code
            scanQRCode();

            // Wait for user to press 'Y' or 'X'
            System.out.println("\nPress 'Y' to scan another QR code or 'X' to exit.");
            while (true) {
                // Wait for button press
                try {
                    Thread.sleep(100); // Sleep to avoid busy-waiting
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        scanner.close();
    }

    // Method to scan a QR code
    private static void scanQRCode() {
        // Set a 60-second timer to detect a QR code
        long start_time = System.currentTimeMillis();
        long end_time = start_time + 60000;

        String QRstr = "";
        while (System.currentTimeMillis() < end_time && QRstr.isEmpty()) {
            BufferedImage QR = swiftBot.getQRImage(); // Capture QR code image
            QRstr = swiftBot.decodeQRImage(QR); // Decode QR code
        }

        if (QRstr.isEmpty()) {
            System.out.println("No QR code detected within the given time.");
        } else {
            System.out.println("- QR code detected: " + QRstr);
            processInput(QRstr); // Process the QR code input
        }
    }

    // Method to save logs to a file
    private static void saveLogToFile() {
        String filePath = "SwiftBot_Shape_Log.txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("Drawing Log Summary");
            writer.println("===================");

            if (shapeLog.isEmpty()) {
                writer.println("No shapes drawn yet.");
            } else {
                for (String log : shapeLog) {
                    writer.println(log); // Write each log entry
                }
                writer.println("\n- Largest Shape: " + largestShape + " (Area: " + largestArea + " cm^2)");
                String frequentShape = (squareCount >= triangleCount && squareCount >= circleCount) ? "Square" :
                        (triangleCount >= circleCount) ? "Triangle" : "Circle"; // Determine most frequent shape
                int count = Math.max(squareCount, Math.max(triangleCount, circleCount)); // Get count of most frequent shape
                writer.println("- Most Frequently Drawn Shape: " + frequentShape + " (" + count + " times)");
                writer.printf("- Average Drawing Time: %.2f seconds\n", totalTime / shapeCount);
            }

            System.out.println("Log file saved to: " + new File(filePath).getAbsolutePath());
        } catch (IOException e) {
            System.out.println("Error saving log file: " + e.getMessage());
        }
    }

    // Maximum number of shapes allowed per QR code
    private static final int MAX_SHAPES_PER_QR = 5;

    // Method to process the input from the QR code
    private static void processInput(String input) {
        String[] commands = input.split("&"); // Split input by '&' delimiter

        if (commands.length > MAX_SHAPES_PER_QR) {
            System.out.println("\nERROR: You can specify a maximum of 5 shapes per QR code.");
            return;
        }

        for (int i = 0; i < commands.length; i++) {
            String command = commands[i].trim();
            String[] parts = command.split("-"); // Split command into parts

            if (parts.length < 2) {
                System.out.println("\nERROR: Invalid input detected in command " + (i + 1));
                continue;
            }

            String shapeType = parts[0].toUpperCase(); // Extract shape type

            try {
                if (shapeType.equals("S")) {
                    int side = Integer.parseInt(parts[1]); // Extract side length for square
                    if (validateSideLength(side)) {
                        Shape square = new Square(side); // Create Square object
                        square.draw(); // Draw the square
                    } else {
                        System.out.println("\nERROR: Side length must be between 15 cm - 85 cm.");
                    }
                } else if (shapeType.equals("T")) {
                    if (parts.length != 4) {
                        System.out.println("\nERROR: Invalid Triangle format in command " + (i + 1));
                        continue;
                    }
                    int a = Integer.parseInt(parts[1]); // Extract side a
                    int b = Integer.parseInt(parts[2]); // Extract side b
                    int c = Integer.parseInt(parts[3]); // Extract side c
                    if (validateTriangle(a, b, c)) {
                        Shape triangle = new Triangle(a, b, c); // Create Triangle object
                        triangle.draw(); // Draw the triangle
                    } else {
                        System.out.println("\nERROR: Invalid triangle sides in command " + (i + 1));
                    }
                } else if (shapeType.equals("C")) {
                    int diameter = Integer.parseInt(parts[1]); // Extract diameter for circle
                    if (validateSideLength(diameter)) {
                        Shape circle = new Circle(diameter); // Create Circle object
                        circle.draw(); // Draw the circle
                    } else {
                        System.out.println("\nERROR: Diameter must be between 15 cm - 85 cm.");
                    }
                } else {
                    System.out.println("\nERROR: Invalid Shape Type in command " + (i + 1));
                }

            } catch (NumberFormatException e) {
                System.out.println("\nERROR: Invalid number format in command " + (i + 1));
            }

            // Move backwards to start the next shape
            if (i < commands.length - 1) {
                moveBackwards(15);
            }
        }
    }

    // Method to move the SwiftBot forward
    private static void moveForward(int timeMs) {
        try {
            swiftBot.move(LEFT_MOTOR_SPEED, RIGHT_MOTOR_SPEED, timeMs); // Move forward
        } catch (Exception e) {
            System.out.println("Error moving forward: " + e.getMessage());
        }
    }

    // Method to turn the SwiftBot left
    private static void turnLeft(int timeMs) {
        try {
            swiftBot.move(-50, 50, timeMs); // Turn left
        } catch (Exception e) {
            System.out.println("Error turning left: " + e.getMessage());
        }
    }

    // Method to move the SwiftBot backwards
    private static void moveBackwards(int distanceCm) {
        int moveTime = (int) (distanceCm * MOVE_TIME_PER_CM * TRAVEL_SCALE_FACTOR);
        try {
            swiftBot.move(-LEFT_MOTOR_SPEED, -RIGHT_MOTOR_SPEED, moveTime); // Move backwards
            System.out.println("Moved " + distanceCm + " cm backwards to start next shape.");
        } catch (Exception e) {
            System.out.println("Error moving backwards: " + e.getMessage());
        }
    }

    // Method to validate side length
    private static boolean validateSideLength(int length) {
        return length >= MIN_SIDE_LENGTH && length <= MAX_SIDE_LENGTH;
    }

    // Method to validate triangle sides
    private static boolean validateTriangle(int a, int b, int c) {
        return validateSideLength(a) && validateSideLength(b) && validateSideLength(c) && (a + b > c && a + c > b && b + c > a);
    }

    // Method to blink SwiftBot underlights
    private static void blinkUnderlights() {
        try {
            int[] greenColor = {0, 255, 0}; // Green color for underlights
            int blinkCount = 3; // Number of blinks
            int blinkDuration = 500; // Duration of each blink in milliseconds

            System.out.println("Blinking underlights in green...");

            for (int i = 0; i < blinkCount; i++) {
                swiftBot.fillUnderlights(greenColor); // Turn on underlights
                Thread.sleep(blinkDuration); // Wait
                swiftBot.disableUnderlights(); // Turn off underlights
                Thread.sleep(blinkDuration); // Wait
            }
        } catch (Exception e) {
            System.out.println("Error blinking underlights: " + e.getMessage());
        }
    }

    // Method to log square details
    private static void logSquare(String type, int side, double timeMs) {
        double timeSeconds = timeMs / 1000.0; // Convert time to seconds
        double area = side * side; // Calculate area of square
        shapeLog.add(type + ": " + side + " cm (Time: " + timeSeconds + " seconds)"); // Add to log
        updateLargestShape(type, area); // Update largest shape
        totalTime += timeSeconds; // Update total time
        shapeCount++; // Increment shape count
        squareCount++; // Increment square count
    }

    // Method to log triangle details
    private static void logTriangle(String type, int a, int b, int c, double timeMs) {
        double timeSeconds = timeMs / 1000.0; // Convert time to seconds
        double s = (a + b + c) / 2.0; // Calculate semi-perimeter
        double area = Math.sqrt(s * (s - a) * (s - b) * (s - c)); // Calculate area using Heron's formula
        shapeLog.add(type + ": " + a + ", " + b + ", " + c + " cm (Time: " + timeSeconds + " seconds)"); // Add to log
        updateLargestShape(type, area); // Update largest shape
        totalTime += timeSeconds; // Update total time
        shapeCount++; // Increment shape count
        triangleCount++; // Increment triangle count
    }

    // Method to log circle details
    private static void logCircle(String type, int diameter, double timeMs) {
        double timeSeconds = timeMs / 1000.0; // Convert time to seconds
        double radius = diameter / 2.0; // Calculate radius
        double area = Math.PI * radius * radius; // Calculate area of circle
        shapeLog.add(type + ": " + diameter + " cm diameter (Time: " + timeSeconds + " seconds)"); // Add to log
        updateLargestShape(type, area); // Update largest shape
        totalTime += timeSeconds; // Update total time
        shapeCount++; // Increment shape count
        circleCount++; // Increment circle count
    }

    // Method to update the largest shape drawn
    private static void updateLargestShape(String type, double area) {
        if (area > largestArea) {
            largestArea = area; // Update largest area
            largestShape = type; // Update largest shape type
        }
    }

    // Method to display a header
    private static void displayHeader(String title) {
        System.out.println("\n========================================================================================");
        System.out.println("                                  " + title);
        System.out.println("========================================================================================\n");
    }

    // Method to save and display the drawing log
    private static void saveLog() {
        displayHeader("Drawing Log Summary");

        if (shapeLog.isEmpty()) {
            System.out.println("No shapes drawn yet.");
        } else {
            for (String log : shapeLog) {
                System.out.println(" - " + log); // Display each log entry
            }
            System.out.println("\n- Largest Shape: " + largestShape + " (Area: " + largestArea + " cm^2)");
            String frequentShape = (squareCount >= triangleCount && squareCount >= circleCount) ? "Square" :
                    (triangleCount >= circleCount) ? "Triangle" : "Circle"; // Determine most frequent shape
            int count = Math.max(squareCount, Math.max(triangleCount, circleCount)); // Get count of most frequent shape
            System.out.println("- Most Frequently Drawn Shape: " + frequentShape + " (" + count + " times)");
            System.out.printf("- Average Drawing Time: %.2f seconds\n", totalTime / shapeCount); // Calculate average time
        }
    }

    // Abstract Shape class
    abstract static class Shape {
        protected String type; // Type of shape (Square, Triangle, Circle)
        protected double area; // Area of the shape
        protected double timeMs; // Time taken to draw the shape

        public abstract void draw(); // Abstract method to draw the shape

        // Method to log the shape details
        protected void logShape() {
            if (type.equals("Square")) {
                Square square = (Square) this;
                logSquare(type, square.side, timeMs); // Log square
            } else if (type.equals("Triangle")) {
                Triangle triangle = (Triangle) this;
                logTriangle(type, triangle.a, triangle.b, triangle.c, timeMs); // Log triangle
            } else if (type.equals("Circle")) {
                Circle circle = (Circle) this;
                logCircle(type, circle.diameter, timeMs); // Log circle
            }
        }
    }

    // Square class
    static class Square extends Shape {
        private int side; // Side length of the square

        public Square(int side) {
            this.side = side;
            this.type = "Square";
            this.area = side * side; // Calculate area
        }

        @Override
        public void draw() {
            int moveTime = (int) (side * MOVE_TIME_PER_CM * TRAVEL_SCALE_FACTOR); // Calculate move time
            int turnTime = TURN_TIME_90_DEGREES; // Time to turn 90 degrees
            int widerLastTurnTime = (int) (turnTime * 1.15); // Increase last turn time by 15%

            System.out.println("\nDrawing Square: " + side + " cm");
            System.out.println("Processing... Drawing in progress.");

            int lastProgress = -1; // Track progress percentage

            for (int i = 0; i < 4; i++) {
                moveForward(moveTime); // Move forward
                if (i == 3) {
                    turnLeft(widerLastTurnTime); // Wider last turn
                    System.out.println("Adjusting last turn to align with starting position...");
                } else {
                    turnLeft(turnTime); // Normal turn
                }

                int progress = (i + 1) * 25; // Calculate progress
                if (progress != lastProgress && (progress % 10 == 0 || progress == 100)) {
                    System.out.println(progress + "% Complete..."); // Display progress
                    lastProgress = progress;
                }
            }

            System.out.println("Drawing complete!");
            blinkUnderlights(); // Blink underlights to indicate completion

            this.timeMs = moveTime * 4; // Calculate total time
            logShape(); // Log the square
            saveLog(); // Save the log
        }
    }

    // Triangle class
    static class Triangle extends Shape {
        private int a, b, c; // Sides of the triangle

        public Triangle(int a, int b, int c) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.type = "Triangle";
            double s = (a + b + c) / 2.0; // Calculate semi-perimeter
            this.area = Math.sqrt(s * (s - a) * (s - b) * (s - c)); // Calculate area using Heron's formula
        }

        @Override
        public void draw() {
            int moveTimeA = (int) (a * MOVE_TIME_PER_CM * TRAVEL_SCALE_FACTOR); // Calculate move time for side a
            int moveTimeB = (int) (b * MOVE_TIME_PER_CM * TRAVEL_SCALE_FACTOR); // Calculate move time for side b
            int moveTimeC = (int) (c * MOVE_TIME_PER_CM * TRAVEL_SCALE_FACTOR); // Calculate move time for side c
            int turnTime = TURN_TIME_120_DEGREES; // Time to turn 120 degrees
            int widerTurnTimeSecond = (int) (turnTime * 1.5); // Increase second turn time by 50%
            int slightlyWiderTurnTimeLast = (int) (turnTime * 1.6); // Increase last turn time by 20%

            System.out.println("\nDrawing Triangle: " + a + ", " + b + ", " + c + " cm");
            System.out.println("Processing... Drawing in progress.");

            int lastProgress = -1; // Track progress percentage

            moveForward(moveTimeA); // Move for side a
            turnLeft(turnTime); // First turn
            int progress = 33;
            if (progress != lastProgress) {
                System.out.println(progress + "% Complete...");
                lastProgress = progress;
            }

            moveForward(moveTimeB); // Move for side b
            turnLeft(widerTurnTimeSecond); // Second turn (wider)
            progress = 66;
            if (progress != lastProgress) {
                System.out.println(progress + "% Complete...");
                lastProgress = progress;
            }

            moveForward(moveTimeC); // Move for side c
            turnLeft(slightlyWiderTurnTimeLast); // Last turn (slightly wider)
            progress = 100;
            if (progress != lastProgress) {
                System.out.println(progress + "% Complete...");
                lastProgress = progress;
            }

            System.out.println("Drawing complete!");
            blinkUnderlights(); // Blink underlights to indicate completion

            this.timeMs = moveTimeA + moveTimeB + moveTimeC; // Calculate total time
            logShape(); // Log the triangle
            saveLog(); // Save the log

            // Calculate and display triangle angles
            double angleA = calculateAngle(b, c, a);
            double angleB = calculateAngle(a, c, b);
            double angleC = calculateAngle(a, b, c);
            System.out.println("Triangle Angles: A = " + angleA + "°, B = " + angleB + "°, C = " + angleC + "°");
        }

        // Method to calculate an angle of the triangle using the Law of Cosines
        private double calculateAngle(double a, double b, double c) {
            return Math.toDegrees(Math.acos((b * b + c * c - a * a) / (2 * b * c)));
        }
    }

    // Circle class
    static class Circle extends Shape {
        private int diameter; // Diameter of the circle

        public Circle(int diameter) {
            this.diameter = diameter;
            this.type = "Circle";
            double radius = diameter / 2.0; // Calculate radius
            this.area = Math.PI * radius * radius; // Calculate area
        }

        @Override
        public void draw() {
            double radius = diameter / 2.0; // Calculate radius
            double circumference = 2 * Math.PI * radius; // Calculate circumference
            int moveTime = (int) (circumference * MOVE_TIME_PER_CM * TRAVEL_SCALE_FACTOR); // Calculate move time

            System.out.println("\nDrawing Circle: " + diameter + " cm diameter");
            System.out.println("Processing... Drawing in progress.");

            int steps = 360; // Number of steps to approximate a circle
            int stepTime = moveTime / steps; // Time for each step
            int turnTimePerStep = 10; // Time to turn slightly for each step

            int lastProgress = -1; // Track progress percentage

            for (int i = 0; i < steps; i++) {
                moveForward(stepTime); // Move forward
                turnLeft(turnTimePerStep); // Turn slightly

                int progress = (int) ((i + 1) * 100.0 / steps); // Calculate progress
                if (progress != lastProgress && (progress % 10 == 0 || progress == 100)) {
                    System.out.println(progress + "% Complete..."); // Display progress
                    lastProgress = progress;
                }
            }

            System.out.println("Drawing complete!");
            blinkUnderlights(); // Blink underlights to indicate completion

            this.timeMs = moveTime; // Calculate total time
            logShape(); // Log the circle
            saveLog(); // Save the log
        }
    }
}