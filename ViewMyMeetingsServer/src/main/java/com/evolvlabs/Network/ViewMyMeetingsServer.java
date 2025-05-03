package com.evolvlabs.Network;

import com.evolvlabs.DataModel.CommunicationProtocol;
import com.evolvlabs.DataModel.Employee;
import com.evolvlabs.DataModel.Meeting;
import com.evolvlabs.SerializationEngine.SerialCompression;
import com.evolvlabs.SerializationEngine.ToJsonSerializer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author : Santiago Arellano
 * @date : 21st-Apr-2025
 * @description : The server implementation for ViewMyMeetings application. This class implements
 * the mediator design pattern to handle multiple client connections. It creates a main server
 * socket to accept client connections and a secondary socket for each client to handle
 * back-propagation of updates.
 */
public class ViewMyMeetingsServer {
    private static final int DEFAULT_PORT = 8080;
    private static final String CONFIG_FILE = "serverConfiguration.properties";
    private static String MEETINGS_FILE = "meetings.json";
    private final int port;
    private ServerSocket serverSocket;
    private boolean running;
    private final ExecutorService clientHandlerPool;
    // Map of employeeID to ClientHandler
    private final Map<String, ClientHandler> connectedClients;
    // Map of employeeID to password
    private final Map<String, String> clientCredentials;
    private final List<Meeting> meetings;
    // Map of uuid to update port
    private final Map<String, Integer> uuidToPort;


    // Map of employeeID to meetings that they organized
    private final Map<String, List<Meeting>> organizerMeetings;
    // Map of employeeID to meetings that they are invited to
    private final Map<String, List<Meeting>> inviteeMeetings;
    // Map of employeeID to linked lists of conflicting meetings (head = causing meeting, tail = affected meetings)
    private final Map<String, Map<Meeting, List<Meeting>>> conflictMeetings;

    /**
     * Constructor with default port
     */
    public ViewMyMeetingsServer() {
        this(DEFAULT_PORT);
    }

    /**
     * Constructor with specified port
     *
     * @param port The port to listen on
     */
    public ViewMyMeetingsServer(int port) {
        this.port = port;
        this.clientHandlerPool = Executors.newCachedThreadPool();
        this.connectedClients = new ConcurrentHashMap<>();
        this.clientCredentials = new HashMap<>();
        this.meetings = new ArrayList<>();
        this.organizerMeetings = new HashMap<>();
        this.inviteeMeetings = new HashMap<>();
        this.conflictMeetings = new HashMap<>();
        this.uuidToPort = new HashMap<>();
        loadClientCredentials();
        loadMeetings();
    }

    /**
     * Load meetings from the JSON file and initializes the server's meeting data structures.
     * <p>
     * This method reads the meetings data from a JSON file specified by MEETINGS_FILE and
     * populates the server's internal data structures. It performs the following operations:
     * - Reads and parses the JSON file containing meeting information
     * - Deserializes the JSON content into Meeting objects
     * - Populates the meetings list with all loaded meetings
     * - Updates the organizerMeetings map with meetings organized by each employee
     * - Updates the inviteeMeetings map with meetings each employee is invited to
     * - Detects and stores any meeting time conflicts
     * </p>
     * <p>
     * If the meetings file doesn't exist, the method will initialize empty data structures.
     * If any error occurs during file reading or parsing, it will be logged to stderr and
     * the method will preserve any existing meeting data.
     * </p>
     *
     * @throws IOException If an I/O error occurs while reading the meetings file
     * @see Meeting
     * @see ToJsonSerializer#deserializeMultipleMeetings(String)
     * @see #detectAndStoreConflicts(List)
     */
    private void loadMeetings() {
        File file = new File(MEETINGS_FILE);
        if (file.exists()) {
            //? Read the file in the context of the application, this is meant to load all
            // meetings that have been stored within the application in a JSON file.
            try (FileReader reader = new FileReader(file)) {
                StringBuilder jsonContent = new StringBuilder();
                char[] buffer = new char[1024];
                int charsRead;
                while ((charsRead = reader.read(buffer)) != -1) {
                    jsonContent.append(buffer, 0, charsRead);
                }

                //? This is done through the JSON deserializaer built into the application as to
                //? package and submit the app for processing.
                List<Meeting> loadedMeetings = ToJsonSerializer.deserializeMultipleMeetings(jsonContent.toString());
                if (loadedMeetings != null) {
                    meetings.addAll(loadedMeetings);

                    for (Meeting meeting : loadedMeetings) {
                        String organizerId = meeting.getOrganizer().getEmployeeID();
                        organizerMeetings.computeIfAbsent(organizerId, k -> new ArrayList<>()).add(meeting);

                        for (Employee invitee : meeting.getInvitees()) {
                            String inviteeId = invitee.getEmployeeID();
                            inviteeMeetings.computeIfAbsent(inviteeId, k -> new ArrayList<>()).add(meeting);
                        }
                    }

                    // Check for conflicts and populate the conflict map, this is done before 
                    // such that when we load the applicaiton state, any notifications for 
                    // conflicts can be send out immediately
                    detectAndStoreConflicts(loadedMeetings);

                    System.out.println("Loaded " + meetings.size() + " meetings from " + MEETINGS_FILE);
                }
            } catch (IOException e) {
                System.err.println("Error loading meetings: " + e.getMessage());
            }
        } else {
            System.out.println("No meetings file found. Starting with empty meetings list.");
        }
    }


