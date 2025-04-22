package com.evolvlabs.DataModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

/**
 * @author : Marcos Lopez, Santiago Arellano
 * @date : 21st-Apr-2025
 * @description : The following is a simple implementation of a meeting object that will be used to
 * transform the info from the client, and store in a format that is readable and serializable to
 * the server. The idea is that the meeting should implement all parameters required, from topic, to
 * names of the invitees (which in our case will be mapped to a client ID), organizer Name (ID when
 * stored); place where the meeting is taking place, as well as the date and time for start and end
 * of the meeting. The data stored here will come from deserialized JSON files that are sent from
 * the client as they create meetings, therefore, as meetings as created, they are put into a
 * concurrent data structure. At any moment if a meeting overlaps with another one already defined,
 * then we are going to handle this by informing the client of the meeting to fix it on its own.
 */
public class Meeting implements Comparable<Meeting>{

    /*! Class Parameters*/
    private String meetingTopic;
    private Employee organizer;
    private List<Employee> invitees;
    private String place;
    private Date startTime;
    private Date endTime;

    /*! Class Constructors*/
    public Meeting(String externalMeetingTopic,
                   Employee externalOrganizer,
                   List<Employee> externalInvitees,
                   String externalPlace,
                   Date externalStartTime,
                   Date externalEndTime) throws IllegalArgumentException{
        this.setMeetingTopic(externalMeetingTopic);
        this.setOrganizer(externalOrganizer);
        this.setInvitees(externalInvitees);
        this.setPlace(externalPlace);
        this.setStartTime(externalStartTime);
        this.setEndTime(externalEndTime);
        this.validateMeetingTimes();
    }

    public Meeting(String externalMeetingTopic,
                   Employee externalOrganizer,
                   List<Employee> externalInvitees) throws IllegalArgumentException{
        this.setMeetingTopic(externalMeetingTopic);
        this.setOrganizer(externalOrganizer);
        this.setInvitees(externalInvitees);
        this.setPlace("null");
        this.setStartTime(Date.from(Instant.now()));
        this.setEndTime(Date.from(Instant.now().plusSeconds(3600)));
    }

    public Meeting(){
        this.setMeetingTopic("null");
        this.setOrganizer(new Employee("null", "null"));
        this.setInvitees(new ArrayList<>());
        this.setPlace("null");
        this.setStartTime(Date.from(Instant.now()));
        this.setEndTime(Date.from(Instant.now().plusSeconds(3600)));
        this.validateMeetingTimes();
    }


    /*! Setters y Getters*/
    public void setMeetingTopic(String meetingTopic) throws IllegalArgumentException {
        if (meetingTopic != null && !meetingTopic.isEmpty()) {
            this.meetingTopic = meetingTopic;
        } else {
            throw new IllegalArgumentException("Error Code 0x001 - [Raised] A Meeting's topic " +
                                                       "cannot be null during instance creation.");
        }
    }

    public String getMeetingTopic() {
        return this.meetingTopic;
    }

    public void setOrganizer(Employee organizer) throws IllegalArgumentException{
        if (organizer != null) {
            this.organizer = organizer;
        } else {
            throw new IllegalArgumentException("Error Code 0x001 - [Raised] A Meeting's organizer" +
                                                       "cannot be null during instance creation.");
        }
    }

    public Employee getOrganizer() {
        return this.organizer;
    }

    public void setInvitees(List<Employee> employees) throws IllegalArgumentException{
        if (employees != null) {
            boolean creatorInInvitees = employees.stream().anyMatch(employee ->
                                                                            employee.getEmployeeID()
                                                                                    .equals(this.organizer.getEmployeeID()));
            if (creatorInInvitees) {
                throw new IllegalArgumentException("Error Code 0x001 - [Raised] A Meeting's " +
                                                           "creator" +
                                                           "cannot be included in its invitees.");
            }
            this.invitees = employees;
        } else {
            throw new IllegalArgumentException("Error Code 0x001 - [Raised] A Meeting's invitees" +
                                                       "cannot be null during instance creation.");
        }
    }

    public List<Employee> getInvitees() {
        return this.invitees;
    }

    public void setPlace(String place) {
        if (place != null && !place.isEmpty()){
            this.place = place;
        } else {
            throw new IllegalArgumentException("Error Code 0x001 - [Raised] A Meeting's place " +
                                                       "cannot be null during instance creation.");
        }
    }

