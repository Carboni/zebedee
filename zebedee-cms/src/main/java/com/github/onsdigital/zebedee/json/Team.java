package com.github.onsdigital.zebedee.json;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by david on 21/04/2015.
 */
public class Team {

    private int id;
    private String name;
    private Set<String> members;

    public Team() {
        this.members = new HashSet<>();
    }

    public int getId() {
        return id;
    }

    public Team setId(int id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public Team setName(String name) {
        this.name = name;
        return this;
    }

    public Set<String> getMembers() {
        return members;
    }

    public Team setMembers(Set<String> members) {
        this.members = members;
        return this;
    }

    public Team addMember(String name) {
        this.members.add(name);
        return this;
    }

    @Override
    public String toString() {
        return name + " (" + id + ") ";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Team team = (Team) o;

        return new EqualsBuilder()
                .append(getId(), team.getId())
                .append(getName(), team.getName())
                .append(getMembers(), team.getMembers())
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(getId())
                .append(getName())
                .append(getMembers())
                .toHashCode();
    }
}
