package com.evolvlabs.UI;

import com.evolvlabs.DataModel.Employee;
import com.evolvlabs.DataModel.Meeting;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author : Santiago Arellano
 * @date : 21st-Apr-2025
 * @description : Console UI for the ViewMyMeetings client application. This class provides a
 * text-based user interface for interacting with the server.
 */
public class ConsoleUI {
    private final ClientFacade facade;
    private final Scanner scanner;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private boolean running = false;

    /**
     * Constructor that initializes the UI with a default ClientFacade
     */
    public ConsoleUI() {
        this.facade = new ClientFacade();
        this.scanner = new Scanner(System.in);
    }

    /**
     * Constructor that initializes the UI with a specified ClientFacade
     *
     * @param facade The ClientFacade to use
     */
    public ConsoleUI(ClientFacade facade) {
        this.facade = facade;
        this.scanner = new Scanner(System.in);
    }

    public ConsoleUI(Properties config) {
        this.facade = new ClientFacade(config);
        this.scanner = new Scanner(System.in);
    }

    /**
     * Start the UI
     */
    public void start() {
        running = true;

        System.out.println("Welcome to ViewMyMeetings Client");
        System.out.println("-------------------------------");

        // Connect to the server
        if (!connectToServer()) {
            return;
        }

        // Authenticate with the server
        if (!authenticateWithServer()) {
            facade.disconnect();
            return;
        }


        // Main menu loop
        while (running) {
            displayMainMenu();
            int choice = getIntInput("Enter your choice: ", 0, 7);

            switch (choice) {
                case 1 -> listMeetings();
                case 2 -> createMeeting();
                case 3 -> updateMeeting();
                case 4 -> deleteMeeting();
                case 5 -> reviewConflictedMeetings();
                case 6 -> correctConflictedMeetings();
                case 7 -> showHelp();
                case 0 -> exit();
            }
        }
    }

    /**
     * Connect to the server
     *
     * @return true if connected successfully, false otherwise
     */
    private boolean connectToServer() {
        System.out.println("Connecting to server...");
        boolean connected = facade.connect();

        if (connected) {
            System.out.println("Connected to server successfully.");
            return true;
        } else {
            System.out.println("Failed to connect to server. Please try again later.");
            return false;
        }
    }


    /**
     * Authenticate with the server
     *
     * @return true if authenticated successfully, false otherwise
     */
    private boolean authenticateWithServer() {
        try {
            System.out.println("Authenticating with server...");

            // Try to authenticate with loaded credentials first
            boolean authenticated = facade.authenticate();

            if (authenticated) {
                System.out.println("Authenticated successfully as " +
                                           facade.getCurrentEmployee().getEmployeeFullName() +
                                           " (" + facade.getCurrentEmployee().getEmployeeID() + ")");
                return true;
            }

            // If automatic authentication fails, prompt for credentials
            System.out.println("Automatic authentication failed. Please enter your credentials.");

            for (int attempts = 0; attempts < 3; attempts++) {
                String employeeId = getStringInput("Enter your employee ID: ");
                String employeeName = getStringInput("Enter your full name: ");
                String password = getStringInput("Enter your password: ");

                authenticated = facade.authenticate(employeeId, employeeName, password);

                if (authenticated) {
                    System.out.println("Authenticated successfully as " +
                                               facade.getCurrentEmployee().getEmployeeFullName() +
                                               " (" + facade.getCurrentEmployee().getEmployeeID() + ")");
                    return true;
                } else {
                    System.out.println("Authentication failed. Please try again. " +
                                               (2 - attempts) + " attempts remaining.");
                }
            }

            System.out.println("Authentication failed after 3 attempts. Exiting.");
            return false;
        } catch (Exception e) {
            System.out.println("Error during authentication: " + e.getMessage());
            return false;
        }
    }


    /**
     * Display the main menu
     */
    private void displayMainMenu() {
        System.out.println("\nMain Menu");
        System.out.println("---------");
        System.out.println("1. List Meetings");
        System.out.println("2. Create Meeting");
        System.out.println("3. Update Meeting");
        System.out.println("4. Delete Meeting");
        System.out.println("5. Review Conflicted Meetings");
        System.out.println("6. Correct Conflicted Meetings");
        System.out.println("7. Help");
        System.out.println("0. Exit");
    }

