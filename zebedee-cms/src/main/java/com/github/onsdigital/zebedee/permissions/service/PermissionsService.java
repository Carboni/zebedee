package com.github.onsdigital.zebedee.permissions.service;

import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.json.CollectionDescription;
import com.github.onsdigital.zebedee.json.PermissionDefinition;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.session.model.Session;
import com.github.onsdigital.zebedee.teams.model.Team;
import com.github.onsdigital.zebedee.user.model.User;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Interface defining the api of a permissions service.
 */
public interface PermissionsService {

    /**
     * Return true if the {@link User} is an Admin, false otherwise.
     *
     * @param session {@link Session} to get the user details from.
     * @return true if the user is an admin, false otherwise.
     * @throws IOException unexpected error checking the user permissions.
     *
     * @deprecated since this method is only used by the users and teams services that will be removed shortly in favour
     *             of the dp-identity-api and JWT sessions implementation.
     *
     * TODO: Remove this method once the JWT session migration is complete.
     */
    @Deprecated
    boolean isAdministrator(Session session) throws IOException;

    /**
     * @return true if an Admin user exists, false otherwise.
     * @throws IOException unexpected error accessing users.
     *
     * @deprecated since this method is only used by the users service that will be removed shortly in favour of the
     *             dp-identity-api and JWT sessions implementation.
     *
     * TODO: Remove this method once the JWT session migration is complete.
     */
    @Deprecated
    boolean hasAdministrator() throws IOException;

    /**
     * Grant a {@link User} admin permissions.
     *
     * @param email   the email of the user to permit the permission to.
     * @param session the {@link Session} of the user granting the permission.
     * @throws IOException           unexpected error granting the permission.
     * @throws UnauthorizedException user does not have the required permissions to permit admin permissions.
     *
     * @deprecated as the dp-identity-api groups management will supersede this when we complete the migration to JWT
     *             sessions
     *
     * TODO: Remove this method once the JWT session migration is complete
     */
    @Deprecated
    void addAdministrator(String email, Session session) throws IOException, UnauthorizedException;

    /**
     * Revoke admin permission from a user.
     *
     * @param email   the email of the user to remove the permission from.
     * @param session the {@link Session} of the user revoking the permission.
     * @throws IOException           unexpected error revoking the permission.
     * @throws UnauthorizedException user revoking the permission does not have the required permissions.
     *
     * @deprecated as the dp-identity-api groups management will supersede this when we complete the migration to JWT
     *             sessions
     *
     * TODO: Remove this method once the JWT session migration is complete
     */
    @Deprecated
    void removeAdministrator(String email, Session session) throws IOException, UnauthorizedException;

    /**
     * Check if the {@link User} has permissions to edit content.
     *
     * @param session the {@link Session} of the user to check.
     * @return true if the user can edit content, false otherwise.
     * @throws IOException unexpected error while checking permissions.
     */
    boolean canEdit(Session session) throws IOException;

    /**
     * Grant editor permission to a user.
     *
     * @param email   the email of the user to permit the permission to.
     * @param session the {@link Session} of the {@link User} granting the permissison.
     * @throws IOException           unexpected error while granting the permission.
     * @throws UnauthorizedException the user granting the permission is not authorised to do so.
     * @throws NotFoundException     unexpected error while granting the permission.
     * @throws BadRequestException   unexpected error while granting the permission.
     *
     * @deprecated as the dp-identity-api groups management will supersede this when we complete the migration to JWT
     *             sessions
     *
     * TODO: Remove this method once the JWT session migration is complete
     */
    @Deprecated
    void addEditor(String email, Session session) throws IOException, UnauthorizedException, NotFoundException, BadRequestException;

    /**
     * Remove editor permission from a user.
     *
     * @param email   the email of the user to revoke the permission from.
     * @param session the {@link Session} of the {@link User} revoking the permissison.
     * @throws IOException           unexpected error while granting the permission.
     * @throws UnauthorizedException the user revoking the permission is not authorised to do so.
     * @throws NotFoundException     unexpected error while granting the permission.
     * @throws BadRequestException   unexpected error while granting the permission.
     *
     * @deprecated as the dp-identity-api groups management will supersede this when we complete the migration to JWT
     *             sessions
     *
     * TODO: Remove this method once the JWT session migration is complete
     */
    @Deprecated
    void removeEditor(String email, Session session) throws IOException, UnauthorizedException;

    /**
     * Check if a {@link User} can view unpublished content.
     *
     * @param session      the {@link Session} to get the user details from.
     * @param collectionId the ID of the {@link Collection} to check.
     * @return true of the user has view permission for the content, false otherwise.
     * @throws IOException unexpected error while checking permissions.
     */
    boolean canView(Session session, String collectionId) throws IOException;

    /**
     * Returns a {@link List} of {@link Team}s that have viewer permissions on the specified collection.
     *
     * @param collectionDescription the {@link CollectionDescription} of the {@link Collection} to get the viewer
     *                              teams for.
     * @param session               the {@link Session} of the {@link User} requesting this information.
     * @return Returns a {@link List} of {@link Team}s that have viewer permissions on the specified collection.
     * @throws IOException           unexpected error while checking permissions.
     * @throws UnauthorizedException unexpected error while checking permissions.
     *
     * @deprecated as the dp-permissions-api policy management will supersede this when we complete the authorisation
     *             migration
     *
     * TODO: Remove this method once the migration to the new dp-permissions-api is completed
     */
    @Deprecated
    Set<String> listViewerTeams(Session session, String collectionId) throws IOException, UnauthorizedException;

    /**
     * Set the list of team IDs that are allowed viewer access to a collection
     *
     * @param collectionID    the ID of the collection collection to set viewer permissions for.
     * @param collectionTeams the set of team IDs for which viewer permissions should be granted to the collection.
     * @param session         the session of the user that is attempting to set the viewer permissions.
     * @throws IOException if reading or writing the access mapping fails.
     * @throws UnauthorizedException if the users' session isn't authorised to edit collections.
     *
     * @deprecated this is deprecated in favour of the dp-permissions-api and will be removed once full migration to
     *             the new API is complete.
     *
     * TODO: Remove once migration to dp-permissions-api is complete and the accessmapping is being removed.
     */
    @Deprecated
    void setViewerTeams(Session session, String collectionId, Set<String> collectionTeams) throws IOException, ZebedeeException;

    /**
     * Return {@link PermissionDefinition} for the specified {@link User}.
     *
     * @param email   the email of the user to get the {@link PermissionDefinition} for.
     * @param session the {@link Session} of the user requesting the {@link PermissionDefinition}.
     * @return Return {@link PermissionDefinition} for the specified {@link User}.
     * @throws IOException           unexpected error while getting the user {@link PermissionDefinition}.
     * @throws NotFoundException     user with the specified email was not found.
     * @throws UnauthorizedException the requesting user does not have the required permissions.
     *
     * @deprecated this will be removed once the migration to JWT sessions has been completed
     *
     * TODO: Remove this method once the migration to JWT sessions is complete
     */
    @Deprecated
    PermissionDefinition userPermissions(String email, Session session) throws IOException, NotFoundException, UnauthorizedException;

    /**
     * Get user permission levels given a session
     *
     * @param session the user session
     * @return a {@link PermissionDefinition} object representing the user's permissions
     * @throws IOException If a filesystem error occurs.
     */
    PermissionDefinition userPermissions(Session session) throws IOException;
}