    /**
     * Detects and stores meeting time conflicts for all meetings in the provided list.
     *
     * <p>
     * This method iterates through the provided list of meetings and checks each meeting against
     * all other meetings to detect any time overlaps. When a conflict is found, it is stored in the
     * conflict map for both the organizer and all invitees of the affected meetings.
     * </p>
     *
     * <p>
     * For each meeting, the method: - Compares it against every other meeting in the list - Checks
     * for time overlaps using Meeting.isOtherMeetingOverlappingThisMeeting() - If an overlap is
     * found, stores the conflict for the organizer - Also stores the conflict for each invitee of
     * the meeting
     * </p>
     *
     * <p>
     * Conflicts are stored in the conflictMeetings map where: - The outer map key is the employee
     * ID (organizer or invitee) - The inner map key is the causing meeting - The inner map value is
     * a list of meetings affected by the causing meeting
     * </p>
     *
     * @param meetingsToCheck List of meetings to check for conflicts. Must not be null.
     * @throws NullPointerException if meetingsToCheck is null or if any meeting in the list is
     *                              null
     * @see Meeting#isOtherMeetingOverlappingThisMeeting(Meeting, Meeting)
     * @see #addConflict(String, Meeting, Meeting)
     */
    private void detectAndStoreConflicts(List<Meeting> meetingsToCheck) {
        // For each meeting
        for (int i = 0; i < meetingsToCheck.size(); i++) {
            Meeting currentMeeting = meetingsToCheck.get(i);
            String organizerId = currentMeeting.getOrganizer().getEmployeeID();

            //? Check for conflict betwene this meeting to every other meeting
            for (int j = 0; j < meetingsToCheck.size(); j++) {
                if (i == j) {
                    continue;
                }

                Meeting otherMeeting = meetingsToCheck.get(j);

                //? Check if the current meeting overlaps with the other meeting
                if (Meeting.isOtherMeetingOverlappingThisMeeting(currentMeeting, otherMeeting)) {
                    addConflict(organizerId, currentMeeting, otherMeeting);

                    for (Employee invitee : currentMeeting.getInvitees()) {
                        String inviteeId = invitee.getEmployeeID();
                        addConflict(inviteeId, currentMeeting, otherMeeting);
                    }
                }
            }
        }
    }

    /**
     * Add a conflict to a client's conflict map
     *
     * @param clientId        The client ID
     * @param causingMeeting  The meeting that caused the conflict
     * @param affectedMeeting The meeting affected by the conflict
     */
    private void addConflict(String clientId, Meeting causingMeeting, Meeting affectedMeeting) {
        Map<Meeting, List<Meeting>> clientConflicts = conflictMeetings.computeIfAbsent(
                clientId, k -> new HashMap<>());

        List<Meeting> conflicts = clientConflicts.computeIfAbsent(
                causingMeeting, k -> new ArrayList<>());

        if (!conflicts.contains(affectedMeeting)) {
            conflicts.add(affectedMeeting);
        }
    }

    /**
     * Save meetings to the JSON file
     */
    private synchronized void saveMeetings() {
        // Create a consolidated list of all meetings for backward compatibility
        List<Meeting> allMeetings = new ArrayList<>(meetings);

        // Clear and rebuild the consolidated list from the maps
        meetings.clear();

        // Add all meetings from organizer map
        for (List<Meeting> orgMeetings : organizerMeetings.values()) {
            for (Meeting meeting : orgMeetings) {
                if (!meetings.contains(meeting)) {
                    meetings.add(meeting);
                }
            }
        }

        try (FileWriter writer = new FileWriter(MEETINGS_FILE)) {
            String meetingsJson = ToJsonSerializer.serializeMultipleMeetings(meetings);
            writer.write(meetingsJson);
            System.out.println("Saved " + meetings.size() + " meetings to " + MEETINGS_FILE);
        } catch (IOException e) {
            System.err.println("Error saving meetings: " + e.getMessage());
        }
    }


