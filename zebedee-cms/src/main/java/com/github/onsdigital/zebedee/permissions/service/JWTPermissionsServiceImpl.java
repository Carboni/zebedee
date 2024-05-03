package com.github.onsdigital.zebedee.permissions.service;

import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.json.PermissionDefinition;
import com.github.onsdigital.zebedee.permissions.model.AccessMapping;
import com.github.onsdigital.zebedee.permissions.store.PermissionsStore;
import com.github.onsdigital.zebedee.session.model.Session;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static com.github.onsdigital.logging.v2.event.SimpleEvent.error;
import static com.github.onsdigital.zebedee.configuration.Configuration.getUnauthorizedMessage;
import static java.text.MessageFormat.format;

/**
 * this has been implemented for the migration to using JWT Session
 * to implement 'PermissionStore' modules when the jwt is enabled
 * Update Zebedee permissions service to get list of groups for user from JWT session store
 */
public class JWTPermissionsServiceImpl implements PermissionsService {
    // TODO: change the following constant to private once migrtion to JWT sessions is complete and the PermissionsServiceImpl is removed
    protected static final String ADMIN_GROUP = "role-admin";
    private static final String PUBLISHER_GROUP = "role-publisher";
    private static final String UNSUPPORTED_ERROR = "JWT sessions are enabled: {0} is no longer supported";

    // TODO: change the following field to private once migration to JWT sessions is complete and the PermissionsServiceImpl is removed
    protected PermissionsStore permissionsStore;
    private ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();

    /**
     * this has been implemented for the migration to using JWT Session
     * to implement 'PermissionStore' modules when the jwt is enabled
     * Update Zebedee permissions service to get list of groups for user from JWT session store
     *
     * @param permissionsStore - {@link PermissionsStore}
     */
    public JWTPermissionsServiceImpl(PermissionsStore permissionsStore) {
        this.permissionsStore = permissionsStore;
    }

    /**
     * Determines whether the specified user has administator permissions.
     *
     * @param session The user's login {@link Session}.
     * @return <code>true</code> the user is an administrator or <code>false</code> otherwise.
     */
    @Override
    public boolean isAdministrator(Session session) {
        throw new UnsupportedOperationException(format(UNSUPPORTED_ERROR, "isAdministrator"));
    }

    /**
     * Determines whether an administator exists.
     *
     * @return null
     * @throws UnsupportedOperationException if invoked.
     *
     * @deprecated since this method is only used by the users service that will be removed shortly in favour of the
     *             dp-identity-api and JWT sessions implementation.
     */
    @Deprecated
    @Override
    public boolean hasAdministrator() throws IOException {
        throw new UnsupportedOperationException(format(UNSUPPORTED_ERROR, "hasAdministrator"));
    }

    /**
     * Adds the specified user to the list of administrators, giving them administrator permissions.
     *
     * @param email   the email of the user to permit the permission to.
     * @param session the {@link Session} of the user granting the permission.
     * @throws UnsupportedOperationException if invoked.
     *
     * @deprecated as the dp-identity-api groups management will supersede this when we complete the migration to JWT
     *             sessions
     */
    @Deprecated
    @Override
    public void addAdministrator(String email, Session session) throws UnauthorizedException, IOException {
        throw new UnsupportedOperationException(format(UNSUPPORTED_ERROR, "addAdministrator"));
    }

    /**
     * Removes the specified user from the administrators, revoking administrative permissions (but not content permissions).
     *
     * @param email   the email of the user to remove the permission from.
     * @param session the {@link Session} of the user revoking the permission.
     * @throws UnsupportedOperationException if invoked.
     *
     * @deprecated as the dp-identity-api groups management will supersede this when we complete the migration to JWT
     *             sessions
     */
    @Deprecated
    @Override
    public void removeAdministrator(String email, Session session) throws IOException, UnauthorizedException {
        throw new UnsupportedOperationException(format(UNSUPPORTED_ERROR, "removeAdministrator"));
    }

    /**
     * implemented as part of session migration to JWT
     * previous version flow...
     * canEdit(session) -> canEdit(session.getEmail()) -> canEdit(email, accessMapping)
     * so  returns whether email is in digitalPublishingTeam and digitalPublishingTeam is not null ...
     * after migration to dp-identity this translates to isPublisher
     *
     * @param session the {@link Session} of the user to check.
     * @return null
     * @throws IOException If a filesystem error occurs.
     */
    @Override
    public boolean canEdit(Session session) {
        return session != null && (isGroupMember(session, PUBLISHER_GROUP) || isGroupMember(session, ADMIN_GROUP));
    }

    /**
     * implemented as part of session migration to JWT
     * will not be required once jwt has been migrated but will error if invoked
     *
     * @param email   the email of the user to permit the permission to.
     * @param session the {@link Session} of the user granting the permission.
     * @throws IOException If a filesystem error occurs.
     *
     * @deprecated as the dp-identity-api groups management will supersede this when we complete the migration to JWT
     *             sessions
     */
    @Deprecated
    @Override
    public void addEditor(String email, Session session) throws IOException, UnauthorizedException, NotFoundException, BadRequestException {
        throw new UnsupportedOperationException(format(UNSUPPORTED_ERROR, "addEditor"));
    }

