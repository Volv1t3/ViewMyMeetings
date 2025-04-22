package com.evolvlabs.DataModel;

import java.util.HashMap;
import java.util.Objects;

/**
 * @author : Marcos López , Santiago Arellano
 * @date : 21st-Apr-2025
 * @description : The following is a simple implementation of a client that is required within the
 * application. Given the application context, the information here is not serialized, as it is only
 * used on the client side for meeting creation, and server side for meeting storage and retrieval.
 * At most, the client would require knowing the ID of the employee that is requesting the  meetings
 * to retrieve them, not the name or other information. On the other hand, names and other
 * information are going to be stored within the system as parts of a connected Clients List, where
 * information can be stored for further revision.
 * <br>
 * As such, the implementation of this class follows two main concerns, first it has to handle all
 * the data fields that are required by a client to register themselves in a meeting or create one,
 * i.e., Full name and ID. Secondly, it has to handle the storage of this information in a
 * concurrent manner, allowing for information to be shared.
 */
public class Employee {

    /*! Class Parameters*/
    private String employeeFullName;
    private String employeeID;

    /*! Three Constructors: Full, semi with only ID, and empty for serialization purposes*/
    public Employee(String employeeFullName, String employeeID) {
        this.setEmployeeFullName(employeeFullName);
        this.setEmployeeID(employeeID);
    }

    public Employee(String employeeID) {
        this.setEmployeeID(employeeID);
    }

    public Employee() {
        this.setEmployeeFullName("null");
        this.setEmployeeID("null");
    }


    /*! Setters y Getters*/
    public void setEmployeeFullName(String employeeFullName) {
        if (employeeFullName != null && !employeeFullName.isEmpty()) {
            this.employeeFullName = employeeFullName;
        } else {
            throw new IllegalArgumentException("Error Code 0x001 - [Raised] An Employee's name " +
                                                       "cannot be null during instance creation.");
        }
    }

    public String getEmployeeFullName() {
        return employeeFullName;
    }

    public void setEmployeeID(String employeeID) {
        if (employeeID != null && !employeeID.isEmpty()) {
            this.employeeID = employeeID;
        } else {
            throw new IllegalArgumentException("Error Code 0x002 - [Raised] An Employee's ID " +
                                                       "cannot be null during instance creation.");
        }
    }

    public String getEmployeeID() {
        return employeeID;
    }

    /*Override Para Java methods*/

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
            Employee employee = (Employee) obj;
            return employee.getEmployeeID().equals(this.getEmployeeID()) &&
                    employee.getEmployeeFullName()
                            .equalsIgnoreCase(this.getEmployeeFullName());
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
        return "Employee{" +
                "employeeFullName='" + employeeFullName + '\'' +
                ", employeeID='" + employeeID + '\'' +
                '}';
    }
}
