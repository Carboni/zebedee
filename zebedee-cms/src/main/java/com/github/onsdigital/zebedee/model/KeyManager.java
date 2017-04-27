package com.github.onsdigital.zebedee.model;

import com.github.onsdigital.zebedee.Zebedee;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.json.Keyring;
import com.github.onsdigital.zebedee.json.Session;
import com.github.onsdigital.zebedee.json.User;
import com.github.onsdigital.zebedee.model.csdb.CsdbImporter;
import com.github.onsdigital.zebedee.util.ZebedeeCmsService;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.onsdigital.zebedee.logging.ZebedeeLogBuilder.logError;

/**
 * Created by thomasridd on 18/11/15.
 */
public class KeyManager {

    private static ZebedeeCmsService zebedeeCmsService = ZebedeeCmsService.getInstance();
    static final ExecutorService executorService = Executors.newFixedThreadPool(25);

    /**
     * Distribute the collection key to users that should have it.
     *
     * @param zebedee         the {@link Zebedee} instance to use.
     * @param session         the users session.
     * @param collection      the {@link Collection} the key belongs to.
     * @param isNewCollection true if the collection is new, false otherwise.
     * @throws IOException
     */
    public static synchronized void distributeCollectionKey(Zebedee zebedee, Session session, Collection collection,
                                                            boolean isNewCollection) throws IOException {
        SecretKey key = zebedee.getKeyringCache().get(session).get(collection.getDescription().id);

        List<User> keyRecipients = zebedee.getPermissions().getCollectionAccessMapping(zebedee, collection);
        List<User> removals = new ArrayList<>();
        List<User> additions = new ArrayList<>();

        if (!isNewCollection) {
            zebedee.getUsersDao().list().stream().forEach(user -> {
                if (!keyRecipients.contains(user)) {
                    removals.add(user);
                }
            });
        }

        zebedee.getUsersDao().list().stream().forEach(user -> {
            if (!removals.contains(user)) {
                additions.add(user);
            }
        });

        for (User removedUser : removals) {
            removeKeyFromUser(zebedee, removedUser, collection.getDescription().id);
        }

        for (User addedUser : additions) {
            assignKeyToUser(zebedee, addedUser, collection.getDescription().id, key);
        }

        zebedee.getKeyringCache().getSchedulerCache().put(collection.description.id, key);
    }

    /**
     * Creates a {@link List} of {@link Callable} tasks to distribute/remove the collection key. For each user creates
     * either a key assignment task if they should have access to the collection, a key removal task if not and a single
     * task to add the new key to {@link Zebedee#keyringCache}
     *
     * @param zebedee         the {@link Zebedee} instance to use.
     * @param collection      the {@link Collection} the key belongs too.
     * @param secretKey       the {@link SecretKey} object to read the collection.
     * @param isNewCollection true if the collection is new, false otherwise.
     * @return A list of {@link Callable}'s which will assign/remove/cache the key as necessary.
     * @throws IOException
     */
    public static List<Callable<Boolean>> getKeyAssignmentTasks(Zebedee zebedee, Collection collection, SecretKey secretKey, boolean isNewCollection) throws IOException {
        List<User> keyRecipients = zebedee.getPermissions().getCollectionAccessMapping(zebedee, collection);
        List<Callable<Boolean>> collectionKeyTasks = new ArrayList<>();

        if (!isNewCollection) {
            // Filter out the users who are should not receive the key and take it from them [evil laugh].
            zebedee.getUsersDao().list().stream().filter(user -> !keyRecipients.contains(user)).forEach(nonKeyRecipient -> {
                collectionKeyTasks.add(() -> {
                    removeKeyFromUser(zebedee, nonKeyRecipient, collection.getDescription().id);
                    return true;
                });
            });
        }

        // Add the key to each recipient user.
        keyRecipients.stream().forEach(user -> {
            collectionKeyTasks.add(() -> {
                assignKeyToUser(zebedee, user, collection.getDescription().id, secretKey);
                return true;
            });
        });

        // Put the Key in the schedule cache.
        collectionKeyTasks.add(() -> {
            zebedee.getKeyringCache().getSchedulerCache().put(collection.description.id, secretKey);
            return true;
        });
        return collectionKeyTasks;
    }

    public static void distributeApplicationKey(Zebedee zebedee, String application, SecretKey secretKey) throws IOException {
        for (User user : zebedee.getUsersDao().list()) {
            distributeApplicationKeyToUser(zebedee, application, secretKey, user);
        }
    }

    private static void distributeApplicationKeyToUser(Zebedee zebedee, String application, SecretKey secretKey, User user) throws IOException {
        if (userShouldHaveApplicationKey(zebedee, user)) {
            // Add the key
            assignKeyToUser(zebedee, user, application, secretKey);
        } else {
            removeKeyFromUser(zebedee, user, application);
        }
    }


    private static boolean userShouldHaveApplicationKey(Zebedee zebedee, User user) throws IOException {
        return zebedee.getPermissions().isAdministrator(user.email) || zebedee.getPermissions().canEdit(user.email);
    }

