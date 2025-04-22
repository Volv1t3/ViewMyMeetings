package com.evolvlabs.DataModel;

/**
 * @author : Santiago Arellano
 * @date : 21st-Apr-2025
 * @description : The present file implements a simple communication protocol for client server and
 * server client to adhere to. Rather than forcing updates to be sent over simple strings, the
 * strings presented here are wrapped over this enum to make working with them easier through a
 * simulated HTTP protocol.
 */
public enum CommunicationProtocol {



    /*The following are all communication protocol constantes defined for this application*/

    //? 1. Client to Server Communication Path
    /**
     * @description : this communication standard is used by the client, and subsequently read by
     * the serer to know that the message after this is the client sending their credentials.
     */
    POST_CLIENTSIDE_AUTH_REQUEST("POST_CLIENTSIDE_AUTH_REQUEST"),
    /**
     * @description : this communication standard is used by the server, and subsequently read by
     * the client as an informativa reply that informs them that the next message contains a
     * boolean indicating whether the credentials were accepted or not.
     */
    POST_SERVERSIDE_AUTH_RESPONSE("POST_SERVERSIDE_AUTH_RESPONSE"),
    /**
     * @description : this communication standard is used by the client, and subsequently read by
     * the server to know that the message after this is the client sending their meeting
     * creation update
     */
    PUSH_CLIENTSIDE_MEETING_CREATION_REQUEST("PUSH_CLIENTSIDE_MEETING_CREATION_REQUEST"),
    /**
     * @description : this communication standard is used by the server, and subsequently read by
     * the client to know that the message after this is the server sending their meeting
     * creation acknowledge, a boolean representing if it was successful or not. If the boolean
     * was negative, then the client can be sure that there will be a forced update message by
     * the server.
     */
    PUSH_SERVERSIDE_MEETING_CREATION_RESPONSE("PUSH_SERVERSIDE_MEETING_CREATION_RESPONSE"),
    /**
     * @description : this communication standard is used by the client, and subsequently read by
     * the server to know that the message after this is the client sending their meeting update
     * information. This will most often be used after the server has informed the client that
     * the system cannot possibly get the meeting in the schedule that is asked
     */
    PUSH_CLIENTSIDE_MEETING_UPDATE_REQUEST("PUSH_CLIENTSIDE_MEETING_UPDATE_REQUEST"),
    /**
     * @description : this communication standard is used by the server, and subsequently read by
     * the client to inform the client that their update response was received, after this the
     * client can expect a boolean that represents wheter their update was correct, or another
     * forced update if not.
     */
    PUSH_SERVERSIDE_MEETING_UPDATE_RESPONSE("PUSH_SERVERSIDE_MEETING_UPDATE_RESPONSE"),
    /**
     * @description : this communication standard is used by the client, and subsequently read by
     * the server to inform the server that the client has requested one meeting be deleted from
     * their schedule.
     */
    PUSH_CLIENTSIDE_MEETING_DELETION_REQUEST("PUSH_CLIENTSIDE_MEETING_DELETION_REQUEST"),
    /**
     * @description : this communication standard is used by the server, and subsequently read by
     * the client informing them that the server has acknowledge their meeting removal request.
     */
    PUSH_SERVERSIDE_MEETING_DELETION_RESPONSE("PUSH_SERVERSIDE_MEETING_DELETION_RESPONSE"),

    /**
     * @description : Communication constant taht s used by the client to inform the server that
     * there is a need for it to return a given meetings information per its id, i.e., the client
     * ID, segregated into two groups, both participant and creator meetings. This ID that will
     * be passed will be the client's id not the meeting id.
     */
    GET_SERVERSIDE_MEETING_INFORMATION_BY_ID_REQUEST(
            "POST_SERVERSIDE_MEETING_INFORMATION_BY_ID_REQUEST"),
    /**
     * @description : Communication constant that informs the client, through the main client ->
     * server communication line, that their request has been acknowledged, and that after this
     * message they should expect a list of meetings, in both groups, formed in json.
     */
    GET_SERVERSIDE_MEETING_INFORMATION_BY_ID_RESPONSE(
            "POST_SERVERSIDE_MEETING_INFORMATION_BY_ID_RESPONSE"),

    //? 2. Server Client Communication Path
    /**
     * @description : this communication standard is used by the server, and subsequently read by
     * the client to inform them that the server has detected incoherences between the clients
     * meetings. This then causes the system to send the meeting information to that client to
     * fix it.
     */
    PUSH_SERVERSIDE_MEETING_CONFLICT_UPDATE_REQUEST("PUSH_SERVERSIDE_MEETING_CONFLICT_UPDATE_REQUEST"),
    /**
     * @description : this communication standard is used by the serer, and susebquently read by
     * the client to update their meeting information by polling for the information that is
     * stored within the system again.
     */
    PUSH_SERVERSIDE_MEETING_DELETION_NOTIFICATION("PUSH_SERVERSIDE_MEETING_DELETION_NOTIFICATION");


    private final String internalMessageAsString;

    private CommunicationProtocol(String internalMessageAsString) {
        this.internalMessageAsString = internalMessageAsString;
    }


}