    /**
     * implemented as part of session migration to JWT
     * will not be required once jwt has been migrated but will error if invoked
     *
     * @param email   the email of the user to revoke the permission from.
     * @param session the {@link Session} of the user revoking the permission.
     *
     * @deprecated as the dp-identity-api groups management will supersede this when we complete the migration to JWT
     *             sessions
     */
    @Deprecated
    @Override
    public void removeEditor(String email, Session session) throws IOException, UnauthorizedException {
        throw new UnsupportedOperationException(format(UNSUPPORTED_ERROR, "removeEditor"));
    }

    /**
     * Check if a user can view unpublished content.
     *
     * @param session      the {@link Session} to get the user details from.
     * @param collectionId the ID of the collection to check.
     * @return true of the user has view permission for the content, false otherwise.
     * @throws IOException If a filesystem error occurs.
     */
    @Override
    public boolean canView(Session session, String collectionId) throws IOException {

        if (session == null || StringUtils.isBlank(collectionId)) {
            return false;
        }

        if (canEdit(session)) {
            return true;
        }

        List<String> userGroups = session.getGroups();
        if (userGroups.isEmpty()) {
            return false;
        }

        readLock.lock();
        try {
            AccessMapping accessMapping = permissionsStore.getAccessMapping();
            Set<String> collectionGroups = accessMapping.getCollections().get(collectionId);

            if (collectionGroups == null || collectionGroups.isEmpty()) {
                return false;
            }

            List<String> intersection = userGroups.stream()
                    .filter(collectionGroups::contains)
                    .collect(Collectors.toList());

            return !intersection.isEmpty();

        } catch (IOException e) {
            error().data("collectionId", collectionId).data("user", session.getEmail())
                    .logException(e, "canView permission request denied: unexpected error");
        } finally {
            readLock.unlock();
        }
        return false;
    }

    /**
     * Returns a {@link List} of IDs of teams that have viewer permissions on the specified collection.
     *
     * @param collectionId the ID of the collection to get the viewer teams for.
     * @param session      the {@link Session} of the user requesting this information.
     * @return Returns a {@link List} of IDs of teams that have viewer permissions on the specified collection.
     * @throws IOException           unexpected error while checking permissions.
     * @throws UnauthorizedException unexpected error while checking permissions.
     *
     * @deprecated as the dp-permissions-api policy management will supersede this when we complete the authorisation
     *             migration
     */
    @Deprecated
    @Override
    public Set<String> listViewerTeams(Session session, String collectionId) throws IOException, UnauthorizedException {
        if (session == null || !canView(session, collectionId)) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        readLock.lock();
        Set<String> teamIds;
        try {
            AccessMapping accessMapping = permissionsStore.getAccessMapping();
            teamIds = accessMapping.getCollections().get(collectionId);

            if (teamIds == null) {
                teamIds = new HashSet<>();
            }
        } finally {
            readLock.unlock();
        }

        return java.util.Collections.unmodifiableSet(teamIds);
    }

    /**
     * Set the list of team IDs that are allowed viewer access to a collection
     *
     * @param collectionID    the ID of the collection collection to set viewer permissions for.
     * @param collectionTeams the set of team IDs for which viewer permissions should be granted to the collection.
     * @param session         the session of the user that is attempting to set the viewer permissions.
     * @throws IOException           if reading or writing the access mapping fails.
     * @throws UnauthorizedException if the users' session isn't authorised to edit collections.
     *
     * @deprecated this is deprecated in favour of the dp-permissions-api and will be removed once full migration to
     *             the new API is complete.
     */
    @Deprecated
    @Override
    public void setViewerTeams(Session session, String collectionID, Set<String> collectionTeams) throws IOException, UnauthorizedException {
        if (session == null || !canEdit(session)) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        if (collectionTeams == null) {
            collectionTeams = new HashSet<>();
        }

        writeLock.lock();
        try {
            AccessMapping accessMapping = permissionsStore.getAccessMapping();
            accessMapping.getCollections().put(collectionID, collectionTeams);
            permissionsStore.saveAccessMapping(accessMapping);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Return {@link PermissionDefinition} for the specified user.
     *
     * @param email   the email of the user to get the {@link PermissionDefinition} for.
     * @param session the {@link Session} of the user requesting the {@link PermissionDefinition}.
     * @return null
     * @throws UnsupportedOperationException if invoked.
     *
     * @deprecated This method is deprecated by the migration to using JWT sessions. In order to query the permissions
     *             under this new implemention, the user's group membership should be queried fromt he dp-identity-api
     *             instead.
     */
    @Deprecated
    @Override
    public PermissionDefinition userPermissions(String email, Session session) throws IOException, UnauthorizedException {
        throw new UnsupportedOperationException(format(UNSUPPORTED_ERROR, "userPermissions"));
    }

    /**
     * Get user permission levels given an session
     *
     * @param session the user session
     * @return a {@link PermissionDefinition} object representing the user's permissions
     * @throws IOException If a filesystem error occurs.
     */
    @Override
    public PermissionDefinition userPermissions(Session session) throws IOException {
        PermissionDefinition permissions = new PermissionDefinition();
        if (session != null) {
            permissions.setEmail(session.getEmail())
                    .isAdmin(isGroupMember(session, ADMIN_GROUP))
                    .isEditor(canEdit(session));
        }
        return permissions;
    }

    /**
     * @param session the {@link Session} to check
     * @param group   the group to check membership of
     * @return <code>true</code> if the user is a member of the group, <code>false</code> otherwise.
     *
     * TODO: change the following method to private once migration to JWT sessions is complete and the PermissionsServiceImpl is removed
     */
    protected boolean isGroupMember(Session session, String group) {
        return session.getGroups().contains(group);
    }
}