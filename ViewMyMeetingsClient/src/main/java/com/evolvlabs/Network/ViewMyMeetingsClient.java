package com.evolvlabs.Network;

import com.evolvlabs.DataModel.CommunicationProtocol;
import com.evolvlabs.DataModel.Employee;
import com.evolvlabs.DataModel.Meeting;
import com.evolvlabs.SerializationEngine.SerialCompression;
import com.evolvlabs.SerializationEngine.ToJsonSerializer;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.evolvlabs.DataModel.CommunicationProtocol;
import com.evolvlabs.DataModel.Employee;
import com.evolvlabs.DataModel.Meeting;
import com.evolvlabs.SerializationEngine.SerialCompression;
import com.evolvlabs.SerializationEngine.ToJsonSerializer;
import org.msgpack.core.annotations.Nullable;

/**
 * @author : Santiago Arellano
 * @date : 21st-Apr-2025
 * @description : The client implementation for ViewMyMeetings application. This class handles the
 * communication with the server, including authentication, meeting management, and receiving
 * updates from the server.
 */
public class ViewMyMeetingsClient {
    
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;
    private static final String CONFIG_FILE = "clientConfiguration.properties";
    private static String LOCAL_MEETINGS_FILE = "clientMeetings.json";

    private String host;
    private int port;
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private boolean connected = false;
    private boolean authenticated = false;
    private Employee currentEmployee;
    private String clientPassword;

    // Secondary connection for receiving updates from the server
    private Socket updateSocket;
    private DataInputStream updateInputStream;
    private final ExecutorService updateListenerExecutor;
    private boolean updateListenerRunning = false;

    // Client-side storage for meetings
    private List<Meeting> localMeetings;
    private List<Meeting> localOnErrorMeetings;

    // Handlers for server updates
    private Consumer<Meeting> meetingUpdateHandler;
    private Consumer<Meeting> meetingDeletionHandler;


