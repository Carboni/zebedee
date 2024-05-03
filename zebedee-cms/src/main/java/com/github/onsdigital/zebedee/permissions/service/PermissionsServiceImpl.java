package com.github.onsdigital.zebedee.permissions.service;

import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.json.PermissionDefinition;
import com.github.onsdigital.zebedee.model.PathUtils;
import com.github.onsdigital.zebedee.permissions.model.AccessMapping;
import com.github.onsdigital.zebedee.permissions.store.PermissionsStore;
import com.github.onsdigital.zebedee.session.model.Session;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.github.onsdigital.zebedee.configuration.Configuration.getUnauthorizedMessage;

/**
 * @deprecated this implementation is deprecated and will be removed once the JWT session migration has been completed
 * <p>
 * // TODO: remove this class once the migration to JWT sessions has been completed
 */
@Deprecated
public class PermissionsServiceImpl extends JWTPermissionsServiceImpl {

    private ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();

    /**
     * @param permissionsStore the permissions store
     */
    public PermissionsServiceImpl(PermissionsStore permissionsStore) {
        super(permissionsStore);
    }

    /**
     * Determines whether the specified user has administator permissions.
     *
     * @param session The user's login {@link Session}.
     * @return <code>true</code> the user is an administrator or <code>false</code> otherwise.
     */
    @Override
    public boolean isAdministrator(Session session) {
        return session != null && isGroupMember(session, ADMIN_GROUP);
    }

    /**
     * Determines whether an administator exists.
     *
     * @return True if at least one administrator exists.
     * @throws IOException If a filesystem error occurs.
     */
    @Override
    public boolean hasAdministrator() throws IOException {
        boolean result = false;
        readLock.lock();
        try {
            AccessMapping accessMapping = permissionsStore.getAccessMapping();
            result = accessMapping.getAdministrators() != null && !accessMapping.getAdministrators().isEmpty();
        } finally {
            readLock.unlock();
        }

        return result;
    }

    /**
     * Adds the specified user to the list of administrators, giving them administrator permissions.
     *
     * @param email The user's email.
     * @throws IOException If a filesystem error occurs.
     */
    @Override
    public void addAdministrator(String email, Session session) throws UnauthorizedException, IOException {
        // Allow the initial user to be set as an administrator:
        if (hasAdministrator() && (session == null || !isAdministrator(session))) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        writeLock.lock();
        try {
            AccessMapping accessMapping = permissionsStore.getAccessMapping();
            if (accessMapping.getAdministrators() == null) {
                accessMapping.setAdministrators(new HashSet<>());
            }
            accessMapping.getAdministrators().add(PathUtils.standardise(email));
            permissionsStore.saveAccessMapping(accessMapping);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Removes the specified user from the administrators, revoking administrative permissions (but not content permissions).
     *
     * @param email   The user's email.
     * @param session the {@link Session} of the user revoking the permission.
     * @throws IOException If a filesystem error occurs.
     */
    @Override
    public void removeAdministrator(String email, Session session) throws IOException, UnauthorizedException {
        if (session == null || StringUtils.isEmpty(email) || !isAdministrator(session)) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        writeLock.lock();
        try {
            AccessMapping accessMapping = permissionsStore.getAccessMapping();
            if (accessMapping.getAdministrators() == null) {
                accessMapping.setAdministrators(new HashSet<>());
            }
            accessMapping.getAdministrators().remove(PathUtils.standardise(email));
            permissionsStore.saveAccessMapping(accessMapping);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Adds the specified user to the Digital Publishing team, giving them access to read and write all content.
     *
     * @param email The user's email.
     * @throws IOException If a filesystem error occurs.
     */
    @Override
    public void addEditor(String email, Session session) throws IOException, UnauthorizedException, NotFoundException, BadRequestException {
        if (hasAdministrator() && (session == null || !isAdministrator(session))) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        writeLock.lock();
        try {
            AccessMapping accessMapping = permissionsStore.getAccessMapping();
            accessMapping.getDigitalPublishingTeam().add(PathUtils.standardise(email));
            permissionsStore.saveAccessMapping(accessMapping);
        } finally {
            writeLock.unlock();
        }
    }


    /**
     * Removes the specified user to the Digital Publishing team, revoking access to read and write all content.
     *
     * @param email The user's email.
     * @throws IOException If a filesystem error occurs.
     */
    @Override
    public void removeEditor(String email, Session session) throws IOException, UnauthorizedException {
        if (session == null || StringUtils.isEmpty(email) || !isAdministrator(session)) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        writeLock.lock();
        try {
            AccessMapping accessMapping = permissionsStore.getAccessMapping();
            accessMapping.getDigitalPublishingTeam().remove(PathUtils.standardise(email));
            permissionsStore.saveAccessMapping(accessMapping);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * User permission levels given an email
     *
     * @param email the user email
     * @return a {@link PermissionDefinition} object
     * @throws IOException           If a filesystem error occurs.
     * @throws UnauthorizedException If the request is not from an admin or publisher
     */
    @Override
    public PermissionDefinition userPermissions(String email, Session session) throws IOException,
            UnauthorizedException {
        if ((session == null) || !(canEdit(session) || session.getEmail().equalsIgnoreCase(email))) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        readLock.lock();
        try {
            AccessMapping accessMapping = permissionsStore.getAccessMapping();
            Set<String> publishers = accessMapping.getDigitalPublishingTeam();
            Set<String> admins = accessMapping.getAdministrators();

            return new PermissionDefinition()
                    .setEmail(email)
                    .isAdmin(admins != null && admins.contains(PathUtils.standardise(email)))
                    .isEditor(publishers != null && publishers.contains(PathUtils.standardise(email)));
        } finally {
            readLock.unlock();
        }
    }
}
