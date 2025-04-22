package com.evolvlabs.SerializationEngine;

import com.evolvlabs.DataModel.Employee;
import com.evolvlabs.DataModel.Meeting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : Santiago Arellano
 * @date : 21st-Apr-2025
 * @description : The presente file implements a series of static methods, along with internal
 * static classes used to handle serialization of single message instances over to JSON that can
 * be then used to chain into a compression method through MessagePack. The idea of this class is
 * to present a streamline way of reading both Lists of Meetings as well as single meetings, as
 * these are the two ways of data moving from the client to the server.
 */
public class ToJsonSerializer {

    //? 1. We begin by building a deserializer that is capable of both reading and writing to
    // both types of objects, just in case
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Meeting.class, new MeetingTypeAdapter())
            .registerTypeAdapter(List.class, new MeetingListTypeAdapter())
            .create();

    //? Now we provide methods, static ones, for the applicaiton to access these deserialization
    // techniques
    public static String serializeSingleMeeting(Meeting meeting){
        if (meeting != null){
            return gson.toJson(meeting);
        }
        return "";
    }

    public static Meeting deserializeSingleMeeting(String serializedMeeting){
        if (serializedMeeting != null && !serializedMeeting.isEmpty()){
            return gson.fromJson(serializedMeeting, new TypeToken<Meeting>(){}.getType());
        }
        return null;
    }

    public static String serializeMultipleMeetings(List<Meeting> meetings){
        if (meetings != null && !meetings.isEmpty()){
            return gson.toJson(meetings);
        }

        return "";
    }

    public static List<Meeting> deserializeMultipleMeetings(String serializedMeetings){
        if (serializedMeetings != null && !serializedMeetings.isEmpty()){
            return gson.fromJson(serializedMeetings,
                                 new TypeToken<List<Meeting>>(){}.getType());
        }

        return null;
    }



    public static class MeetingTypeAdapter extends TypeAdapter<Meeting>{

        @Override
        public void write(JsonWriter jsonWriter, Meeting meeting) throws IOException {
            //? 1. AL revisar, debemos tener en cuent si tenemos informacion correcta
            if (meeting == null){
                jsonWriter.nullValue();
                return;
            }

            //? 2. If not, we begin by serializing and producing a coherent json format,
            // beginning by serializing an object
            jsonWriter.beginObject();

            //? 2.1 Now we begin to write different values of our class, specially each field in
            // an order that is comprehensive
            jsonWriter.name("meetingTopic");
            jsonWriter.value(meeting.getMeetingTopic());
            jsonWriter.name("meetingOrganizer");
            jsonWriter.beginObject();
            jsonWriter.name("meetingOrganizerID");
            jsonWriter.value(meeting.getOrganizer().getEmployeeID());
            jsonWriter.name("meetingOrganizerName");
            jsonWriter.value(meeting.getOrganizer().getEmployeeFullName());
            jsonWriter.endObject();
            jsonWriter.name("meetingPlace");
            jsonWriter.value(meeting.getPlace());
            jsonWriter.name("meetingInviteeList");
            jsonWriter.beginArray();
            for(Employee invitee: meeting.getInvitees()){
                jsonWriter.beginObject();
                jsonWriter.name("meetingInviteeID");
                jsonWriter.value(invitee.getEmployeeID());
                jsonWriter.name("meetingInviteeName");
                jsonWriter.value(invitee.getEmployeeFullName());
                jsonWriter.endObject();
            }
            jsonWriter.endArray();
            jsonWriter.name("meetingStartTime");
            jsonWriter.value(meeting.getStartTime().getTime());
            jsonWriter.name("meetingEndTime");
            jsonWriter.value(meeting.getEndTime().getTime());
            jsonWriter.endObject();
        }

        @Override
        public Meeting read(JsonReader jsonReader) throws IOException {
            if (jsonReader.peek() == JsonToken.NULL) {
                jsonReader.nextNull();
                return null;  // Return null instead of default meeting
            }

            String meetingTopic = null;
            Employee organizer = null;
            String place = null;
            Date startTime = null;
            Date endTime = null;
            List<Employee> invitees = new ArrayList<>();

            jsonReader.beginObject();
            while (jsonReader.hasNext()) {
                String name = jsonReader.nextName();
                switch (name) {
                    case "meetingTopic" -> meetingTopic = jsonReader.nextString();
                    case "meetingOrganizer" -> {
                        jsonReader.beginObject();
                        String orgId = null;
                        String orgName = null;
                        while (jsonReader.hasNext()) {
                            String orgField = jsonReader.nextName();
                            switch (orgField) {
                                case "meetingOrganizerID" -> orgId = jsonReader.nextString();
                                case "meetingOrganizerName" -> orgName = jsonReader.nextString();
                            }
                        }
                        organizer = new Employee(orgName, orgId);
                        jsonReader.endObject();
                    }
                    case "meetingPlace" -> place = jsonReader.nextString();
                    case "meetingInviteeList" -> {
                        jsonReader.beginArray();
                        while (jsonReader.hasNext()) {
                            jsonReader.beginObject();
                            String inviteeId = null;
                            String inviteeName = null;
                            while (jsonReader.hasNext()) {
                                String inviteeField = jsonReader.nextName();
                                switch (inviteeField) {
                                    case "meetingInviteeID" -> inviteeId = jsonReader.nextString();
                                    case "meetingInviteeName" ->
                                            inviteeName = jsonReader.nextString();
                                }
                            }
                            invitees.add(new Employee(inviteeName, inviteeId));
                            jsonReader.endObject();
                        }
                        jsonReader.endArray();
                    }
                    case "meetingStartTime" -> startTime = new Date(jsonReader.nextLong());
                    case "meetingEndTime" -> endTime = new Date(jsonReader.nextLong());
                }
            }
            jsonReader.endObject();

            // Validate all required fields are present
            if (meetingTopic == null || organizer == null || place == null ||
                    startTime == null || endTime == null) {
                throw new IOException("Missing required fields in Meeting JSON");
            }

            try {
                return new Meeting(meetingTopic, organizer, invitees, place, startTime, endTime);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid meeting data: " + e.getMessage());
            }
        }
    }

    public static class MeetingListTypeAdapter extends TypeAdapter<List<Meeting>>{

        private final MeetingTypeAdapter adapterForMeetings = new MeetingTypeAdapter();

        /**
         * Writes one JSON value (an array, object, string, number, boolean or null) for
         * {@code value}.
         *
         * @param out
         * @param value the Java object to write. May be null.
         */
        @Override
        public void write(JsonWriter out, List<Meeting> value) throws IOException {
            //? Although I do not expect to have to write ever, a multi-meeting list to the
            // server it is nevertheless important to have both methods implemented
            if (value == null){
                out.nullValue();
                return;
            }

            out.beginArray();
            for (Meeting meeting: value){
                if (meeting != null) {
                    adapterForMeetings.write(out, meeting);
                } else {
                    out.nullValue();
                }
            }
            out.endArray();
        }

        /**
         * Reads one JSON value (an array, object, string, number, boolean or null) and converts it
         * to a Java object. Returns the converted object.
         *
         * @param in
         * @return the converted Java object. May be {@code null}.
         */
        @Override
        public List<Meeting> read(JsonReader in) throws IOException {
            //? 1. In this case we also review to see if we, for some reason, received null values
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            //? 2. If by a miracle we did not get there, we now begin to deserialize everything
            // making use of the singular serializer.
            List<Meeting> meetings = new ArrayList<>();

            in.beginArray();
            while(in.hasNext()){
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    continue;
                }
                Meeting meeting = adapterForMeetings.read(in);
                if (meeting != null){
                    meetings.add(meeting);
                }
            }
            in.endArray();

            return meetings;
        }
    }
}