    public String getPlace() {
        return this.place;
    }

    public void setStartTime(Date startTime) throws IllegalArgumentException{
        if (startTime != null) {
                this.startTime = startTime;
        } else {
            throw new IllegalArgumentException("Error Code 0x001 - [Raised] A Meeting's start time " +
                                                       "cannot be null during instance creation.");
        }
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setEndTime(Date endTime) throws IllegalArgumentException{
        if (endTime != null) {
            this.endTime = endTime;
        } else {
            throw new IllegalArgumentException("Error Code 0x001 - [Raised] A Meeting's end time " +
                                                       "cannot be null during instance creation.");
        }
    }

    public Date getEndTime() {
        return endTime;
    }

    private void validateMeetingTimes() throws IllegalArgumentException{
        if (endTime.before(startTime) || endTime.equals(startTime)){
            throw new IllegalArgumentException("Error Code 0x001 - [Raised] A Meeting's end time " +
                                                       "cannot be before its start time. " +
                                                       "Similarly, a meeting cannot have an " +
                                                       "identical start and end time.");
        }
    }


    /*! Override Para Java methods*/

    /**
     * Indicates whether some other object is "equal to" this one.
     * <p>
     * The {@code equals} method implements an equivalence relation on non-null object references:
     * <ul>
     * <li>It is <i>reflexive</i>: for any non-null reference value
     *     {@code x}, {@code x.equals(x)} should return
     *     {@code true}.
     * <li>It is <i>symmetric</i>: for any non-null reference values
     *     {@code x} and {@code y}, {@code x.equals(y)}
     *     should return {@code true} if and only if
     *     {@code y.equals(x)} returns {@code true}.
     * <li>It is <i>transitive</i>: for any non-null reference values
     *     {@code x}, {@code y}, and {@code z}, if
     *     {@code x.equals(y)} returns {@code true} and
     *     {@code y.equals(z)} returns {@code true}, then
     *     {@code x.equals(z)} should return {@code true}.
     * <li>It is <i>consistent</i>: for any non-null reference values
     *     {@code x} and {@code y}, multiple invocations of
     *     {@code x.equals(y)} consistently return {@code true}
     *     or consistently return {@code false}, provided no
     *     information used in {@code equals} comparisons on the
     *     objects is modified.
     * <li>For any non-null reference value {@code x},
     *     {@code x.equals(null)} should return {@code false}.
     * </ul>
     *
     * <p>
     * An equivalence relation partitions the elements it operates on
     * into <i>equivalence classes</i>; all the members of an
     * equivalence class are equal to each other. Members of an
     * equivalence class are substitutable for each other, at least
     * for some purposes.
     *
     * @param obj the reference object with which to compare.
     * @return {@code true} if this object is the same as the obj argument; {@code false} otherwise.
     * @implSpec The {@code equals} method for class {@code Object} implements the most
     * discriminating possible equivalence relation on objects; that is, for any non-null reference
     * values {@code x} and {@code y}, this method returns {@code true} if and only if {@code x} and
     * {@code y} refer to the same object ({@code x == y} has the value {@code true}).
     * <p>
     * In other words, under the reference equality equivalence relation, each equivalence class
     * only has a single element.
     * @apiNote It is generally necessary to override the {@link #hashCode() hashCode} method
     * whenever this method is overridden, so as to maintain the general contract for the
     * {@code hashCode} method, which states that equal objects must have equal hash codes.
     * <p>The two-argument {@link Objects#equals(Object,
     * Object) Objects.equals} method implements an equivalence relation on two possibly-null object
     * references.
     * @see #hashCode()
     * @see HashMap
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !obj.getClass().getName().equals(this.getClass().getName())) {
            return false;
        } else if (obj == this) {
            return true;
        } else {
            Meeting meeting = (Meeting) obj;
            return this.meetingTopic.equals(meeting.meetingTopic) &&
                    this.organizer.equals(meeting.organizer) &&
                    this.invitees.equals(meeting.invitees) &&
                    this.place.equals(meeting.place) &&
                    this.startTime.equals(meeting.startTime) &&
                    this.endTime.equals(meeting.endTime);
        }
    }

    /**
     * {@return a string representation of the object}
     *
     * @apiNote In general, the {@code toString} method returns a string that "textually represents"
     * this object. The result should be a concise but informative representation that is easy for a
     * person to read. It is recommended that all subclasses override this method. The string output
     * is not necessarily stable over time or across JVM invocations.
     * @implSpec The {@code toString} method for class {@code Object} returns a string consisting of
     * the name of the class of which the object is an instance, the at-sign character `{@code @}',
     * and the unsigned hexadecimal representation of the hash code of the object. In other words,
     * this method returns a string equal to the value of:
     * {@snippet lang = java:
     * getClass().getName() + '@' + Integer.toHexString(hashCode())
     *} The {@link Objects#toIdentityString(Object) Objects.toIdentityString} method returns the
     * string for an object equal to the string that would be returned if neither the
     * {@code toString} nor {@code hashCode} methods were overridden by the object's class.
     */
    @Override
    public String toString() {
        return "Meeting{" +
                "meetingTopic='" + meetingTopic + '\'' +
                ", organizer=" + organizer +
                ", invitees=" + invitees +
                ", place='" + place + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }

    /**
     * Compares this object with the specified object for order.  Returns a negative integer, zero,
     * or a positive integer as this object is less than, equal to, or greater than the specified
     * object.
     *
     * <p>The implementor must ensure {@link Integer#signum
     * signum}{@code (x.compareTo(y)) == -signum(y.compareTo(x))} for all {@code x} and {@code y}.
     * (This implies that {@code x.compareTo(y)} must throw an exception if and only if
     * {@code y.compareTo(x)} throws an exception.)
     *
     * <p>The implementor must also ensure that the relation is transitive:
     * {@code (x.compareTo(y) > 0 && y.compareTo(z) > 0)} implies {@code x.compareTo(z) > 0}.
     *
     * <p>Finally, the implementor must ensure that {@code
     * x.compareTo(y)==0} implies that {@code signum(x.compareTo(z)) == signum(y.compareTo(z))}, for
     * all {@code z}.
     *
     * @param o the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object is less than, equal
     * to, or greater than the specified object.
     * @throws NullPointerException if the specified object is null
     * @throws ClassCastException   if the specified object's type prevents it from being compared
     *                              to this object.
     * @apiNote It is strongly recommended, but <i>not</i> strictly required that
     * {@code (x.compareTo(y)==0) == (x.equals(y))}.  Generally speaking, any class that implements
     * the {@code Comparable} interface and violates this condition should clearly indicate this
     * fact.  The recommended language is "Note: this class has a natural ordering that is
     * inconsistent with equals."
     */
    @Override
    public int compareTo(Meeting o) {
        return this.getStartTime().compareTo(o.getStartTime());
    }



    /*! Static methods for email revision*/
    public static boolean isOtherMeetingOverlappingThisMeeting(Meeting otherMeeting, Meeting thisMeeting) {
        //? 1. First, we have to grab the distances between each of the meetings, this allows us
        // to analyze the start time, end time and duration to find overlaps
        long startTimeDifference =
                otherMeeting.getStartTime().getTime() - thisMeeting.getStartTime().getTime();
        long endTimeDifference =
                otherMeeting.getEndTime().getTime() - thisMeeting.getEndTime().getTime();
        long thisMeetingDuration =
                thisMeeting.getEndTime().getTime() - thisMeeting.getStartTime().getTime();

        //? 2. Now we can check if there are overlaps on the start times, there will be an
        // overlap if for example: the meetings start time this - other produces a non negative
        // value
        boolean thisMeetingStartsWithinOtherMeeting =
                startTimeDifference > 0 && startTimeDifference < thisMeetingDuration;
        boolean thisMeetingStartAndEndAsOtherMeeting =
                startTimeDifference == 0 && endTimeDifference == 0;
        boolean thisMeetingEndsAfterOtherMeeting =
                endTimeDifference < 0;


        return thisMeetingStartAndEndAsOtherMeeting || thisMeetingStartsWithinOtherMeeting ||
                (startTimeDifference > 0 && thisMeetingEndsAfterOtherMeeting);
    }


    public Meeting(Date startTime, Date endTime) {
        this.setMeetingTopic("null");
        this.setOrganizer(new Employee("null", "null"));
        this.setInvitees(new ArrayList<>());
        this.setPlace("null");
        this.setStartTime(startTime);
        this.setEndTime(endTime);
    }

}