    /**
     * List all meetings
     */
    private void listMeetings() {
        System.out.println("\nFetching meetings...");
        List<Meeting> meetings = facade.getMeetings();

        if (meetings == null) {
            System.out.println("Failed to fetch meetings. Please try again later.");
            return;
        }

        if (meetings.isEmpty()) {
            System.out.println("No meetings found.");
            return;
        }

        System.out.println("\nYour Meetings:");
        System.out.println("-------------");

        for (int i = 0; i < meetings.size(); i++) {
            Meeting meeting = meetings.get(i);
            System.out.println((i + 1) + ". " + meeting.getMeetingTopic());
            System.out.println("   Place: " + meeting.getPlace());
            System.out.println("   Start: " + dateFormat.format(meeting.getStartTime()));
            System.out.println("   End: " + dateFormat.format(meeting.getEndTime()));
            System.out.println("   Organizer: " + meeting.getOrganizer().getEmployeeFullName());
            System.out.println("   Invitees: " + formatInvitees(meeting.getInvitees()));
            System.out.println();
        }
    }

    /**
     * Allows users to correct meetings that have time conflicts with other meetings.
     * <p>
     * This method displays a list of conflicted meetings where the current user is the organizer
     * and provides functionality to modify their details. The method first fetches all conflicted
     * meetings from the server through the facade. If there are any conflicted meetings, it filters
     * them to show only those organized by the current user.
     * </p>
     *
     * <p>
     * For each conflicted meeting, it displays: - Meeting topic - Place - Start time - End time -
     * Organizer - List of invitees
     * </p>
     *
     * <p>
     * The user can select a meeting to update by entering its number, or cancel the operation by
     * entering -1. After successful update, the conflicted meetings list is cleared.
     * </p>
     *
     * @throws RuntimeException if there's an error while communicating with the server
     * @see Meeting
     * @see ClientFacade#getConflictedMeetings()
     * @see ClientFacade#clearErrorMeetings()
     */
    public void correctConflictedMeetings() {
        LinkedList<Meeting> meetingsWithError = facade.getConflictedMeetings();

        if (meetingsWithError == null) {
            System.out.println("Failed to fetch meetings. Please try again later.");
            return;
        }
        if (meetingsWithError.isEmpty()) {
            System.out.println("No conflicted meetings found.");
            return;
        }

        System.out.println("\nYour Conflicted Meetings:");
        System.out.println("-------------");
        System.out.println("Please enter the meeting number to correct the conflicted meeting");
        System.out.println("Enter -1 to exit");
        List<Meeting> filteredMeetingsForOnlyOrganizedOnes = this.facade.getConflictedMeetings()
                .stream()
                .filter(new Predicate<Meeting>() {
                    @Override
                    public boolean test(Meeting meeting) {
                        return meeting.getOrganizer().getEmployeeID()
                                .equals(facade.getCurrentEmployee().getEmployeeID());
                    }
                }).toList();
        int counter = 0;
        for (Meeting meeting : filteredMeetingsForOnlyOrganizedOnes) {
            System.out.println((counter + 1) + ". " + meeting.getMeetingTopic());
            System.out.println("   Place: " + meeting.getPlace());
            System.out.println("   Start: " + dateFormat.format(meeting.getStartTime()));
            System.out.println("   End: " + dateFormat.format(meeting.getEndTime()));
            System.out.println("   Organizer: " + meeting.getOrganizer().getEmployeeFullName());
            System.out.println("   Invitees: " + formatInvitees(meeting.getInvitees()));
            System.out.println();
            counter++;
        }

        int choice = getIntInput("Enter meeting number (1-" + counter  +")" + ", -1 to cancel): ",
                -1, counter);
        if (choice == -1) {
            System.out.println("Correction cancelled.");
            return;
        }
        if (extractMeetingFromListOfMeetingsAndUpdateDetailsBasedOnIndex(filteredMeetingsForOnlyOrganizedOnes, choice - 1)){
            System.out.println("Meeting updated successfully.");
            facade.clearErrorMeetings();
        } else {
            System.out.println("Failed to update meeting. Please try again later.");
        }
    }