    /**
     * Loads client credentials from the configuration file specified by CONFIG_FILE.
     *
     * <p>
     * This method reads the server configuration file to load client credentials and their
     * associated update ports. The configuration file should contain the following properties: -
     * clients.count: The total number of clients to load - clients.[i].id: The client ID for the
     * i-th client - clients.[i].password: The password for the i-th client -
     * clients.[i].update_port: The update port number for the i-th client -
     * server.storage.location: The location of the meetings storage file
     * </p>
     *
     * <p>
     * For each client defined in the configuration, this method: - Loads the client's ID and
     * password into the clientCredentials map - Associates the client's ID with their update port
     * in the uuidToPort map - Updates the MEETINGS_FILE path if specified in the configuration
     * </p>
     *
     * <p>
     * If the configuration file cannot be found or read, appropriate error messages are logged to
     * the system error stream. If individual client entries are missing required fields (id,
     * password, or update_port), those entries are skipped.
     * </p>
     *
     * @throws IOException           If an error occurs while reading the configuration file
     * @throws NumberFormatException If the clients.count property or any update_port value cannot
     *                               be parsed as an integer
     * @see #CONFIG_FILE
     * @see #clientCredentials
     * @see #uuidToPort
     */
    private void loadClientCredentials() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                System.err.println("Unable to find " + CONFIG_FILE);
                return;
            }

            Properties prop = new Properties();
            prop.load(input);

            int clientCount = Integer.parseInt(prop.getProperty("clients.count", "0"));
            System.out.println("Loading credentials for " + clientCount + " clients");

            for (int i = 0; i < clientCount; i++) {
                String id = prop.getProperty("clients." + i + ".id");
                String password = prop.getProperty("clients." + i + ".password");
                String port = prop.getProperty("clients." + i + ".update_port");
                MEETINGS_FILE = prop.getProperty("server.storage.location");

                if (id != null && password != null && port != null) {
                    clientCredentials.put(id, password);
                    uuidToPort.put(id, Integer.parseInt(port));
                    System.out.println("Loaded credentials for client ID: " + id);
                }
            }

            System.out.println("Loaded " + clientCredentials.size() + " client credentials");
        } catch (IOException e) {
            System.err.println("Error loading client credentials: " + e.getMessage());
        }
    }

    /**
     * Start the server
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Server started on port " + port);

            // Main loop to accept client connections
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());

                    // Create a new client handler for this connection
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    clientHandlerPool.submit(clientHandler);
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        } finally {
            stop();
        }
    }

    /**
     * Stop the server
     */
    public void stop() {
        running = false;
        clientHandlerPool.shutdown();

        // Close all client connections
        for (ClientHandler handler : connectedClients.values()) {
            handler.close();
        }
        connectedClients.clear();

        // Close the server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing server socket: " + e.getMessage());
            }
        }
    }

    /**
     * Send a message to a specific client
     *
     * @param employeeId The ID of the employee/client to send the message to
     * @param protocol   The communication protocol to use
     * @param message    The message to send
     * @return true if the message was sent successfully, false otherwise
     */
    public boolean sendMessageToClient(String employeeId, CommunicationProtocol protocol, String message) {
        ClientHandler handler = connectedClients.get(employeeId);
        if (handler != null) {
            return handler.sendMessage(protocol, message);
        }
        return false;
    }

    /**
     * Send a meeting update to a specific client
     *
     * @param employeeId The ID of the employee/client to send the update to
     * @param meeting    The meeting to send
     * @return true if the update was sent successfully, false otherwise
     */
    public boolean sendMeetingUpdateToClient(String employeeId, Meeting meeting) {
        String meetingJson = ToJsonSerializer.serializeSingleMeeting(meeting);
        return sendMessageToClient(employeeId,
                                   CommunicationProtocol.PUSH_SERVERSIDE_MEETING_CONFLICT_UPDATE_REQUEST,
                                   meetingJson);
    }

    /**
     * Send a meeting deletion notification to a specific client
     *
     * @param employeeId The ID of the employee/client to send the notification to
     * @param meeting    The meeting that was deleted
     * @return true if the notification was sent successfully, false otherwise
     */
    public boolean sendMeetingDeletionToClient(String employeeId, Meeting meeting) {
        String meetingJson = ToJsonSerializer.serializeSingleMeeting(meeting);
        return sendMessageToClient(employeeId,
                                   CommunicationProtocol.PUSH_SERVERSIDE_MEETING_DELETION_NOTIFICATION,
                                   meetingJson);
    }

    /**
     * Get all conflicting meetings for a client
     *
     * @param clientId The client ID
     * @return A map of causing meetings to lists of affected meetings
     */
    public Map<Meeting, List<Meeting>> getConflictingMeetings(String clientId) {
        return conflictMeetings.getOrDefault(clientId, new HashMap<>());
    }

    /**
     * Get all meetings organized by a client
     *
     * @param clientId The client ID
     * @return A list of meetings organized by the client
     */
    public List<Meeting> getClientOrganizerMeetings(String clientId) {
        return organizerMeetings.getOrDefault(clientId, new ArrayList<>());
    }

    /**
     * Get all meetings a client is invited to
     *
     * @param clientId The client ID
     * @return A list of meetings the client is invited to
     */
    public List<Meeting> getClientInviteeMeetings(String clientId) {
        return inviteeMeetings.getOrDefault(clientId, new ArrayList<>());
    }

    /**
     * The main method to start the server
     *
     * @param args Command line arguments (optional path to config file)
     */
    public static void main(String[] args) {
        String configPath = args.length > 0 ? args[0] : "serverConfiguration.properties";
        Properties serverConfig = new Properties();

        try {
            // Try to load from external file first
            File configFile = new File(configPath);
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    serverConfig.load(fis);
                }
            } else {
                // Fallback to classpath resource
                try (InputStream is = ViewMyMeetingsServer.class.getClassLoader()
                        .getResourceAsStream("serverConfiguration.properties")) {
                    if (is != null) {
                        serverConfig.load(is);
                    } else {
                        throw new FileNotFoundException("Could not find configuration file in classpath");
                    }
                }
            }

            // Get server configuration
            String address = serverConfig.getProperty("serverSide.serverSocketAddress", "0.0.0.0");
            int port = Integer.parseInt(serverConfig.getProperty("serverSide.serverSocketPort",
                                                                 String.valueOf(DEFAULT_PORT)));
            int backlog = Integer.parseInt(serverConfig.getProperty("serverSide.serverSocketMaxBacklog",
                                                                    "100"));

            // Create and start server with configuration
            ViewMyMeetingsServer server = new ViewMyMeetingsServer(port);
            server.start();

        } catch (IOException e) {
            System.err.println("Error loading configuration: " + e.getMessage());
            System.err.println("Using default configuration...");
            // Fall back to default configuration
            ViewMyMeetingsServer server = new ViewMyMeetingsServer(DEFAULT_PORT);
            server.start();
        } catch (NumberFormatException e) {
            System.err.println("Invalid port or backlog number in configuration: " + e.getMessage());
            System.err.println("Using default configuration...");
            ViewMyMeetingsServer server = new ViewMyMeetingsServer(DEFAULT_PORT);
            server.start();
        }
    }


    private class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private DataInputStream inputStream;
        private DataOutputStream outputStream;
        private String employeeId;
        private boolean authenticated = false;
        private Socket updateSocket;
        private DataOutputStream updateOutputStream;
        private ServerSocket updateServerSocket;

        /**
         * Constructor
         *
         * @param socket The client socket
         */
        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
            try {
                this.inputStream = new DataInputStream(socket.getInputStream());
                this.outputStream = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                System.err.println("Error creating streams: " + e.getMessage());
                close();
            }
        }

        @Override
        public void run() {
            try {
                while (!clientSocket.isClosed()) {
                    // Read the protocol
                    String protocolStr = inputStream.readUTF();
                    CommunicationProtocol protocol = CommunicationProtocol.valueOf(protocolStr);

                    // Read the message
                    byte[] compressedMessage = new byte[inputStream.readInt()];
                    inputStream.readFully(compressedMessage);
                    String message = SerialCompression.unpackSerializedStringFromByteArray(compressedMessage);

                    // Process the message based on the protocol
                    processMessage(protocol, message);
                }
            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                close();
            }
        }

        /**
         * Process a message from the client
         *
         * @param protocol The communication protocol
         * @param message  The message
         */
        private void processMessage(CommunicationProtocol protocol, String message) {
            switch (protocol) {
                case POST_CLIENTSIDE_AUTH_REQUEST:
                    handleAuthRequest(message);
                    break;
                case PUSH_CLIENTSIDE_MEETING_CREATION_REQUEST:
                    if (authenticated) {
                        handleMeetingCreationRequest(message);
                    }
                    break;
                case PUSH_CLIENTSIDE_MEETING_UPDATE_REQUEST:
                    if (authenticated) {
                        handleMeetingUpdateRequest(message);
                    }
                    break;
                case PUSH_CLIENTSIDE_MEETING_DELETION_REQUEST:
                    if (authenticated) {
                        handleMeetingDeletionRequest(message);
                    }
                    break;
                case GET_SERVERSIDE_MEETING_INFORMATION_BY_ID_REQUEST:
                    if (authenticated) {
                        handleMeetingInformationRequest(message);
                    }
                    break;
                default:
                    System.err.println("Unknown protocol: " + protocol);
            }
        }

        /**
         * Handle an authentication request
         *
         * @param message The message containing the employee information and credentials
         */
        private void handleAuthRequest(String message) {
            Meeting authMeeting = ToJsonSerializer.deserializeSingleMeeting(message);
            if (authMeeting != null && authMeeting.getOrganizer() != null) {
                Employee employee = authMeeting.getOrganizer();
                String employeeId = employee.getEmployeeID();
                // The password is stored in the meeting topic field for this authentication request
                String password = authMeeting.getMeetingTopic();

                // Validate credentials
                if (clientCredentials.containsKey(employeeId) &&
                        clientCredentials.get(employeeId).equals(password)) {

                    this.employeeId = employeeId;
                    this.authenticated = true;
                    connectedClients.put(employeeId, this);

                    System.out.println("Client authenticated: " + employeeId);

                    // Create a secondary socket for updates
                    try {

                        updateServerSocket = new ServerSocket(uuidToPort.get(employeeId));

                        // Send the update port to the client
                        sendMessage(CommunicationProtocol.POST_SERVERSIDE_AUTH_RESPONSE,
                                    String.valueOf(uuidToPort.get(employeeId)));

                        // Accept the client's connection for updates
                        updateSocket = updateServerSocket.accept();
                        updateOutputStream = new DataOutputStream(updateSocket.getOutputStream());

                        System.out.println("Update channel established for client: " + employeeId);

                        // After successful authentication and update channel establishment,
                        // send all existing conflicts to the client
                        sendExistingConflictsToClient(employeeId);

                    } catch (IOException e) {
                        System.err.println("Error creating update socket: " + e.getMessage());
                        authenticated = false;
                        connectedClients.remove(employeeId);
                        sendMessage(CommunicationProtocol.POST_SERVERSIDE_AUTH_RESPONSE, "false");
                    }
                } else {
                    System.err.println("Authentication failed for client ID: " + employeeId);
                    sendMessage(CommunicationProtocol.POST_SERVERSIDE_AUTH_RESPONSE, "false");
                }
            } else {
                System.err.println("Invalid authentication request");
                sendMessage(CommunicationProtocol.POST_SERVERSIDE_AUTH_RESPONSE, "false");
            }
        }

        /**
         * Handle a meeting creation request
         *
         * @param message The message containing the meeting information
         */
        private void handleMeetingCreationRequest(String message) {
            Meeting meeting = ToJsonSerializer.deserializeSingleMeeting(message);
            if (meeting != null) {
                boolean hasConflicts = false;
                String organizerId = meeting.getOrganizer().getEmployeeID();

                // Check for conflicts with the organizer's meetings
                if (organizerMeetings.containsKey(organizerId)) {
                    for (Meeting existingMeeting : organizerMeetings.get(organizerId)) {
                        if (Meeting.isOtherMeetingOverlappingThisMeeting(meeting, existingMeeting)) {
                            // Add to conflict map
                            addConflict(organizerId, meeting, existingMeeting);
                            hasConflicts = true;
                        }
                    }
                }

                // Check for conflicts with organizer's invited meetings even if there are no
                // invitees in the previous meeting
                if (inviteeMeetings.containsKey(organizerId)) {
                    for (Meeting existingMeeting : inviteeMeetings.get(organizerId)) {
                        if (Meeting.isOtherMeetingOverlappingThisMeeting(meeting, existingMeeting)) {
                            // Add to conflict map
                            addConflict(organizerId, meeting, existingMeeting);
                            hasConflicts = true;
                        }
                    }
                }

                // Check for conflicts with each invitee's meetings
                for (Employee invitee : meeting.getInvitees()) {
                    String inviteeId = invitee.getEmployeeID();

                    // Check conflicts with invitee's organized meetings
                    if (organizerMeetings.containsKey(inviteeId)) {
                        for (Meeting existingMeeting : organizerMeetings.get(inviteeId)) {
                            if (Meeting.isOtherMeetingOverlappingThisMeeting(meeting, existingMeeting)) {
                                // Add to conflict map
                                addConflict(inviteeId, meeting, existingMeeting);
                                hasConflicts = true;
                            }
                        }
                    }

                    // Check conflicts with invitee's invited meetings
                    if (inviteeMeetings.containsKey(inviteeId)) {
                        for (Meeting existingMeeting : inviteeMeetings.get(inviteeId)) {
                            if (Meeting.isOtherMeetingOverlappingThisMeeting(meeting, existingMeeting)) {
                                // Add to conflict map
                                addConflict(inviteeId, meeting, existingMeeting);
                                hasConflicts = true;
                            }
                        }
                    }
                }

                // If there are conflicts, notify the clients
                if (hasConflicts) {
                    notifyClientsOfConflicts(meeting);

                }

                // Add the meeting to the appropriate maps
                // Add to organizer's meetings
                organizerMeetings.computeIfAbsent(organizerId, k -> new ArrayList<>()).add(meeting);

                // Add to each invitee's meetings
                for (Employee invitee : meeting.getInvitees()) {
                    String inviteeId = invitee.getEmployeeID();
                    inviteeMeetings.computeIfAbsent(inviteeId, k -> new ArrayList<>()).add(meeting);
                }

                // Add to general meetings list for backward compatibility
                meetings.add(meeting);

                // Save the updated meetings list to the JSON file
                saveMeetings();

                System.out.println("Meeting created: " + meeting.getMeetingTopic());
                sendMessage(CommunicationProtocol.PUSH_SERVERSIDE_MEETING_CREATION_RESPONSE, "true");
            } else {
                sendMessage(CommunicationProtocol.PUSH_SERVERSIDE_MEETING_CREATION_RESPONSE, "false");
            }
        }

        /**
         * Notify clients of meeting conflicts
         *
         * @param meeting The meeting that caused conflicts
         */
        private void notifyClientsOfConflicts(Meeting meeting) {
            String organizerId = meeting.getOrganizer().getEmployeeID();

            // Notify the organizer
            if (conflictMeetings.containsKey(organizerId) &&
                    conflictMeetings.get(organizerId).containsKey(meeting)) {

                ClientHandler organizerHandler = connectedClients.get(organizerId);
                if (organizerHandler != null) {
                    // Get all affected meetings
                    List<Meeting> affectedMeetings = conflictMeetings.get(organizerId).get(meeting);

                    // First send the causing meeting
                    String causingMeetingJson = ToJsonSerializer.serializeSingleMeeting(meeting);
                    organizerHandler.sendUpdateMessage(
                            CommunicationProtocol.PUSH_SERVERSIDE_MEETING_CONFLICT_UPDATE_REQUEST,
                            causingMeetingJson);

                    // Then send all affected meetings
                    for (Meeting affectedMeeting : affectedMeetings) {
                        String affectedMeetingJson = ToJsonSerializer.serializeSingleMeeting(affectedMeeting);
                        organizerHandler.sendUpdateMessage(
                                CommunicationProtocol.PUSH_SERVERSIDE_MEETING_CONFLICT_UPDATE_REQUEST,
                                affectedMeetingJson);
                    }
                }
            }

            // Notify each invitee
            for (Employee invitee : meeting.getInvitees()) {
                String inviteeId = invitee.getEmployeeID();

                if (conflictMeetings.containsKey(inviteeId) &&
                        conflictMeetings.get(inviteeId).containsKey(meeting)) {

                    ClientHandler inviteeHandler = connectedClients.get(inviteeId);
                    if (inviteeHandler != null) {
                        // Get all affected meetings
                        List<Meeting> affectedMeetings = conflictMeetings.get(inviteeId).get(meeting);

                        // First send the causing meeting
                        String causingMeetingJson = ToJsonSerializer.serializeSingleMeeting(meeting);
                        inviteeHandler.sendUpdateMessage(
                                CommunicationProtocol.PUSH_SERVERSIDE_MEETING_CONFLICT_UPDATE_REQUEST,
                                causingMeetingJson);

                        // Then send all affected meetings
                        for (Meeting affectedMeeting : affectedMeetings) {
                            String affectedMeetingJson = ToJsonSerializer.serializeSingleMeeting(affectedMeeting);
                            inviteeHandler.sendUpdateMessage(
                                    CommunicationProtocol.PUSH_SERVERSIDE_MEETING_CONFLICT_UPDATE_REQUEST,
                                    affectedMeetingJson);
                        }
                    }
                }
            }
        }

        /**
         * Handle a meeting update request from a client
         *
         * @param message The serialized meeting
         */
        private void handleMeetingUpdateRequest(String message) {
            Meeting updatedMeeting = ToJsonSerializer.deserializeSingleMeeting(message);
            if (updatedMeeting == null) {
                sendMessage(CommunicationProtocol.PUSH_SERVERSIDE_MEETING_UPDATE_RESPONSE, "false");
                return;
            }

            String organizerId = updatedMeeting.getOrganizer().getEmployeeID();
            Meeting existingMeeting = null;

            // Find existing meeting by comparing identity (organizer, topic, place) - not times
            if (organizerMeetings.containsKey(organizerId)) {
                List<Meeting> orgMeetings = organizerMeetings.get(organizerId);
                for (int i = 0; i < orgMeetings.size(); i++) {
                    Meeting orgMeeting = orgMeetings.get(i);
                    if (areSameMeetingIdentity(orgMeeting, updatedMeeting)) {
                        existingMeeting = orgMeeting;
                        // Replace the meeting in the organizer's list
                        orgMeetings.set(i, updatedMeeting);
                        System.out.println("Found and updated meeting: " + updatedMeeting.getMeetingTopic() +
                                                   " for organizer: " + organizerId);
                        break;
                    }
                }
            }

            if (existingMeeting == null) {
                sendMessage(CommunicationProtocol.PUSH_SERVERSIDE_MEETING_UPDATE_RESPONSE, "false");
                System.out.println("Failed to find meeting to update: " + updatedMeeting.getMeetingTopic());
                return;
            }

            // Update invitee lists too
            updateInviteeLists(existingMeeting, updatedMeeting);

            // Clear the existing conflicts for this meeting
            clearConflictsForMeeting(updatedMeeting);

            // Check for new conflicts with the updated meeting times
            boolean hasConflicts = checkForConflicts(updatedMeeting);
            System.out.println("Updated meeting: " + updatedMeeting.getMeetingTopic() +
                                       " has conflicts: " + hasConflicts);

            // If there are no conflicts after the update, make sure to notify clients
            if (!hasConflicts) {
                notifyClientsOfConflictResolution(updatedMeeting);
            }

            saveMeetings();
            sendMessage(CommunicationProtocol.PUSH_SERVERSIDE_MEETING_UPDATE_RESPONSE, "true");
            System.out.println("Successfully updated meeting: " + updatedMeeting.getMeetingTopic());
        }

        /**
         * Update invitee lists when a meeting is updated
         *
         * @param oldMeeting The old meeting
         * @param newMeeting The new meeting
         */
        private void updateInviteeLists(Meeting oldMeeting, Meeting newMeeting) {
            // Remove from old invitees' lists
            for (Employee oldInvitee : oldMeeting.getInvitees()) {
                String inviteeId = oldInvitee.getEmployeeID();
                if (inviteeMeetings.containsKey(inviteeId)) {
                    List<Meeting> inviteeMeetingList = inviteeMeetings.get(inviteeId);
                    Iterator<Meeting> iterator = inviteeMeetingList.iterator();
                    while (iterator.hasNext()) {
                        Meeting m = iterator.next();
                        if (areSameMeetingIdentity(m, oldMeeting)) {
                            iterator.remove();
                            break;
                        }
                    }
                }
            }

            // Add to new invitees' lists
            for (Employee newInvitee : newMeeting.getInvitees()) {
                String inviteeId = newInvitee.getEmployeeID();
                List<Meeting> inviteeMeetingList = inviteeMeetings.computeIfAbsent(
                        inviteeId, k -> new ArrayList<>()
                                                                                  );

                // Add new meeting if not already present
                boolean found = false;
                for (Meeting m : inviteeMeetingList) {
                    if (areSameMeetingIdentity(m, newMeeting)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    inviteeMeetingList.add(newMeeting);
                }
            }

            System.out.println("Updated invitee lists for meeting: " + newMeeting.getMeetingTopic());
        }

        /**
         * Clear all conflicts for a meeting
         *
         * @param meeting The meeting to clear conflicts for
         */
        private void clearConflictsForMeeting(Meeting meeting) {
            String organizerId = meeting.getOrganizer().getEmployeeID();
            Set<String> affectedClientIds = new HashSet<>();
            affectedClientIds.add(organizerId);

            // Add all invitees to the affected clients set
            for (Employee invitee : meeting.getInvitees()) {
                affectedClientIds.add(invitee.getEmployeeID());
            }

            // Process all affected clients
            for (String clientId : affectedClientIds) {
                if (conflictMeetings.containsKey(clientId)) {
                    Map<Meeting, List<Meeting>> clientConflicts = conflictMeetings.get(clientId);

                    // 1. Remove this meeting as a causing meeting (when it's the key)
                    // Use iterator to safely remove while iterating
                    Iterator<Map.Entry<Meeting, List<Meeting>>> keyIterator = clientConflicts.entrySet().iterator();
                    while (keyIterator.hasNext()) {
                        Map.Entry<Meeting, List<Meeting>> entry = keyIterator.next();
                        Meeting causingMeeting = entry.getKey();
                        if (areSameMeetingIdentity(causingMeeting, meeting)) {
                            System.out.println("Removed meeting as causing conflict: " + meeting.getMeetingTopic() +
                                                       " for client: " + clientId);
                            keyIterator.remove();
                        }
                    }

                    // 2. Remove this meeting from all affected meeting lists (as a value)
                    for (Map.Entry<Meeting, List<Meeting>> entry : clientConflicts.entrySet()) {
                        List<Meeting> affectedMeetings = entry.getValue();
                        Iterator<Meeting> valueIterator = affectedMeetings.iterator();
                        while (valueIterator.hasNext()) {
                            Meeting affectedMeeting = valueIterator.next();
                            if (areSameMeetingIdentity(affectedMeeting, meeting)) {
                                System.out.println("Removed meeting as affected by conflict: " + meeting.getMeetingTopic() +
                                                           " for client: " + clientId);
                                valueIterator.remove();
                            }
                        }
                    }

                    // 3. Remove any empty conflict entries
                    clientConflicts.entrySet().removeIf(entry -> entry.getValue().isEmpty());

                    // 4. If client has no more conflicts, remove their map entirely
                    if (clientConflicts.isEmpty()) {
                        conflictMeetings.remove(clientId);
                        System.out.println("Removed all conflicts for client: " + clientId);
                    }
                }
            }

            // Notify clients that conflicts have been resolved
            notifyClientsOfConflictResolution(meeting);
        }

        /**
         * Helper method to identify if two meetings are functionally the same (ignoring time
         * changes which are often updated during conflict resolution)
         *
         * @param m1 First meeting to compare
         * @param m2 Second meeting to compare
         * @return true if meetings have the same identity (organizer, topic, place)
         */
        private boolean areSameMeetingIdentity(Meeting m1, Meeting m2) {
            if (m1 == null || m2 == null) {
                return false;
            }

            return m1.getOrganizer().getEmployeeID().equals(m2.getOrganizer().getEmployeeID()) &&
                    m1.getMeetingTopic().equals(m2.getMeetingTopic()) &&
                    m1.getPlace().equals(m2.getPlace());
        }


        /**
         * Notify clients that conflicts have been resolved for a meeting
         *
         * @param meeting The meeting whose conflicts have been resolved
         */
        private void notifyClientsOfConflictResolution(Meeting meeting) {
            // Notify organizer
            String organizerId = meeting.getOrganizer().getEmployeeID();
            ClientHandler organizerHandler = connectedClients.get(organizerId);
            if (organizerHandler != null) {
                String meetingJson = ToJsonSerializer.serializeSingleMeeting(meeting);
                organizerHandler.sendUpdateMessage(
                        CommunicationProtocol.PUSH_SERVERSIDE_MEETING_CONFLICT_RESOLUTION_NOTIFICATION,
                        meetingJson);
                System.out.println("Notified organizer " + organizerId + " that conflicts for meeting " +
                                           meeting.getMeetingTopic() + " have been resolved");
            }

            // Notify all invitees
            for (Employee invitee : meeting.getInvitees()) {
                String inviteeId = invitee.getEmployeeID();
                ClientHandler inviteeHandler = connectedClients.get(inviteeId);
                if (inviteeHandler != null) {
                    String meetingJson = ToJsonSerializer.serializeSingleMeeting(meeting);
                    inviteeHandler.sendUpdateMessage(
                            CommunicationProtocol.PUSH_SERVERSIDE_MEETING_CONFLICT_RESOLUTION_NOTIFICATION,
                            meetingJson);
                    System.out.println("Notified invitee " + inviteeId + " that conflicts for meeting " +
                                               meeting.getMeetingTopic() + " have been resolved");
                }
            }
        }

        /**
         * Check for conflicts with a meeting
         *
         * @param meeting The meeting to check for conflicts
         * @return true if conflicts were found, false otherwise
         */
        private boolean checkForConflicts(Meeting meeting) {
            boolean hasConflicts = false;
            String organizerId = meeting.getOrganizer().getEmployeeID();

            // Check for conflicts with organizer's meetings
            if (organizerMeetings.containsKey(organizerId)) {
                for (Meeting existingMeeting : organizerMeetings.get(organizerId)) {
                    if (existingMeeting != meeting && Meeting.isOtherMeetingOverlappingThisMeeting(meeting, existingMeeting)) {
                        addConflict(organizerId, meeting, existingMeeting);
                        hasConflicts = true;
                    }
                }
            }

            // Check for conflicts with invitees' meetings
            for (Employee invitee : meeting.getInvitees()) {
                String inviteeId = invitee.getEmployeeID();

                // Check against organizer meetings
                if (organizerMeetings.containsKey(inviteeId)) {
                    for (Meeting existingMeeting : organizerMeetings.get(inviteeId)) {
                        if (existingMeeting != meeting && Meeting.isOtherMeetingOverlappingThisMeeting(meeting, existingMeeting)) {
                            addConflict(inviteeId, meeting, existingMeeting);
                            hasConflicts = true;
                        }
                    }
                }

                // Check against invitee meetings
                if (inviteeMeetings.containsKey(inviteeId)) {
                    for (Meeting existingMeeting : inviteeMeetings.get(inviteeId)) {
                        if (existingMeeting != meeting && Meeting.isOtherMeetingOverlappingThisMeeting(meeting, existingMeeting)) {
                            addConflict(inviteeId, meeting, existingMeeting);
                            hasConflicts = true;
                        }
                    }
                }
            }

            return hasConflicts;
        }

        /**
         * Handle a meeting deletion request
         *
         * @param message The message containing the meeting information
         */
        private void handleMeetingDeletionRequest(String message) {
            Meeting meetingToDelete = ToJsonSerializer.deserializeSingleMeeting(message);
            if (meetingToDelete != null) {
                String organizerId = meetingToDelete.getOrganizer().getEmployeeID();
                boolean removed = false;
                Meeting actualMeetingToDelete = null;

                // Find and remove from organizer's meetings
                if (organizerMeetings.containsKey(organizerId)) {
                    List<Meeting> orgMeetings = organizerMeetings.get(organizerId);
                    for (int i = 0; i < orgMeetings.size(); i++) {
                        Meeting meeting = orgMeetings.get(i);
                        // Compare by organizer and topic (assuming these uniquely identify a meeting)
                        if (meeting.getOrganizer().getEmployeeID().equals(meetingToDelete.getOrganizer().getEmployeeID()) &&
                                meeting.getMeetingTopic().equals(meetingToDelete.getMeetingTopic())) {
                            actualMeetingToDelete = meeting;
                            orgMeetings.remove(i);
                            removed = true;
                            break;
                        }
                    }

                    // Remove the list if it's empty
                    if (orgMeetings.isEmpty()) {
                        organizerMeetings.remove(organizerId);
                    }
                }

                if (removed && actualMeetingToDelete != null) {
                    // Remove from invitees' meetings
                    for (Employee invitee : actualMeetingToDelete.getInvitees()) {
                        String inviteeId = invitee.getEmployeeID();
                        if (inviteeMeetings.containsKey(inviteeId)) {
                            List<Meeting> inviteeMeetingList = inviteeMeetings.get(inviteeId);
                            for (int i = 0; i < inviteeMeetingList.size(); i++) {
                                Meeting meeting = inviteeMeetingList.get(i);
                                if (meeting.getOrganizer().getEmployeeID().equals(actualMeetingToDelete.getOrganizer().getEmployeeID()) &&
                                        meeting.getMeetingTopic().equals(actualMeetingToDelete.getMeetingTopic())) {
                                    inviteeMeetingList.remove(i);
                                    break;
                                }
                            }

                            // Remove the list if it's empty
                            if (inviteeMeetingList.isEmpty()) {
                                inviteeMeetings.remove(inviteeId);
                            }
                        }
                    }

                    // Remove from conflict maps
                    clearConflictsForMeeting(actualMeetingToDelete);

                    // Remove from general meetings list for backward compatibility
                    for (int i = 0; i < meetings.size(); i++) {
                        Meeting meeting = meetings.get(i);
                        if (meeting.getOrganizer().getEmployeeID().equals(actualMeetingToDelete.getOrganizer().getEmployeeID()) &&
                                meeting.getMeetingTopic().equals(actualMeetingToDelete.getMeetingTopic())) {
                            meetings.remove(i);
                            break;
                        }
                    }

                    // Save the updated meetings list to the JSON file
                    saveMeetings();

                    // Notify clients about the deletion
                    notifyClientsOfDeletion(actualMeetingToDelete);

                    System.out.println("Meeting deleted: " + actualMeetingToDelete.getMeetingTopic());
                    sendMessage(CommunicationProtocol.PUSH_SERVERSIDE_MEETING_DELETION_RESPONSE, "true");
                } else {
                    System.out.println("Meeting not found for deletion: " + meetingToDelete.getMeetingTopic());
                    sendMessage(CommunicationProtocol.PUSH_SERVERSIDE_MEETING_DELETION_RESPONSE, "false");
                }
            } else {
                sendMessage(CommunicationProtocol.PUSH_SERVERSIDE_MEETING_DELETION_RESPONSE, "false");
            }
        }

        /**
         * Notify clients about a meeting deletion
         *
         * @param meeting The meeting that was deleted
         */
        private void notifyClientsOfDeletion(Meeting meeting) {
            String organizerId = meeting.getOrganizer().getEmployeeID();

            // Notify the organizer if connected
            ClientHandler organizerHandler = connectedClients.get(organizerId);
            if (organizerHandler != null) {
                String meetingJson = ToJsonSerializer.serializeSingleMeeting(meeting);
                organizerHandler.sendUpdateMessage(
                        CommunicationProtocol.PUSH_SERVERSIDE_MEETING_DELETION_NOTIFICATION,
                        meetingJson);
            }

            // Notify all invitees if connected
            for (Employee invitee : meeting.getInvitees()) {
                String inviteeId = invitee.getEmployeeID();
                ClientHandler inviteeHandler = connectedClients.get(inviteeId);
                if (inviteeHandler != null) {
                    String meetingJson = ToJsonSerializer.serializeSingleMeeting(meeting);
                    inviteeHandler.sendUpdateMessage(
                            CommunicationProtocol.PUSH_SERVERSIDE_MEETING_DELETION_NOTIFICATION,
                            meetingJson);
                }
            }
        }

        /**
         * Send all existing conflicts to a client
         *
         * @param clientId The ID of the client to send conflicts to
         */
        private void sendExistingConflictsToClient(String clientId) {
            // Check if the client has any conflicts
            if (conflictMeetings.containsKey(clientId)) {
                Map<Meeting, List<Meeting>> clientConflicts = conflictMeetings.get(clientId);

                // For each causing meeting and its affected meetings
                for (Map.Entry<Meeting, List<Meeting>> conflictEntry : clientConflicts.entrySet()) {
                    Meeting causingMeeting = conflictEntry.getKey();
                    List<Meeting> affectedMeetings = conflictEntry.getValue();

                    // Only send conflicts if there are actually affected meetings
                    if (affectedMeetings != null && !affectedMeetings.isEmpty()) {
                        // First send the causing meeting
                        String causingMeetingJson = ToJsonSerializer.serializeSingleMeeting(causingMeeting);
                        sendUpdateMessage(
                                CommunicationProtocol.PUSH_SERVERSIDE_MEETING_CONFLICT_UPDATE_REQUEST,
                                causingMeetingJson);

                        // Then send all affected meetings
                        for (Meeting affectedMeeting : affectedMeetings) {
                            String affectedMeetingJson = ToJsonSerializer.serializeSingleMeeting(affectedMeeting);
                            sendUpdateMessage(
                                    CommunicationProtocol.PUSH_SERVERSIDE_MEETING_CONFLICT_UPDATE_REQUEST,
                                    affectedMeetingJson);
                        }

                        System.out.println("Sent conflict group for meeting: " + causingMeeting.getMeetingTopic());
                    }
                }

                System.out.println("Finished sending existing conflicts to client: " + clientId);
            }
        }

        /**
         * Handle a meeting information request
         *
         * @param message The message containing the employee ID
         */
        private void handleMeetingInformationRequest(String message) {
            String employeeId = message.trim();
            if (employeeId != null && !employeeId.isEmpty()) {
                // Create a combined list of meetings for this employee
                List<Meeting> employeeMeetings = new ArrayList<>();

                // Add organizer meetings //Passing colors!!!
                if (organizerMeetings.containsKey(employeeId)) {
                    employeeMeetings.addAll(organizerMeetings.get(employeeId));
                }

                // Add invitee meetings
                if (inviteeMeetings.containsKey(employeeId)) {
                    // Add only meetings that aren't already in the list
                    for (Meeting meeting : inviteeMeetings.get(employeeId)) {
                        if (!employeeMeetings.contains(meeting) && meeting.getInvitees().stream()
                                .map(Employee::getEmployeeID).toList().contains(employeeId)) {
                            employeeMeetings.add(meeting);
                        }
                    }
                }


                // Serialize the filtered meetings list and send it to the client
                String meetingsJson = ToJsonSerializer.serializeMultipleMeetings(employeeMeetings);
                System.out.println("Sending " + employeeMeetings.size() + " meetings to employee " + employeeId);
                sendMessage(CommunicationProtocol.GET_SERVERSIDE_MEETING_INFORMATION_BY_ID_RESPONSE, meetingsJson);
            } else {
                // If no employee ID was provided, send an empty list
                System.err.println("No employee ID provided for meeting information request");
                sendMessage(CommunicationProtocol.GET_SERVERSIDE_MEETING_INFORMATION_BY_ID_RESPONSE, "[]");
            }
        }

        /**
         * Send a message to the client
         *
         * @param protocol The communication protocol
         * @param message  The message
         * @return true if the message was sent successfully, false otherwise
         */
        public boolean sendMessage(CommunicationProtocol protocol, String message) {
            try {
                outputStream.writeUTF(protocol.name());
                byte[] compressedMessage = SerialCompression.packSerializedStringIntoByteArray(message);
                outputStream.writeInt(compressedMessage.length);
                outputStream.write(compressedMessage);
                outputStream.flush();
                return true;
            } catch (IOException e) {
                System.err.println("Error sending message: " + e.getMessage());
                return false;
            }
        }

        /**
         * Send an update message to the client through the update socket
         *
         * @param protocol The communication protocol
         * @param message  The message
         * @return true if the message was sent successfully, false otherwise
         */
        public boolean sendUpdateMessage(CommunicationProtocol protocol, String message) {
            if (updateOutputStream != null && updateSocket != null && !updateSocket.isClosed()) {
                try {
                    updateOutputStream.writeUTF(protocol.name());
                    byte[] compressedMessage = SerialCompression.packSerializedStringIntoByteArray(message);
                    updateOutputStream.writeInt(compressedMessage.length);
                    updateOutputStream.write(compressedMessage);
                    updateOutputStream.flush();
                    return true;
                } catch (IOException e) {
                    System.err.println("Error sending update message: " + e.getMessage());
                    // If there's an error, attempt to reconnect or handle the error
                    return false;
                }
            } else {
                System.err.println("Update connection not available for client: " + employeeId);
                return false;
            }
        }

        /**
         * Close the client handler
         */
        public void close() {
            if (employeeId != null) {
                connectedClients.remove(employeeId);
            }

            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
                if (updateSocket != null && !updateSocket.isClosed()) {
                    updateSocket.close();
                }
                if (updateServerSocket != null && !updateServerSocket.isClosed()) {
                    updateServerSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing client handler: " + e.getMessage());
            }
        }
    }
}
