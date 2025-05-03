package com.evolvlabs.UI;

import com.evolvlabs.DataModel.Employee;
import com.evolvlabs.DataModel.Meeting;
import com.evolvlabs.Network.ViewMyMeetingsClient;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * @author : Santiago Arellano
 * @date : 21st-Apr-2025
 * @description : Facade class that abstracts the client-server communication.
 * This class provides a simplified interface for the UI to interact with the server.
 */
public class ClientFacade {
    private final ViewMyMeetingsClient client;
    private boolean isConnected = false;
    private boolean isAuthenticated = false;

    /**
     * Constructor that initializes the client with default host and port
     */
    public ClientFacade() {
        this.client = new ViewMyMeetingsClient();
    }

    /**
     * Constructor that initializes the client with specified host and port
     * @param host The server host
     * @param port The server port
     */
    public ClientFacade(String host, int port) {
        this.client = new ViewMyMeetingsClient(host, port);
    }

    public ClientFacade(Properties config) {
        this.client = new ViewMyMeetingsClient(config);
    }

    /**
     * Connect to the server
     * @return true if the connection was successful, false otherwise
     */
    public boolean connect() {
        if (isConnected) {
            return true;
        }
        
        isConnected = client.connect();
        return isConnected;
    }

    /**
     * Authenticate with the server using the loaded credentials
     * @return true if authentication was successful, false otherwise
     */
    public boolean authenticate() {
        if (!isConnected) {
            System.err.println("Not connected to server");
            return false;
        }
        
        if (isAuthenticated) {
            return true;
        }
        
        isAuthenticated = client.authenticate();
        return isAuthenticated;
    }

    /**
     * Authenticate with the server using specified credentials
     * @param employeeId The employee ID
     * @param password The password
     * @return true if authentication was successful, false otherwise
     */
    public boolean authenticate(String employeeId, String employeeName, String password) {
        if (!isConnected) {
            System.err.println("Not connected to server");
            return false;
        }
        
        if (isAuthenticated) {
            return true;
        }
        
        Employee employee = new Employee(employeeName, employeeId);
        isAuthenticated = client.authenticate(employee, password);
        return isAuthenticated;
    }

    /**
     * Create a meeting
     * @param topic The meeting topic
     * @param place The meeting place
     * @param startTime The meeting start time
     * @param endTime The meeting end time
     * @param invitees The list of invitees
     * @return true if the meeting was created successfully, false otherwise
     */
    public boolean createMeeting(String topic, String place, java.util.Date startTime, 
                                java.util.Date endTime, List<Employee> invitees) {
        if (!isAuthenticated) {
            System.err.println("Not authenticated");
            return false;
        }
        
        Meeting meeting = new Meeting(
            topic,
            client.getCurrentEmployee(),
            invitees,
            place,
            startTime,
            endTime
        );
        
        return client.createMeeting(meeting);
    }

    /**
     * Update a meeting
     * @param meeting The meeting to update
     * @return true if the meeting was updated successfully, false otherwise
     */
    public boolean updateMeeting(Meeting meeting) {
        if (!isAuthenticated) {
            System.err.println("Not authenticated");
            return false;
        }
        
        return client.updateMeeting(meeting);
    }

    /**
     * Delete a meeting
     * @param meeting The meeting to delete
     * @return true if the meeting was deleted successfully, false otherwise
     */
    public boolean deleteMeeting(Meeting meeting) {
        if (!isAuthenticated) {
            System.err.println("Not authenticated");
            return false;
        }
        
        return client.deleteMeeting(meeting);
    }

    /**
     * Get all meetings for the current employee
     * @return A list of meetings, or null if an error occurred
     */
    public List<Meeting> getMeetings() {
        if (!isAuthenticated) {
            System.err.println("Not authenticated");
            return null;
        }
        
        return client.getMeetings();
    }

    public LinkedList<Meeting> getConflictedMeetings(){
        if (!isAuthenticated){
            System.err.println("Not Authenticated");
            return null;
        }

        return client.getOnErrorMeetingsLinkedList();
    }

    /**
     * Disconnect from the server
     */
    public void disconnect() {
        if (isConnected) {
            client.disconnect();
            isConnected = false;
            isAuthenticated = false;
        }
    }

    public void clearErrorMeetings(){
        if (!isAuthenticated){
            System.err.println("Not Authenticated");
            return;
        }

        client.clearOnErrorMeetingsLinkedList();
    }

    /**
     * Check if the client is connected to the server
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Check if the client is authenticated
     * @return true if authenticated, false otherwise
     */
    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    /**
     * Get the current employee
     * @return The current employee
     */
    public Employee getCurrentEmployee() {
        return client.getCurrentEmployee();
    }
}