    /**
     * Creates a new meeting in the system with the specified details provided by the user.
     *
     * <p>
     * This method guides the user through the process of creating a new meeting by prompting for: -
     * Meeting topic - Meeting place - Start time - End time - List of invitees
     * </p>
     *
     * <p>
     * The method performs validation to ensure: - Start and end times are valid dates - End time is
     * after start time - All required fields are provided
     * </p>
     *
     * <p>
     * If any validation fails or the user cancels during date input, the meeting creation is
     * aborted. After successful validation, the meeting is created through the ClientFacade.
     * </p>
     *
     * @throws RuntimeException if there's an error communicating with the server
     * @throws ParseException   if the provided date format is invalid
     * @see Meeting
     * @see ClientFacade#createMeeting(String, String, Date, Date, List)
     */
    private void createMeeting() {
        try {
            System.out.println("\nCreate New Meeting");
            System.out.println("-----------------");

            String topic = getStringInput("Enter meeting topic: ");
            String place = getStringInput("Enter meeting place: ");

            Date startTime = getDateInput("Enter start time (yyyy-MM-dd HH:mm): ");
            if (startTime == null) return;

            Date endTime = getDateInput("Enter end time (yyyy-MM-dd HH:mm): ");
            if (endTime == null) return;

            if (endTime.before(startTime) || endTime.equals(startTime)) {
                System.out.println("End time must be after start time. Meeting creation cancelled.");
                return;
            }

            List<Employee> invitees = getInviteesList();

            System.out.println("\nCreating meeting...");
            boolean created = facade.createMeeting(topic, place, startTime, endTime, invitees);

            if (created) {
                System.out.println("Meeting created successfully.");
            } else {
                System.out.println("Failed to create meeting. Please try again later.");
            }
        } catch (Exception exception) {
            System.out.println("Failed to create meeting. Please try again later.");
            System.out.println(exception.getMessage());
        }
    }


    /**
     * Updates an existing meeting's details in the system.
     *
     * <p>
     * This method allows users to modify the details of meetings they have organized. It first
     * retrieves all meetings from the server and filters them to show only those where the current
     * user is the organizer.
     * </p>
     *
     * <p>
     * For each meeting that can be updated, it displays: - Meeting topic - Meeting start time
     * </p>
     *
     * <p>
     * After selecting a meeting, users can update: - Start time - End time - List of invitees
     * </p>
     *
     * <p>
     * The method performs validation to ensure: - The meeting exists in the system - The user is
     * the organizer of the meeting - The new end time is after the new start time
     * </p>
     *
     * @throws RuntimeException if there's an error communicating with the server
     * @throws ParseException   if the provided date format is invalid
     * @see Meeting
     * @see ClientFacade#getMeetings()
     * @see ClientFacade#updateMeeting(Meeting)
     */
    private void updateMeeting() {
        System.out.println("\nFetching meetings...");
        List<Meeting> meetings = facade.getMeetings();

        if (meetings == null) {
            System.out.println("Failed to fetch meetings. Please try again later.");
            return;
        }

        if (meetings.isEmpty()) {
            System.out.println("No meetings found to update.");
            return;
        }

        System.out.println("\nSelect a meeting to update:");
        int meetingCounter = 0;
        for (int i = 0; i < meetings.size(); i++) {
            Meeting meeting = meetings.get(i);
            if (meeting.getOrganizer().getEmployeeID().equals(this.facade.getCurrentEmployee().getEmployeeID())) {
                System.out.println((meetingCounter + 1) + ". " + meeting.getMeetingTopic() + " (" +
                                           dateFormat.format(meeting.getStartTime()) + ")");
                meetingCounter++;
            }
        }

        int meetingIndex = getIntInput("Enter meeting number (0 to cancel): ", 0, meetingCounter) - 1;
        if (meetingIndex == -1) {
            System.out.println("Update cancelled.");
            return;
        }

        extractMeetingFromListOfMeetingsAndUpdateDetailsBasedOnIndex(meetings, meetingIndex);

    }

