package com.evolvlabs.UI;

import java.io.*;
import java.util.Properties;

/**
 * @author : Santiago Arellano
 * @date : 21st-Apr-2025
 * @description : Main class for the ViewMyMeetings console application.
 * This class initializes the ConsoleUI and starts the application.
 */
public class ViewMyMeetingsConsoleApp {


    /**
     * <p>
     * The main entry point for the ViewMyMeetings Console Application. This method initializes and
     * starts the application's command-line interface.
     * </p>
     *
     * <p>
     * The application attempts to load configuration in the following order: 1. From an external
     * configuration file if specified in command line arguments 2. From the classpath resource
     * 'clientConfiguration.properties' if no external config is found 3. Falls back to default
     * settings if no configuration files are available
     * </p>
     *
     * <p>
     * The application handles various error scenarios gracefully, including missing configuration
     * files and unexpected runtime errors, ensuring the application either starts with available
     * settings or provides appropriate error messages.
     * </p>
     *
     * @param args Command line arguments. If provided, the first argument should be the path to an
     *             external configuration file. If no arguments are provided, the application will
     *             attempt to load configuration from the classpath.
     * @throws FileNotFoundException If neither the external configuration file nor the default
     *                               configuration file in the classpath can be found
     * @throws IOException           If there are issues reading the configuration files
     * @throws Exception             For any other unexpected runtime errors that may occur during
     *                               execution
     */
    public static void main(String[] args) {
        System.out.println("Starting ViewMyMeetings Console Application...");

        // Load configuration
        Properties clientConfig = new Properties();
        String configPath = args.length > 0 ? args[0] : null;

        try {
            // Try to load external configuration if provided
            if (configPath != null) {
                File externalConfig = new File(configPath);
                if (externalConfig.exists()) {
                    try (FileInputStream fis = new FileInputStream(externalConfig)) {
                        clientConfig.load(fis);
                        System.out.println("Loaded configuration from: " + configPath);
                    }
                } else {
                    System.err.println("Warning: Specified config file not found: " + configPath);
                    System.out.println("Falling back to default configuration...");
                }
            }

            // If no external config was loaded, try loading from classpath
            if (clientConfig.isEmpty()) {
                try (InputStream is = ViewMyMeetingsConsoleApp.class.getClassLoader()
                        .getResourceAsStream("clientConfiguration.properties")) {
                    if (is != null) {
                        clientConfig.load(is);
                        System.out.println("Loaded configuration from classpath");
                    } else {
                        throw new FileNotFoundException("Could not find default configuration in classpath");
                    }
                }
            }

            // Create and start the console UI with configuration
            ConsoleUI ui = new ConsoleUI(clientConfig);
            ui.start();

        } catch (IOException e) {
            System.err.println("Error loading configuration: " + e.getMessage());
            System.err.println("Starting with default settings...");
            // Start UI with default settings
            ConsoleUI ui = new ConsoleUI();
            try {
                ui.start();
            } catch (Exception ex) {
                System.err.println("An unexpected error occurred: " + ex.getMessage());
                ex.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("ViewMyMeetings Console Application terminated.");
    }
}