    /**
     * Determine if the user should have the key assigned or removed for the given collection.
     *
     * @param zebedee
     * @param collection
     * @param session
     * @param user
     * @throws IOException
     */
    public static void distributeKeyToUser(Zebedee zebedee, Collection collection, Session session, User user) throws IOException {
        SecretKey key = zebedee.getKeyringCache().get(session).get(collection.description.id);
        distributeKeyToUser(zebedee, collection, key, user);
    }

    private static void distributeKeyToUser(Zebedee zebedee, Collection collection, SecretKey key, User user) throws IOException {
        if (userShouldHaveKey(zebedee, user, collection)) {
            // Add the key
            assignKeyToUser(zebedee, user, collection.description.id, key);
        } else {
            removeKeyFromUser(zebedee, user, collection.description.id);
        }
    }

    /**
     * @param zebedee
     * @param user
     * @param key
     * @throws IOException
     */
    public static void assignKeyToUser(Zebedee zebedee, User user, String keyIdentifier, SecretKey key) throws IOException {
        // Escape in case user keyring has not been generated
        if (user.keyring() == null) return;

        // Add the key to the user keyring and save
//        user.keyring().put(keyIdentifier, key);
//        zebedee.getUsers().addKeyToKeyring(user);

        zebedee.getUsersDao().addKeyToKeyring(user.email, keyIdentifier, key);

        // If the user is logged in assign the key to their cached keyring
        Session session = zebedee.getSessions().find(user.email);
        if (session != null) {
            Keyring keyring = zebedee.getKeyringCache().get(session);
            try {
                if (keyring != null) keyring.put(keyIdentifier, key);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Remove the collection key for the given user.
     * This method is intentionally private as the distribute* methods should be used
     * to re-evaluate whether a key should be removed instead of just removing it.
     *
     * @param zebedee
     * @param user
     * @throws IOException
     */
    private static void removeKeyFromUser(Zebedee zebedee, User user, String keyIdentifier) throws IOException {
        // Escape in case user keyring has not been generated
        if (user.keyring() == null) return;

        // Remove the key from the users keyring and save
/*        user.keyring().remove(keyIdentifier);
        zebedee.getUsers().updateKeyring(user);*/

        zebedee.getUsersDao().removeKeyFromKeyring(user.email, keyIdentifier);

        // If the user is logged in remove the key from their cached keyring
        Session session = zebedee.getSessions().find(user.email);
        if (session != null) {
            Keyring keyring = zebedee.getKeyringCache().get(session);
            try {
                if (keyring != null) keyring.remove(keyIdentifier);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Transfer a set of secret keys from the source keyring to the target
     *
     * @param targetKeyring the keyring to be populated
     * @param sourceKeyring the keyring to take keys from
     * @param collectionIds the keys to transfer
     * @throws NotFoundException
     * @throws BadRequestException
     * @throws IOException
     */
    public static void transferKeyring(Keyring targetKeyring, Keyring sourceKeyring, Set<String> collectionIds) throws NotFoundException, BadRequestException, IOException {

        for (String collectionId : collectionIds) {
            SecretKey key = sourceKeyring.get(collectionId);
            if (key != null) {
                targetKeyring.put(collectionId, key);
            }
        }
    }

    /**
     * Transfer all secret keys from the source keyring to the target
     *
     * @param targetKeyring the keyring to be populated
     * @param sourceKeyring the keyring to take keys from
     * @throws NotFoundException
     * @throws BadRequestException
     * @throws IOException
     */
    public static void transferKeyring(Keyring targetKeyring, Keyring sourceKeyring, CollectionOwner collectionOwner) throws NotFoundException, BadRequestException, IOException {
        Set<String> collectionIds = new HashSet<>();

        sourceKeyring.list().stream().forEach(collectionId -> {
            if (StringUtils.equals(collectionId, CsdbImporter.APPLICATION_KEY_ID)) {
                // csdb-import is a special case always add this.
                collectionIds.add(collectionId);
            } else {
                Collection collection = getCollection(collectionId);
                if (collection != null && collection.description.collectionOwner != null && collection.description.collectionOwner.equals(collectionOwner)) {
                    collectionIds.add(collectionId);
                } else {
                    if (CollectionOwner.PUBLISHING_SUPPORT.equals(collectionOwner)) {
                        collectionIds.add(collectionId);
                    }
                }
            }
        });
        transferKeyring(targetKeyring, sourceKeyring, collectionIds);
    }

    private static boolean userShouldHaveKey(Zebedee zebedee, User user, Collection collection) throws IOException {
        if (zebedee.getPermissions().isAdministrator(user.email) || zebedee.getPermissions().canView(user, collection.description))
            return true;
        return false;
    }

    private static Collection getCollection(String id) {
        try {
            return zebedeeCmsService.getCollection(id);
        } catch (ZebedeeException e) {
            logError(e, "failed to get collection").addParameter("collectionId", id).log();
            throw new RuntimeException("failed to get collection with collectionId " + id, e);
        }
    }

    public static void setZebedeeCmsService(ZebedeeCmsService zebedeeCmsService) {
        KeyManager.zebedeeCmsService = zebedeeCmsService;
    }
}