    /**
     * Extracts a meeting from a list based on the provided index and facilitates updating its
     * details.
     *
     * <p>
     * This method handles the process of updating an existing meeting's details. It displays the
     * current meeting information and allows the user to modify: - Start time - End time - List of
     * invitees
     * </p>
     *
     * <p>
     * The method provides validation for the following: - Date format validation for start and end
     * times - Ensures end time is after start time - Allows optional updates by letting users skip
     * fields by leaving them blank
     * </p>
     *
     * <p>
     * After collecting the updated information, the method attempts to update the meeting through
     * the client facade. If the update is successful, it returns true; otherwise, it returns
     * false.
     * </p>
     *
     * @param meetings     The list of meetings from which to extract the meeting to update
     * @param meetingIndex The index of the meeting to update in the provided list
     * @return Boolean indicating whether the update was successful (true) or failed (false)
     * @throws IndexOutOfBoundsException if the meetingIndex is invalid for the provided list
     * @throws ParseException            if the provided date strings cannot be parsed
     * @throws IllegalArgumentException  if the end time is before or equal to the start time
     * @see Meeting
     * @see ClientFacade#updateMeeting(Meeting)
     */
    private Boolean extractMeetingFromListOfMeetingsAndUpdateDetailsBasedOnIndex(List<Meeting> meetings, int meetingIndex) {
        Meeting meetingToUpdate = meetings.get(meetingIndex);

        System.out.println("\nUpdate Meeting: " + meetingToUpdate.getMeetingTopic());
        System.out.println("Current details:");
        System.out.println("- Topic: " + meetingToUpdate.getMeetingTopic());
        System.out.println("- Place: " + meetingToUpdate.getPlace());
        System.out.println("- Start: " + dateFormat.format(meetingToUpdate.getStartTime()));
        System.out.println("- End: " + dateFormat.format(meetingToUpdate.getEndTime()));
        System.out.println("- Invitees: " + formatInvitees(meetingToUpdate.getInvitees()));


        String startTimeStr = getStringInput("Enter new start time (yyyy-MM-dd HH:mm) or leave blank: ");
        if (!startTimeStr.isBlank()) {
            try {
                Date startTime = dateFormat.parse(startTimeStr);
                meetingToUpdate.setStartTime(startTime);
            } catch (ParseException e) {
                System.out.println("Invalid date format. Start time not updated.");
                return false;
            }
        }

        String endTimeStr = getStringInput("Enter new end time (yyyy-MM-dd HH:mm) or leave blank: ");
        if (!endTimeStr.isBlank()) {
            try {
                Date endTime = dateFormat.parse(endTimeStr);
                meetingToUpdate.setEndTime(endTime);
            } catch (ParseException e) {
                System.out.println("Invalid date format. End time not updated.");
                return false;
            }
        }

        if (meetingToUpdate.getEndTime().before(meetingToUpdate.getStartTime()) ||
                meetingToUpdate.getEndTime().equals(meetingToUpdate.getStartTime())) {
            System.out.println("End time must be after start time. Meeting update cancelled.");
            return false;
        }

        String updateInvitees = getStringInput("Update invitees? (y/n): ");
        if (updateInvitees.equalsIgnoreCase("y")) {
            List<Employee> invitees = getInviteesList();
            meetingToUpdate.setInvitees(invitees);
        }

        System.out.println("\nUpdating meeting...");
        boolean updated = facade.updateMeeting(meetingToUpdate);

        if (updated) {
            System.out.println("Meeting updated successfully.");
            return true;
        } else {
            System.out.println("Failed to update meeting. Please try again later.");
            return false;
        }
    }