    /**
     * Constructor with default host and port
     */
    public ViewMyMeetingsClient() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    /**
     * Constructor with specified host and port
     *
     * @param host The server host
     * @param port The server port
     */
    public ViewMyMeetingsClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.updateListenerExecutor = Executors.newSingleThreadExecutor();
        this.localMeetings = new ArrayList<>();
        this.localOnErrorMeetings = new LinkedList<>();
        loadClientCredentials(null);
        loadLocalMeetings();
        registerShutdownHook();
    }

    /**
     * Constructs a ViewMyMeetingsClient with configuration from Properties object. This constructor
     * initializes the client using the provided configuration properties for server connection and
     * client credentials.
     *
     * @param config Properties object containing client configuration with the following keys: 
     *               
     *               -server.host: The host address of the server (defaults to "0.0.0.0") -
     *               server.port: The port number of the server (defaults to "8080") - client.name:
     *               The name of the client - client.id: The unique identifier of the client -
     *               client.password: The authentication password for the client -
     *               client.storage.location: The file path for local meeting storage
     * @throws NumberFormatException if the port number in config is not a valid integer
     * @see Properties
     * @see #loadClientCredentials(Properties)
     * @see #loadLocalMeetings()
     * @see #registerShutdownHook()
     */
    public ViewMyMeetingsClient(Properties config) {
        this.host = config.getProperty("server.host", "0.0.0.0");
        this.port = Integer.parseInt(config.getProperty("server.port", "8080"));
        this.updateListenerExecutor = Executors.newSingleThreadExecutor();
        this.localMeetings = new ArrayList<>();
        this.localOnErrorMeetings = new LinkedList<>();
        loadClientCredentials(config);
        loadLocalMeetings();
        registerShutdownHook();
    }

    /**
     * Register a shutdown hook to save meetings when the JVM is shutting down
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook triggered - saving meetings to file...");
            if (localMeetings != null && !localMeetings.isEmpty()) {
                saveLocalMeetings();
            }
        }));
        System.out.println("Shutdown hook registered to save meetings on JVM shutdown");
    }


    /**
     * Loads meetings from the local storage file into memory.
     *
     * <p>This method attempts to read and deserialize meeting data from a JSON file specified by
     * LOCAL_MEETINGS_FILE. If the file exists, it reads the content using a FileReader and
     * deserializes the JSON content into Meeting objects using ToJsonSerializer.</p>
     *
     * <p>If the file doesn't exist, it creates a new empty file and initializes an empty meetings
     * list. This ensures that the application always has a valid storage file to work with.</p>
     *
     * <p>The method uses a buffer-based reading approach for efficient file processing and handles
     * all potential I/O operations within try-with-resources blocks to ensure proper resource
     * cleanup.</p>
     *
     * @throws IOException If there are errors reading from or creating the storage file. These
     *                     exceptions are caught and logged internally.
     * @see Meeting
     * @see ToJsonSerializer#deserializeMultipleMeetings(String)
     * @see #LOCAL_MEETINGS_FILE
     */
    private void loadLocalMeetings() {
        File file = new File(LOCAL_MEETINGS_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                StringBuilder jsonContent = new StringBuilder();
                char[] buffer = new char[1024];
                int charsRead;
                while ((charsRead = reader.read(buffer)) != -1) {
                    jsonContent.append(buffer, 0, charsRead);
                }

                List<Meeting> loadedMeetings = ToJsonSerializer.deserializeMultipleMeetings(jsonContent.toString());
                if (loadedMeetings != null) {
                    localMeetings.addAll(loadedMeetings);
                    System.out.println("Loaded " + localMeetings.size() + " meetings from " + LOCAL_MEETINGS_FILE);
                }
            } catch (IOException e) {
                System.err.println("Error loading local meetings: " + e.getMessage());
            }
        } else {
            System.out.println("No local meetings file found. Starting with empty meetings list.");
            try {
                if (file.createNewFile()) {
                    System.out.println("Created new local meetings file: " + LOCAL_MEETINGS_FILE);
                }
            } catch (IOException e) {
                System.err.println("Error creating local meetings file: " + e.getMessage());
            }
        }
    }


    /**
     * Saves the local meetings to a JSON file in a thread-safe manner.
     *
     * <p>This method is synchronized to prevent concurrent modifications to the local meetings
     * file
     * while saving. It handles the serialization of meetings to JSON format and writes them to the
     * configured local storage location.</p>
     *
     * <p>If the local meetings list is null or empty, the method will log a message and return
     * without performing any file operations. This prevents unnecessary file access and maintains
     * the existing file state.</p>
     *
     * <p>The method uses a FileWriter with try-with-resources to ensure proper resource cleanup.
     * The meetings are serialized using the ToJsonSerializer utility class, which converts the
     * Meeting objects into a JSON string representation.</p>
     *
     * @throws IOException If there is an error writing to the file or if the file cannot be
     *                     created
     * @throws Exception   If there is an unexpected error during the serialization process
     * @see ToJsonSerializer#serializeMultipleMeetings(List)
     * @see Meeting
     */
    private synchronized void saveLocalMeetings() {
        if (localMeetings == null || localMeetings.isEmpty()) {
            System.out.println("No meetings to save.");
            return;
        }

        try (FileWriter writer = new FileWriter(LOCAL_MEETINGS_FILE)) {
            String meetingsJson = ToJsonSerializer.serializeMultipleMeetings(localMeetings);
            writer.write(meetingsJson);
            System.out.println("Saved " + localMeetings.size() + " meetings to " + LOCAL_MEETINGS_FILE);
        } catch (IOException e) {
            System.err.println("Error saving local meetings: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error during meeting serialization: " + e.getMessage());
        }
    }


    /**
     * Loads the client credentials from either provided Properties object or default configuration
     * file.
     *
     * <p>This method attempts to load client credentials (name, ID, password) and local storage
     * location
     * either from a provided Properties object or from the default configuration file
     * (clientConfiguration.properties). If properties parameter is null, it will attempt to load
     * from the default file.</p>
     *
     * <p>When loading from default configuration, the method looks for a file named
     * "clientConfiguration.properties"
     * in the classpath. If the file is not found or cannot be read, appropriate error messages are
     * logged.</p>
     *
     * <p>The method expects the following properties to be present:</p>
     * <ul>
     *   <li>client.name - The full name of the client</li>
     *   <li>client.id - The unique identifier for the client</li>
     *   <li>client.password - The authentication password</li>
     *   <li>client.storage.location - The file path for storing local meetings</li>
     * </ul>
     *
     * <p>If any required properties are missing, the client credentials will not be set and an error
     * message will be logged.</p>
     *
     * @param properties Properties object containing client configuration, or null to load from
     *                   default file
     * @throws IOException If an error occurs while reading the configuration file
     * @see Properties
     * @see Employee
     */
    private void loadClientCredentials(Properties properties) {
        if (properties == null) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                if (input == null) {
                    System.err.println("Unable to find " + CONFIG_FILE);
                    return;
                }

                Properties prop = new Properties();
                prop.load(input);

                // Load client credentials
                String clientName = prop.getProperty("client.name");
                String clientId = prop.getProperty("client.id");
                this.clientPassword = prop.getProperty("client.password");
                LOCAL_MEETINGS_FILE = prop.getProperty("client.storage.location");
                if (clientName != null && clientId != null && clientPassword != null) {
                    this.currentEmployee = new Employee(clientName, clientId);
                    System.out.println("Loaded client credentials for: " + clientName + " (ID: " + clientId + ")");
                } else {
                    System.err.println("Missing client credentials in configuration file");
                }
            } catch (IOException e) {
                System.err.println("Error loading client credentials: " + e.getMessage());
            }
        } else {
            String clientName = properties.getProperty("client.name");
            String clientId = properties.getProperty("client.id");
            this.clientPassword = properties.getProperty("client.password");
            LOCAL_MEETINGS_FILE = properties.getProperty("client.storage.location");
            if (clientName != null && clientId != null && clientPassword != null) {
                this.currentEmployee = new Employee(clientName, clientId);
                System.out.println("Loaded client credentials for: " + clientName + " (ID: " + clientId + ")");
            } else {
                System.err.println("Missing client credentials in configuration file");
            }
        }

    }


    public boolean connect() {
        try {
            socket = new Socket(host, port);
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
            connected = true;
            System.out.println("Connected to server at " + host + ":" + port);
            return true;
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
            return false;
        }
    }

    /**
     * Authenticate with the server using the loaded credentials
     *
     * @return true if authentication was successful, false otherwise
     */
    public boolean authenticate() {
        if (currentEmployee == null || clientPassword == null) {
            System.err.println("No client credentials loaded");
            return false;
        }
        return authenticate(currentEmployee, clientPassword);
    }

    /**
     * Authenticate with the server
     *
     * @param employee The employee to authenticate
     * @param password The password for authentication
     * @return true if authentication was successful, false otherwise
     */
    public boolean authenticate(Employee employee, String password) {
        if (!connected) {
            System.err.println("Not connected to server");
            return false;
        }

        try {
            // Create a temporary meeting to hold the employee information and password
            // We use the meeting topic field to store the password
            Meeting tempMeeting = new Meeting();
            tempMeeting.setOrganizer(employee);
            tempMeeting.setMeetingTopic(password);

            // Send the authentication request
            sendMessage(CommunicationProtocol.POST_CLIENTSIDE_AUTH_REQUEST,
                        ToJsonSerializer.serializeSingleMeeting(tempMeeting));

            // Wait for the response
            String protocolStr = inputStream.readUTF();
            CommunicationProtocol protocol = CommunicationProtocol.valueOf(protocolStr);

            if (protocol == CommunicationProtocol.POST_SERVERSIDE_AUTH_RESPONSE) {
                byte[] compressedMessage = new byte[inputStream.readInt()];
                inputStream.readFully(compressedMessage);
                String response = SerialCompression.unpackSerializedStringFromByteArray(compressedMessage);

                try {
                    // The response should be the port for the update connection 
                    int updatePort = Integer.parseInt(response);
                    System.out.println(updatePort);

                    // Connect to the update port opening up a connection for change communication
                    connectToUpdatePort(updatePort);

                    // If we got here, authentication was successful as it has returned a port 
                    authenticated = true;
                    currentEmployee = employee;
                    System.out.println("Authentication successful for: " + employee.getEmployeeFullName());
                    return true;
                } catch (NumberFormatException e) {
                    // If the response is not a port number, authentication failed given that the
                    // method should always return a port for the listening server
                    System.err.println("Authentication failed: " + response);
                    return false;
                }
            } else {
                System.err.println("Unexpected response protocol: " + protocol);
                return false;
            }
        } catch (IOException e) {
            System.err.println("Error during authentication: " + e.getMessage());
            return false;
        }
    }

    
    /**
     * Connect to the update port for receiving server updates
     *
     * @param updatePort The port to connect to
     * @throws IOException If an I/O error occurs
     */
    private void connectToUpdatePort(int updatePort) throws IOException {
        updateSocket = new Socket(host, updatePort);
        updateInputStream = new DataInputStream(updateSocket.getInputStream());

        // Start the update listener
        updateListenerRunning = true;
        updateListenerExecutor.submit(this::listenForUpdates);

        System.out.println("Connected to update port: " + updatePort);
    }


    /**
     * Continuously listens for updates from the server through the update socket connection.
     *
     * <p>This method runs in a separate thread and continuously monitors the update socket for
     * incoming messages from the server. It will continue running until either the update listener
     * is explicitly stopped or the socket is closed.</p>
     *
     * <p>For each received message, the method performs the following steps:</p>
     * <ol>
     *   <li>Reads the communication protocol identifier from the input stream</li>
     *   <li>Reads and decompresses the message payload</li>
     *   <li>Delegates the processing of the update to the {@link #processUpdate} method</li>
     * </ol>
     *
     * <p>The method handles various types of updates including meeting conflicts, deletions, and
     * conflict resolutions. All received messages are expected to be compressed using the
     * application's defined compression logic.</p>
     *
     * @throws IOException If there is an error reading from the update socket. These exceptions are
     *                     caught and logged internally when the update listener is still running
     * @see CommunicationProtocol
     * @see SerialCompression#unpackSerializedStringFromByteArray(byte[])
     * @see #processUpdate(CommunicationProtocol, String)
     */
    private void listenForUpdates() {
        try {
            while (updateListenerRunning && !updateSocket.isClosed()) {
                // Read the protocol coming from the server, this is always the first step.
                String protocolStr = updateInputStream.readUTF();
                CommunicationProtocol protocol = CommunicationProtocol.valueOf(protocolStr);

                // Read the message coming afterwards, this message often contains either a 
                // boolean or a serialized message using the compression logic defined for the app!
                byte[] compressedMessage = new byte[updateInputStream.readInt()];
                updateInputStream.readFully(compressedMessage);
                String message = SerialCompression.unpackSerializedStringFromByteArray(compressedMessage);

                // Process the update based on the protocol, this delegates to another method 
                // internally, this could've used the Command pattern, but it is so much I think 
                // that would be too much refactoring.
                processUpdate(protocol, message);
            }
        } catch (IOException e) {
            if (updateListenerRunning) {
                System.err.println("Error listening for updates: " + e.getMessage());
            }
        }
    }


    /**
     * Processes updates received from the server through the update socket connection.
     *
     * <p>This method handles different types of server-side updates based on the received
     * protocol.
     * The updates can be one of three types:</p>
     *
     * <p>1. Meeting Conflict Updates: When a meeting conflicts with another, it is added to the
     * localOnErrorMeetings list and the registered update handler is notified.</p>
     *
     * <p>2. Meeting Deletion Updates: When a meeting is deleted on the server, it is removed from
     * both local storage and the conflict list. The registered deletion handler is notified.</p>
     *
     * <p>3. Meeting Conflict Resolution Updates: When a meeting conflict is resolved, the meeting
     * is removed from the conflict list and the update handler is notified.</p>
     *
     * @param protocol The {@link CommunicationProtocol} indicating the type of update received
     * @param message  The serialized meeting data as a JSON string
     * @see Meeting
     * @see CommunicationProtocol
     * @see ToJsonSerializer#deserializeSingleMeeting(String)
     */
    private void processUpdate(CommunicationProtocol protocol, String message) {
        switch (protocol) {
            case PUSH_SERVERSIDE_MEETING_CONFLICT_UPDATE_REQUEST:

                Meeting conflictMeeting = ToJsonSerializer.deserializeSingleMeeting(message);
                if (conflictMeeting != null) {
                    // Add the conflicting meeting to the linked list
                    if (!this.localOnErrorMeetings.contains(conflictMeeting)) {
                        this.localOnErrorMeetings.add(conflictMeeting);
                    }

                    // Notify the update handler if registered
                    if (meetingUpdateHandler != null) {
                        meetingUpdateHandler.accept(conflictMeeting);
                    }

                    System.out.println("Added conflicting meeting to linked list: " + conflictMeeting.getMeetingTopic());
                }
                break;

            case PUSH_SERVERSIDE_MEETING_DELETION_NOTIFICATION:
                Meeting deletedMeeting = ToJsonSerializer.deserializeSingleMeeting(message);
                if (deletedMeeting != null) {
                    // Remove the meeting from local storage
                    removeMeetingFromLocalStorage(deletedMeeting);

                    // Also remove from the conflict linked list if present
                    if (localOnErrorMeetings != null) {
                        localOnErrorMeetings.removeIf(m ->
                                                              m.getOrganizer().getEmployeeID().equals(deletedMeeting.getOrganizer().getEmployeeID()) &&
                                                                      m.getMeetingTopic().equals(deletedMeeting.getMeetingTopic())
                                                     );
                    }

                    // Notify the deletion handler if registered
                    if (meetingDeletionHandler != null) {
                        meetingDeletionHandler.accept(deletedMeeting);
                    }

                    System.out.println("Processed meeting deletion notification: " + deletedMeeting.getMeetingTopic());
                }
                break;
            case PUSH_SERVERSIDE_MEETING_CONFLICT_RESOLUTION_NOTIFICATION:
                Meeting resolvedMeeting = ToJsonSerializer.deserializeSingleMeeting(message);
                if (resolvedMeeting != null && localOnErrorMeetings != null) {
                    // Remove the meeting from the conflict list
                    localOnErrorMeetings.removeIf(m ->
                                                          m.getOrganizer().getEmployeeID().equals(resolvedMeeting.getOrganizer().getEmployeeID()) &&
                                                                  m.getMeetingTopic().equals(resolvedMeeting.getMeetingTopic())
                                                 );

                    // Also notify the UI that conflicts have been resolved
                    if (meetingUpdateHandler != null) {
                        meetingUpdateHandler.accept(resolvedMeeting);
                    }
                }
                break;
            default:
                System.err.println("Unknown update protocol: " + protocol);
        }
    }


    /**
     * Creates a new meeting on both the server and local storage.
     *
     * <p>This method attempts to create a new meeting by sending a creation request to the server.
     * The
     * method requires the client to be authenticated before proceeding. If authentication is not
     * present, the method will fail immediately.</p>
     *
     * <p>The creation process involves the following steps:</p>
     * <ol>
     *   <li>Verifies client authentication</li>
     *   <li>Serializes and sends the meeting data to the server</li>
     *   <li>Waits for server response</li>
     *   <li>If successful, stores the meeting in local storage</li>
     * </ol>
     *
     * <p>If the meeting is successfully created on the server, it will be added to the local meetings
     * list and persisted to local storage. This ensures consistency between server and client
     * state.</p>
     *
     * @param meeting The {@link Meeting} object containing all the meeting details to be created.
     *                Must not be null and should contain valid meeting data including organizer,
     *                topic, and time information.
     * @return boolean indicating whether the meeting was successfully created (true) or not (false)
     * @throws IllegalStateException if the client is not authenticated
     * @throws IOException           if there is an error in communication with the server or in
     *                               local storage operations
     * @see Meeting
     * @see #saveLocalMeetings()
     * @see CommunicationProtocol
     */
    public boolean createMeeting(Meeting meeting) {
        if (!authenticated) {
            System.err.println("Not authenticated");
            return false;
        }

        try {
            // Send the meeting creation request
            sendMessage(CommunicationProtocol.PUSH_CLIENTSIDE_MEETING_CREATION_REQUEST,
                        ToJsonSerializer.serializeSingleMeeting(meeting));

            // Wait for the response
            String protocolStr = inputStream.readUTF();
            CommunicationProtocol protocol = CommunicationProtocol.valueOf(protocolStr);

            if (protocol == CommunicationProtocol.PUSH_SERVERSIDE_MEETING_CREATION_RESPONSE) {
                byte[] compressedMessage = new byte[inputStream.readInt()];
                inputStream.readFully(compressedMessage);
                String response = SerialCompression.unpackSerializedStringFromByteArray(compressedMessage);

                boolean success = Boolean.parseBoolean(response);

                // If the meeting was created successfully, store it locally
                if (success) {
                    localMeetings.add(meeting);
                    saveLocalMeetings();
                }

                return success;
            } else {
                System.err.println("Unexpected response protocol: " + protocol);
                return false;
            }
        } catch (IOException e) {
            System.err.println("Error creating meeting: " + e.getMessage());
            return false;
        }
    }


    /**
     * Updates an existing meeting on both the server and in local storage.
     *
     * <p>This method handles the update of an existing meeting by communicating with the server
     * and maintaining consistency in local storage. The update process requires client
     * authentication and involves several steps:</p>
     *
     * <p>1. Verification of client authentication status</p>
     * <p>2. Sending the update request to the server</p>
     * <p>3. Processing the server's response</p>
     * <p>4. If successful, updating the local storage to reflect the changes</p>
     *
     * <p>The method ensures that both the server and client maintain consistent states by:</p>
     * <p>- Removing any existing versions of the meeting from local storage</p>
     * <p>- Removing the meeting from the conflict list if present</p>
     * <p>- Adding the updated version to local storage</p>
     * <p>- Persisting the changes to the local storage file</p>
     *
     * @param meeting The {@link Meeting} object containing the updated meeting information. Must
     *                not be null and should contain valid meeting data including organizer, topic,
     *                and updated information.
     * @return boolean indicating whether the update was successful (true) or failed (false)
     * @throws IllegalStateException if the client is not authenticated
     * @throws IOException           if there is an error in communication with the server or in
     *                               local storage operations
     * @see Meeting
     * @see #saveLocalMeetings()
     * @see CommunicationProtocol
     */
    public boolean updateMeeting(Meeting meeting) {
        if (!authenticated) {
            System.err.println("Not authenticated");
            return false;
        }

        try {
            // Send the meeting update request
            sendMessage(CommunicationProtocol.PUSH_CLIENTSIDE_MEETING_UPDATE_REQUEST,
                        ToJsonSerializer.serializeSingleMeeting(meeting));

            // Wait for the response
            String protocolStr = inputStream.readUTF();
            CommunicationProtocol protocol = CommunicationProtocol.valueOf(protocolStr);

            if (protocol == CommunicationProtocol.PUSH_SERVERSIDE_MEETING_UPDATE_RESPONSE) {
                byte[] compressedMessage = new byte[inputStream.readInt()];
                inputStream.readFully(compressedMessage);
                String response = SerialCompression.unpackSerializedStringFromByteArray(compressedMessage);

                boolean success = Boolean.parseBoolean(response);

                // If the meeting was updated successfully, update it locally
                if (success) {
                    // First, remove all versions of this meeting by topic and organizer
                    localMeetings.removeIf(m ->
                                                   m.getOrganizer().getEmployeeID().equals(meeting.getOrganizer().getEmployeeID()) &&
                                                           m.getMeetingTopic().equals(meeting.getMeetingTopic())
                                          );

                    // Also remove from conflict list if present
                    if (localOnErrorMeetings != null) {
                        localOnErrorMeetings.removeIf(m ->
                                                              m.getOrganizer().getEmployeeID().equals(meeting.getOrganizer().getEmployeeID()) &&
                                                                      m.getMeetingTopic().equals(meeting.getMeetingTopic())
                                                     );
                    }

                    // Then add the updated meeting
                    localMeetings.add(meeting);
                    saveLocalMeetings();
                }

                return success;
            } else {
                System.err.println("Unexpected response protocol: " + protocol);
                return false;
            }
        } catch (IOException e) {
            System.err.println("Error updating meeting: " + e.getMessage());
            return false;
        }
    }


    /**
     * Deletes a meeting from both the server and local storage.
     *
     * <p>This method handles the deletion of an existing meeting by communicating with the server
     * and maintaining consistency in local storage. The deletion process requires client
     * authentication and follows these steps:</p>
     *
     * <p>1. Verifies that the client is authenticated before proceeding with the deletion
     * request</p>
     * <p>2. Sends a deletion request to the server with the meeting information</p>
     * <p>3. Waits for and processes the server's response</p>
     * <p>4. If the server confirms successful deletion, removes the meeting from local storage</p>
     *
     * <p>The method ensures data consistency between the server and client by only removing the
     * meeting from local storage after receiving confirmation of successful deletion from the
     * server.</p>
     *
     * @param meeting The {@link Meeting} object to be deleted. Must not be null and should contain
     *                valid meeting data including organizer and topic information.
     * @return boolean indicating whether the deletion was successful (true) or failed (false)
     * @throws IllegalStateException if the client is not authenticated
     * @throws IOException           if there is an error in communication with the server
     * @see Meeting
     * @see #removeMeetingFromLocalStorage(Meeting)
     * @see CommunicationProtocol
     */
    public boolean deleteMeeting(Meeting meeting) {
        if (!authenticated) {
            System.err.println("Not authenticated");
            return false;
        }

        try {
            // Send the meeting deletion request
            sendMessage(CommunicationProtocol.PUSH_CLIENTSIDE_MEETING_DELETION_REQUEST,
                        ToJsonSerializer.serializeSingleMeeting(meeting));

            // Wait for the response
            String protocolStr = inputStream.readUTF();
            CommunicationProtocol protocol = CommunicationProtocol.valueOf(protocolStr);

            if (protocol == CommunicationProtocol.PUSH_SERVERSIDE_MEETING_DELETION_RESPONSE) {
                byte[] compressedMessage = new byte[inputStream.readInt()];
                inputStream.readFully(compressedMessage);
                String response = SerialCompression.unpackSerializedStringFromByteArray(compressedMessage);

                boolean success = Boolean.parseBoolean(response);

                // If the meeting was deleted successfully, remove it from local storage
                if (success) {
                    removeMeetingFromLocalStorage(meeting);
                }


                return success;
            } else {
                System.err.println("Unexpected response protocol: " + protocol);
                return false;
            }
        } catch (IOException e) {
            System.err.println("Error deleting meeting: " + e.getMessage());
            return false;
        }
    }

    
    private void removeMeetingFromLocalStorage(Meeting meeting) {
        boolean removed = false;

        // Find and remove the meeting from the local list
        for (int i = 0; i < localMeetings.size(); i++) {
            Meeting localMeeting = localMeetings.get(i);
            // Identify the meeting by organizer ID and topic, this is because we are locking the
            // update logic to being only the dates, I should've modeled an ID, but I did not and
            // that hurt the design a bit.
            if (localMeeting.getOrganizer().getEmployeeID().equals(meeting.getOrganizer().getEmployeeID()) &&
                    localMeeting.getMeetingTopic().equals(meeting.getMeetingTopic())) {
                localMeetings.remove(i);
                removed = true;
                break;
            }
        }

        if (removed) {
            saveLocalMeetings();
            System.out.println("Removed meeting from local storage: " + meeting.getMeetingTopic());
        }
    }

    /**
     * Get meetings for the current employee
     *
     * @return A list of meetings, or null if an error occurred
     */
    public List<Meeting> getMeetings() {
        if (!authenticated) {
            System.err.println("Not authenticated");
            return null;
        }

        try {
            // Send the meeting information request
            sendMessage(CommunicationProtocol.GET_SERVERSIDE_MEETING_INFORMATION_BY_ID_REQUEST,
                        currentEmployee.getEmployeeID());

            // Wait for the response
            String protocolStr = inputStream.readUTF();
            CommunicationProtocol protocol = CommunicationProtocol.valueOf(protocolStr);

            if (protocol == CommunicationProtocol.GET_SERVERSIDE_MEETING_INFORMATION_BY_ID_RESPONSE) {
                byte[] compressedMessage = new byte[inputStream.readInt()];
                inputStream.readFully(compressedMessage);
                String response = SerialCompression.unpackSerializedStringFromByteArray(compressedMessage);

                List<Meeting> meetings = ToJsonSerializer.deserializeMultipleMeetings(response);
                if (meetings != null) {
                    for (int i = 0; i < meetings.size(); i++) {
                        Meeting comingInMeeting = meetings.get(i);
                        boolean exists = this.localMeetings.stream()
                                .anyMatch(m -> m.getOrganizer().getEmployeeID().equals(comingInMeeting.getOrganizer().getEmployeeID())
                                        && m.getMeetingTopic().equals(comingInMeeting.getMeetingTopic())
                                        && m.getPlace().equals(comingInMeeting.getPlace()));


                        if (exists) {
                            Meeting firstFindMeeting = this.localMeetings.stream()
                                    .filter(m -> m.getOrganizer().getEmployeeID().equals(comingInMeeting.getOrganizer().getEmployeeID())
                                            && m.getMeetingTopic().equals(comingInMeeting.getMeetingTopic())
                                            && m.getPlace().equals(comingInMeeting.getPlace())).toList().getFirst();
                            int index = this.localMeetings.indexOf(firstFindMeeting);
                            this.localMeetings.set(index, comingInMeeting);
                        } else {
                            this.localMeetings.add(comingInMeeting);
                        }
                    }
                }
                return localMeetings;
            } else {
                System.err.println("Unexpected response protocol: " + protocol);
                return null;
            }
        } catch (IOException e) {
            System.err.println("Error getting meetings: " + e.getMessage());
            return null;
        }
    }


    /**
     * Sends a message to the server using the specified communication protocol.
     *
     * <p>This method handles the low-level communication with the server by performing the
     * following
     * steps:</p>
     *
     * <p>1. Writes the protocol enum name as a UTF string to identify the type of message</p>
     * <p>2. Compresses the message content using the SerialCompression utility</p>
     * <p>3. Writes the length of the compressed message to the output stream</p>
     * <p>4. Writes the compressed message bytes to the output stream</p>
     * <p>5. Flushes the output stream to ensure immediate sending</p>
     *
     * <p>This method is used internally by other public methods to standardize the communication
     * format between client and server.</p>
     *
     * @param protocol The {@link CommunicationProtocol} enum indicating the type of message being
     *                 sent
     * @param message  The string message to be sent to the server, typically containing serialized
     *                 data
     * @throws IOException If there is an error writing to the output stream or if the connection to
     *                     the server is broken
     * @see CommunicationProtocol
     * @see SerialCompression#packSerializedStringIntoByteArray(String)
     */
    private void sendMessage(CommunicationProtocol protocol, String message) throws IOException {
        outputStream.writeUTF(protocol.name());
        byte[] compressedMessage = SerialCompression.packSerializedStringIntoByteArray(message);
        outputStream.writeInt(compressedMessage.length);
        outputStream.write(compressedMessage);
        outputStream.flush();
    }

    /**
     * Get the linked list of meetings with conflicts
     *
     * @return The linked list of meetings with conflicts, or null if empty
     */
    public LinkedList<Meeting> getOnErrorMeetingsLinkedList() {
        if (this.localOnErrorMeetings == null || this.localOnErrorMeetings.isEmpty()) {
            return null;
        } else {
            return new LinkedList<>(this.localOnErrorMeetings); // Return a copy to prevent external modifications
        }
    }

    public void clearOnErrorMeetingsLinkedList() {
        this.localOnErrorMeetings.clear();
        saveLocalMeetings();
    }

    /**
     * Set the handler for meeting updates
     *
     * @param handler The consumer that will handle meeting updates
     */
    public void setMeetingUpdateHandler(Consumer<Meeting> handler) {
        this.meetingUpdateHandler = handler;
    }

    /**
     * Set the handler for meeting deletions
     *
     * @param handler The consumer that will handle meeting deletions
     */
    public void setMeetingDeletionHandler(Consumer<Meeting> handler) {
        this.meetingDeletionHandler = handler;
    }

    /**
     * Disconnect from the server
     */
    public void disconnect() {
        // Save local meetings before disconnecting
        if (localMeetings != null && !localMeetings.isEmpty()) {
            saveLocalMeetings();
        }

        updateListenerRunning = false;
        updateListenerExecutor.shutdown();

        try {
            if (updateSocket != null && !updateSocket.isClosed()) {
                updateSocket.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }

        connected = false;
        authenticated = false;
        currentEmployee = null;
    }

    /**
     * Check if the client is connected to the server
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Check if the client is authenticated
     *
     * @return true if authenticated, false otherwise
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Get the current employee
     *
     * @return The current employee
     */
    public Employee getCurrentEmployee() {
        return currentEmployee;
    }
}
