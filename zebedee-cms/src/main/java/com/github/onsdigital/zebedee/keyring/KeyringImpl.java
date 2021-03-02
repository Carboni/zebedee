package com.github.onsdigital.zebedee.keyring;

import com.github.onsdigital.zebedee.keyring.cache.KeyringCache;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.permissions.service.PermissionsService;
import com.github.onsdigital.zebedee.user.model.User;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Set;

/**
 * CollectionKeyringImpl adds a permissions check wrapper around a {@link KeyringCache} instance to ensure only
 * authorised users can access collection encryption keys
 */
public class KeyringImpl implements Keyring {

    static final String USER_NULL_ERR = "user required but was null";
    static final String USER_KEYRING_NULL_ERR = "user keyring required but was null";
    static final String USER_KEYRING_LOCKED_ERR = "error user keyring is locked";
    static final String NOT_INITIALISED_ERR = "CollectionKeyring accessed but not yet initialised";
    static final String KEYRING_CACHE_NULL_ERR = "keyringCache required but was null";
    static final String COLLECTION_NULL_ERR = "collection required but was null";
    static final String COLLECTION_DESCRIPTION_NULL_ERR = "collection description required but is null";
    static final String COLLECTION_ID_NULL_OR_EMPTY_ERR = "collection ID required but was null or empty";
    static final String PERMISSION_SERVICE_NULL_ERR = "permissionsService required but was null";
    static final String SECRET_KEY_NULL_ERR = "secret key required but was null";

    /**
     * Singleton instance.
     */
    private static Keyring INSTANCE = null;

    private final KeyringCache cache;
    private final PermissionsService permissionsService;

    /**
     * CollectionKeyringImpl is a singleton instance. Use {@link KeyringImpl#init(KeyringCache, PermissionsService)} to
     * construct and initialise a new instance. Use {@link KeyringImpl#getInstance()} to accessed the
     * singleton.
     *
     * @param cache the {@link KeyringCache} instance to use.
     * @throws KeyringException the {@link KeyringCache} was null.
     */
    private KeyringImpl(KeyringCache cache, PermissionsService permissionsService) throws KeyringException {
        if (cache == null) {
            throw new KeyringException(KEYRING_CACHE_NULL_ERR);
        }

        if (permissionsService == null) {
            throw new KeyringException(PERMISSION_SERVICE_NULL_ERR);
        }

        this.permissionsService = permissionsService;
        this.cache = cache;
    }

    @Override
    public void populateFromUser(User user) throws KeyringException {
        if (user == null) {
            throw new KeyringException(USER_NULL_ERR);
        }

        if (user.keyring() == null) {
            throw new KeyringException(USER_KEYRING_NULL_ERR);
        }

        if (!user.keyring().isUnlocked()) {
            throw new KeyringException(USER_KEYRING_LOCKED_ERR);
        }

        if (!user.keyring().list().isEmpty()) {
            for (String collectionID : user.keyring().list()) {
                cache.add(collectionID, user.keyring().get(collectionID));
            }
        }
    }

    /**
     * @param user
     * @param collection
     * @return
     * @throws KeyringException
     */
    @Override
    public SecretKey get(User user, Collection collection) throws KeyringException {
        validGetParams(user, collection);

        boolean hasAccess = false;
        try {
            hasAccess = permissionsService.hasAccessToCollection(user, collection);
        } catch (IOException ex) {
            throw new KeyringException(ex);
        }

        if (!hasAccess) {
            return null;
        }

        return cache.get(collection.getDescription().getId());
    }

    @Override
    public void remove(User user, Collection collection) throws KeyringException {
        validGetParams(user, collection);

        boolean hasAccess = false;
        try {
            hasAccess = permissionsService.canEdit(user, collection.getDescription());
        } catch (IOException ex) {
            throw new KeyringException(ex);
        }

        if (!hasAccess) {
            return;
        }

        cache.remove(collection.getDescription().getId());
    }

    @Override
    public void add(User user, Collection collection, SecretKey key) throws KeyringException {
        validGetParams(user, collection);
        if (key == null) {
            throw new KeyringException(SECRET_KEY_NULL_ERR);
        }

        boolean hasAccess = false;
        try {
            hasAccess = permissionsService.canEdit(user, collection.getDescription());
        } catch (IOException ex) {
            throw new KeyringException(ex);
        }

        if (!hasAccess) {
            return;
        }

        cache.add(collection.getDescription().getId(), key);
    }

    /**
     * Lists the collection IDs in the keyring.
     *
     * @param user
     * @return An unmodifiable set of the key identifiers in the keyring.
     */
    @Override
    public Set<String> list(User user) throws KeyringException {
        return null;
    }

    private void validGetParams(User user, Collection collection) throws KeyringException {
        if (user == null) {
            throw new KeyringException(USER_NULL_ERR);
        }

        if (collection == null) {
            throw new KeyringException(COLLECTION_NULL_ERR);
        }

        if (collection.getDescription() == null) {
            throw new KeyringException(COLLECTION_DESCRIPTION_NULL_ERR);
        }

        if (StringUtils.isEmpty(collection.getDescription().getId())) {
            throw new KeyringException(COLLECTION_ID_NULL_OR_EMPTY_ERR);
        }
    }

    /**
     * Initailise the CollectionKeyring.
     *
     * @param keyringCache the {@link KeyringCache} instance to use.
     * @throws KeyringException failed to initialise instance.
     */
    public static void init(KeyringCache keyringCache, PermissionsService permissionsService) throws KeyringException {
        if (INSTANCE == null) {
            synchronized (KeyringImpl.class) {
                if (INSTANCE == null) {
                    INSTANCE = new KeyringImpl(keyringCache, permissionsService);
                }
            }
        }
    }

    /**
     * @return a singleton instance of the CollectionKeyring
     * @throws KeyringException CollectionKeyring has not been initalised before being accessed.
     */
    public static Keyring getInstance() throws KeyringException {
        if (INSTANCE == null) {
            throw new KeyringException(NOT_INITIALISED_ERR);
        }
        return INSTANCE;
    }
}