    /**
     * Reviews and displays all meetings that have scheduling conflicts.
     *
     * <p>
     * This method retrieves a list of all meetings with scheduling conflicts from the server
     * through the client facade. A meeting is considered conflicted if it overlaps in time with
     * another meeting where the same person is invited or is the organizer.
     * </p>
     *
     * <p>
     * For each conflicted meeting, the method displays detailed information including: - The
     * meeting organizer's name and ID - Meeting topic - Meeting place - Start time - End time -
     * List of invited participants
     * </p>
     *
     * <p>
     * If there are no conflicted meetings or if the retrieval fails, appropriate messages are
     * displayed to the user.
     * </p>
     *
     * @throws RuntimeException if there's an error while communicating with the server
     * @see Meeting
     * @see ClientFacade#getConflictedMeetings()
     */
    public void reviewConflictedMeetings() {
        System.out.println("\nFetching Conflicted Meetings...");
        LinkedList<Meeting> meetings = facade.getConflictedMeetings();

        if (meetings == null) {
            System.out.println("Failed to fetch conflicted meetings. There might not be any " +
                                       "meetings to print here");
            return;
        }

        if (meetings.isEmpty()) {
            System.out.println("There are no conflicted meetings...");
            return;
        }

        System.out.println("\nYour Conflicted Meetings:");
        System.out.println("-------------");
        int counter = 0;
        for (int i = 0; i < meetings.size(); i++) {
            Meeting meeting = meetings.get(i);
            System.out.println("\n" + (counter + 1) + ". Current details:");
            System.out.println("- Organizer: " + meeting.getOrganizer().getEmployeeFullName() +
                                       "(" + meeting.getOrganizer().getEmployeeID() + ")");
            System.out.println("- Topic: " + meeting.getMeetingTopic());
            System.out.println("- Place: " + meeting.getPlace());
            System.out.println("- Start: " + dateFormat.format(meeting.getStartTime()));
            System.out.println("- End: " + dateFormat.format(meeting.getEndTime()));
            System.out.println("- Invitees: " + formatInvitees(meeting.getInvitees()));
            counter++;

        }
    }


    /**
     * Deletes a meeting from the system that the current user has organized.
     *
     * <p>
     * This method retrieves all meetings from the server and filters them to show only those where
     * the current user is the organizer. It then allows the user to select a meeting to delete from
     * this filtered list.
     * </p>
     *
     * <p>
     * The deletion process includes the following steps:
     * <ul>
     * <li>Fetching and displaying all meetings organized by the current user</li>
     * <li>Allowing the user to select a meeting to delete</li>
     * <li>Requesting confirmation before deletion</li>
     * <li>Attempting to delete the meeting through the client facade</li>
     * </ul>
     * </p>
     *
     * <p>
     * The method includes several validation steps:
     * <ul>
     * <li>Checks if meetings can be retrieved from the server</li>
     * <li>Verifies that there are meetings available to delete</li>
     * <li>Ensures the selected meeting index is valid</li>
     * <li>Requires explicit confirmation before deletion</li>
     * </ul>
     * </p>
     *
     * @throws RuntimeException if there's an error communicating with the server
     * @see Meeting
     * @see ClientFacade#getMeetings()
     * @see ClientFacade#deleteMeeting(Meeting)
     */
    private void deleteMeeting() {
        System.out.println("\nFetching meetings...");
        List<Meeting> meetings = facade.getMeetings();

        if (meetings == null) {
            System.out.println("Failed to fetch meetings. Please try again later.");
            return;
        }

        if (meetings.isEmpty()) {
            System.out.println("No meetings found to delete.");
            return;
        }

        System.out.println("\nSelect a meeting to delete:");
        int counter = 0;
        for (int i = 0; i < meetings.size(); i++) {
            Meeting meeting = meetings.get(i);
            if (meeting.getOrganizer().getEmployeeID().equals(this.facade.getCurrentEmployee().getEmployeeID())) {
                System.out.println((counter + 1) + ". " + meeting.getMeetingTopic() + " (" +
                                           dateFormat.format(meeting.getStartTime()) + ")");
                counter++;
            }
        }

        int meetingIndex = getIntInput("Enter meeting number (0 to cancel): ", 0, counter) - 1;
        if (meetingIndex == -1) {
            System.out.println("Deletion cancelled.");
            return;
        }

        Meeting meetingToDelete = meetings.get(meetingIndex);

        String confirm = getStringInput("Are you sure you want to delete the meeting \"" +
                                                meetingToDelete.getMeetingTopic() + "\"? (y/n): ");

        if (!confirm.equalsIgnoreCase("y")) {
            System.out.println("Deletion cancelled.");
            return;
        }

        System.out.println("\nDeleting meeting...");
        boolean deleted = facade.deleteMeeting(meetingToDelete);

        if (deleted) {
            System.out.println("Meeting deleted successfully.");
        } else {
            System.out.println("Failed to delete meeting. Please try again later.");
        }
    }

