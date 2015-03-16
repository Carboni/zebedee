package com.github.onsdigital.zebedee.json;

/**
 * Represents a user account. NB this record intentionally does not contain any permission-related information.
 * Created by david on 12/03/2015.
 */
public class User {
    public String name;
    public String email;
    public String passwordHash;
    public boolean inactive;
}