    /**
     * Show help information
     */
    private void showHelp() {
        System.out.println("\nViewMyMeetings Client Help");
        System.out.println("-------------------------");
        System.out.println("This application allows you to manage your meetings.");
        System.out.println("You can create, update, delete, and list meetings.");
        System.out.println("\nMain Menu Options:");
        System.out.println("1. List Meetings - View all your meetings");
        System.out.println("2. Create Meeting - Create a new meeting");
        System.out.println("3. Update Meeting - Modify an existing meeting");
        System.out.println("4. Delete Meeting - Remove a meeting");
        System.out.println("5. Help - Display this help information");
        System.out.println("6. Review Conflicted Meetings - View all conflicted meetings");
        System.out.println("7. Correct Meeting Information - Correct meeting information");
        System.out.println("0. Exit - Close the application");
        System.out.println("\nDate Format: yyyy-MM-dd HH:mm (e.g., 2025-04-21 14:30)");
    }

    /**
     * Exit the application
     */
    private void exit() {
        System.out.println("\nExiting application...");
        facade.disconnect();
        running = false;
    }

    /**
     * Get a list of invitees for a meeting
     *
     * @return The list of invitees
     */
    private List<Employee> getInviteesList() {
        List<Employee> invitees = new ArrayList<>();

        System.out.println("\nAdd invitees (enter blank ID to finish):");

        while (true) {
            String inviteeId = getStringInput("Enter invitee ID: ");
            if (inviteeId.isBlank()) {
                break;
            }

            String inviteeName = getStringInput("Enter invitee name: ");
            invitees.add(new Employee(inviteeName, inviteeId));

            System.out.println("Invitee added. Current invitees: " + formatInvitees(invitees));
        }

        return invitees;
    }

    /**
     * Format a list of invitees for display
     *
     * @param invitees The list of invitees
     * @return A formatted string of invitees
     */
    private String formatInvitees(List<Employee> invitees) {
        if (invitees.isEmpty()) {
            return "None";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < invitees.size(); i++) {
            Employee invitee = invitees.get(i);
            sb.append(invitee.getEmployeeFullName())
                    .append(" (")
                    .append(invitee.getEmployeeID())
                    .append(")");

            if (i < invitees.size() - 1) {
                sb.append(", ");
            }
        }

        return sb.toString();
    }

    /**
     * Get a string input from the user
     *
     * @param prompt The prompt to display
     * @return The user's input
     */
    private String getStringInput(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    /**
     * Get a string input from the user with a default value
     *
     * @param prompt       The prompt to display
     * @param defaultValue The default value to use if the input is blank
     * @return The user's input or the default value
     */
    private String getStringInputWithDefault(String prompt, String defaultValue) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        return input.isBlank() ? defaultValue : input;
    }

    /**
     * Get an integer input from the user within a specified range
     *
     * @param prompt The prompt to display
     * @param min    The minimum valid value
     * @param max    The maximum valid value
     * @return The user's input
     */
    private int getIntInput(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                String input = scanner.nextLine().trim();
                int value = Integer.parseInt(input);

                if (value >= min && value <= max) {
                    return value;
                } else {
                    System.out.println("Please enter a number between " + min + " and " + max + ".");
                }
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    /**
     * Get a date input from the user
     *
     * @param prompt The prompt to display
     * @return The parsed date, or null if parsing failed
     */
    private Date getDateInput(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();

            try {
                return dateFormat.parse(input);
            } catch (ParseException e) {
                System.out.println("Invalid date format. Please use yyyy-MM-dd HH:mm (e.g., 2025-04-21 14:30).");
                String retry = getStringInput("Try again? (y/n): ");
                if (!retry.equalsIgnoreCase("y")) {
                    return null;
                }
            }
        }
    }
}