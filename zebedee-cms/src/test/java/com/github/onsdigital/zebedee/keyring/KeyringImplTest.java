package com.github.onsdigital.zebedee.keyring;

import com.github.onsdigital.zebedee.keyring.store.CollectionKeyStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.crypto.SecretKey;
import java.util.HashMap;
import java.util.Map;

import static com.github.onsdigital.zebedee.keyring.KeyringImpl.INVALID_COLLECTION_ID_ERR;
import static com.github.onsdigital.zebedee.keyring.KeyringImpl.INVALID_SECRET_KEY_ERR;
import static com.github.onsdigital.zebedee.keyring.KeyringImpl.KEYSTORE_NULL_ERR;
import static com.github.onsdigital.zebedee.keyring.KeyringImpl.KEY_MISMATCH_ERR;
import static com.github.onsdigital.zebedee.keyring.KeyringImpl.KEY_NOT_FOUND_ERR;
import static com.github.onsdigital.zebedee.keyring.KeyringImpl.LOAD_KEYS_NULL_ERR;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class KeyringImplTest {

    private static final String TEST_COLLECTION_ID = "138"; // We are 138! We are 138 \m/

    private Keyring keyring;
    private Map<String, SecretKey> cache;

    @Mock
    private CollectionKeyStore keyStore;

    @Mock
    private SecretKey secretKey;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        this.cache = new HashMap<>();
        this.keyring = new KeyringImpl(keyStore, cache);
    }

    @Test
    public void testAdd_collectionIDNull_shouldThrowException() {
        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.add(null, null));

        assertThat(ex.getMessage(), equalTo(INVALID_COLLECTION_ID_ERR));
        assertTrue(cache.isEmpty());
    }

    @Test
    public void testAdd_collectionIDEmpty_shouldThrowException() throws Exception {
        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.add("", null));

        assertThat(ex.getMessage(), equalTo(INVALID_COLLECTION_ID_ERR));
        assertTrue(cache.isEmpty());
    }

    @Test
    public void testAdd_secretKeyNull_shouldThrowException() throws Exception {
        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.add(TEST_COLLECTION_ID, null));

        assertThat(ex.getMessage(), equalTo(INVALID_SECRET_KEY_ERR));
        assertThat(ex.getCollectionID(), equalTo(TEST_COLLECTION_ID));
        assertTrue(cache.isEmpty());
    }

    @Test
    public void testAdd_keyExistsInCache_shouldDoNothing() throws Exception {
        cache.put(TEST_COLLECTION_ID, secretKey);
        assertThat(cache.size(), equalTo(1));

        keyring.add(TEST_COLLECTION_ID, secretKey);

        assertThat(cache.size(), equalTo(1));
        assertTrue(cache.containsKey(TEST_COLLECTION_ID));
        assertThat(cache.get(TEST_COLLECTION_ID), equalTo(secretKey));
        verifyZeroInteractions(keyStore);
    }

    @Test
    public void testAdd_keyWithDiffentValueExistsInCache_shouldThrowException() throws Exception {
        SecretKey key2 = mock(SecretKey.class);
        cache.put(TEST_COLLECTION_ID, key2);

        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.add(TEST_COLLECTION_ID, secretKey));

        verifyZeroInteractions(keyStore);
        assertThat(cache.size(), equalTo(1));
        assertThat(ex.getMessage(), equalTo(KEY_MISMATCH_ERR));
        assertThat(ex.getCollectionID(), equalTo(TEST_COLLECTION_ID));
    }

    @Test
    public void testAdd_keystoreThrowsException_shouldThrowException() throws Exception {
        doThrow(KeyringException.class)
                .when(keyStore)
                .write(TEST_COLLECTION_ID, secretKey);

        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.add(TEST_COLLECTION_ID, secretKey));

        verify(keyStore, times(1)).write(TEST_COLLECTION_ID, secretKey);
        assertTrue(cache.isEmpty());
    }

    @Test
    public void testAdd_keyNotInCacheButExistsInStore_shouldAddKeyToCache() throws Exception {
        when(keyStore.exists(TEST_COLLECTION_ID))
                .thenReturn(true);

        when(keyStore.read(TEST_COLLECTION_ID))
                .thenReturn(secretKey);

        keyring.add(TEST_COLLECTION_ID, secretKey);

        assertTrue(cache.containsKey(TEST_COLLECTION_ID));
        assertThat(cache.get(TEST_COLLECTION_ID), equalTo(secretKey));
    }

    @Test
    public void testAdd_keyNotInCacheKeyStoreExistsError_shouldThrowException() throws Exception {
        when(keyStore.exists(TEST_COLLECTION_ID))
                .thenReturn(true);

        when(keyStore.read(TEST_COLLECTION_ID))
                .thenThrow(new KeyringException("unexpected error"));

        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.add(TEST_COLLECTION_ID, secretKey));

        assertTrue(cache.isEmpty());
        assertThat(ex.getMessage(), equalTo("unexpected error"));

        verify(keyStore, times(1)).exists(TEST_COLLECTION_ID);
        verify(keyStore, times(1)).read(TEST_COLLECTION_ID);
        verifyNoMoreInteractions(keyStore);
    }

    @Test
    public void testAdd_keystoreKeyMismatch_shouldThrowException() throws Exception {
        when(keyStore.exists(TEST_COLLECTION_ID))
                .thenReturn(true);

        when(keyStore.read(TEST_COLLECTION_ID))
                .thenReturn(mock(SecretKey.class));

        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.add(TEST_COLLECTION_ID, secretKey));

        assertThat(ex.getMessage(), equalTo(KEY_MISMATCH_ERR));
        assertThat(ex.getCollectionID(), equalTo(TEST_COLLECTION_ID));

        verify(keyStore, times(1)).exists(TEST_COLLECTION_ID);
        verify(keyStore, times(1)).read(TEST_COLLECTION_ID);
        verifyNoMoreInteractions(keyStore);
    }

    @Test
    public void testAdd_success_shouldWriteKeyToStoreAndCache() throws Exception {
        keyring.add(TEST_COLLECTION_ID, secretKey);

        verify(keyStore, times(1)).write(TEST_COLLECTION_ID, secretKey);
        assertThat(cache.size(), equalTo(1));
        assertTrue(cache.containsKey(TEST_COLLECTION_ID));
        assertThat(cache.get(TEST_COLLECTION_ID), equalTo(secretKey));
    }

    @Test
    public void testGet_collectionIDNull_shouldThrowException() throws Exception {
        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.get(null));

        assertThat(ex.getMessage(), equalTo(INVALID_COLLECTION_ID_ERR));
        verifyZeroInteractions(keyStore);
    }

    @Test
    public void testGet_collectionIDEmpty_shouldThrowException() throws Exception {
        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.get(null));

        assertThat(ex.getMessage(), equalTo(INVALID_COLLECTION_ID_ERR));
        verifyZeroInteractions(keyStore);
    }

    @Test
    public void testGet_keyExistsInCache_shouldReturnKey() throws Exception {
        cache.put(TEST_COLLECTION_ID, secretKey);

        SecretKey actual = keyring.get(TEST_COLLECTION_ID);

        assertThat(actual, equalTo(secretKey));
        verifyZeroInteractions(keyStore);
    }

    @Test
    public void testGet_keyNotInCacheOrStore_shouldThrowException() throws Exception {
        when(keyStore.exists(TEST_COLLECTION_ID))
                .thenReturn(false);

        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.get(TEST_COLLECTION_ID));

        assertThat(ex.getMessage(), equalTo(KEY_NOT_FOUND_ERR));
        assertThat(ex.getCollectionID(), equalTo(TEST_COLLECTION_ID));
        verify(keyStore, times(1)).exists(TEST_COLLECTION_ID);
        verifyNoMoreInteractions(keyStore);
    }

    @Test
    public void testGet_keyExistsInStore_shouldReturnKey() throws Exception {
        when(keyStore.exists(TEST_COLLECTION_ID))
                .thenReturn(true);

        when(keyStore.read(TEST_COLLECTION_ID))
                .thenReturn(secretKey);

        SecretKey actual = keyring.get(TEST_COLLECTION_ID);

        assertTrue(cache.containsKey(TEST_COLLECTION_ID));
        assertThat(cache.get(TEST_COLLECTION_ID), equalTo(actual));
        assertThat(actual, equalTo(secretKey));

        verify(keyStore, times(1)).exists(TEST_COLLECTION_ID);
        verify(keyStore, times(1)).read(TEST_COLLECTION_ID);
    }

    @Test
    public void testGet_keystoreException_shouldThrowException() throws Exception {
        when(keyStore.exists(TEST_COLLECTION_ID))
                .thenReturn(true);

        when(keyStore.read(TEST_COLLECTION_ID))
                .thenThrow(new KeyringException("error"));

        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.get(TEST_COLLECTION_ID));

        assertThat(ex.getMessage(), equalTo("error"));
        assertFalse(cache.containsKey(TEST_COLLECTION_ID));
        assertTrue(cache.isEmpty());

        verify(keyStore, times(1)).exists(TEST_COLLECTION_ID);
        verify(keyStore, times(1)).read(TEST_COLLECTION_ID);
    }

    @Test
    public void testRemove_collectionIDNull_shouldThrowException() {
        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.remove(null));

        assertThat(ex.getMessage(), equalTo(INVALID_COLLECTION_ID_ERR));

        verifyZeroInteractions(keyStore);
    }

    @Test
    public void testRemove_collectionIDEmpty_shouldThrowException() {
        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.remove(""));

        assertThat(ex.getMessage(), equalTo(INVALID_COLLECTION_ID_ERR));

        verifyZeroInteractions(keyStore);
    }

    @Test
    public void testRemove_collectionKeyDoesntExist_shouldThrowException() throws KeyringException {
        when(keyStore.exists(TEST_COLLECTION_ID))
                .thenReturn(false);

        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.remove(TEST_COLLECTION_ID));

        assertThat(ex.getMessage(), equalTo(KEY_NOT_FOUND_ERR));

        verify(keyStore, times(1)).exists(TEST_COLLECTION_ID);
        verify(keyStore, never()).delete(TEST_COLLECTION_ID);
    }

    @Test
    public void testRemove_keyStoreDeleteEx_shouldThrowException() throws KeyringException {
        when(keyStore.exists(TEST_COLLECTION_ID))
                .thenReturn(true);

        doThrow(new KeyringException("pr review me"))
                .when(keyStore)
                .delete(TEST_COLLECTION_ID);

        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.remove(TEST_COLLECTION_ID));

        assertThat(ex.getMessage(), equalTo("pr review me"));

        verify(keyStore, times(1)).exists(TEST_COLLECTION_ID);
        verify(keyStore, times(1)).delete(TEST_COLLECTION_ID);
    }

    @Test
    public void testRemove_keyFileExistsButNotInCache_shouldDeleteFile() throws KeyringException {
        when(keyStore.exists(TEST_COLLECTION_ID))
                .thenReturn(true);

        keyring.remove(TEST_COLLECTION_ID);

        verify(keyStore, times(1)).exists(TEST_COLLECTION_ID);
        verify(keyStore, times(1)).delete(TEST_COLLECTION_ID);
    }

    @Test
    public void testRemove_success_shouldRemoveKeyFromFileAndCache() throws KeyringException {
        cache.put(TEST_COLLECTION_ID, secretKey);
        assertTrue(cache.containsKey(TEST_COLLECTION_ID));
        assertThat(cache.size(), equalTo(1));

        when(keyStore.exists(TEST_COLLECTION_ID))
                .thenReturn(true);

        keyring.remove(TEST_COLLECTION_ID);

        assertFalse(cache.containsKey(TEST_COLLECTION_ID));

        verify(keyStore, times(1)).exists(TEST_COLLECTION_ID);
        verify(keyStore, times(1)).delete(TEST_COLLECTION_ID);
    }

    @Test
    public void testLoad_keystoreException_shouldThrowException() throws Exception {
        when(keyStore.readAll()).thenThrow(new KeyringException("boom"));

        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.load());

        assertThat(ex.getMessage(), equalTo("boom"));
        verify(keyStore, times(1)).readAll();
    }

    @Test
    public void testLoad_keystoreReturnsNull_shouldThrowException() throws Exception {
        when(keyStore.readAll())
                .thenReturn(null);

        KeyringException ex = assertThrows(KeyringException.class, () -> keyring.load());

        assertThat(ex.getMessage(), equalTo(LOAD_KEYS_NULL_ERR));
        verify(keyStore, times(1)).readAll();
    }

    @Test
    public void testLoad_keystoreEmpty_cacheShouldBeEmpty() throws Exception {
        when(keyStore.readAll())
                .thenReturn(new HashMap<>());

        keyring.load();

        assertTrue(cache.isEmpty());
        verify(keyStore, times(1)).readAll();
    }

    @Test
    public void testLoad_keystoreReturnsKeyMapping_cacheShouldBePopulated() throws Exception {
        Map<String, SecretKey> keysInStore = new HashMap<String, SecretKey>() {{
            put(TEST_COLLECTION_ID, secretKey);
        }};

        when(keyStore.readAll())
                .thenReturn(keysInStore);

        keyring.load();

        assertThat(cache.size(), equalTo(1));
        assertTrue(cache.containsKey(TEST_COLLECTION_ID));
        assertThat(cache.get(TEST_COLLECTION_ID), equalTo(secretKey));
        verify(keyStore, times(1)).readAll();
    }

    @Test
    public void testLoad_keystoreReturnsKeyMapping_cacheShouldOverwritten() throws Exception {
        SecretKey oldKey = mock(SecretKey.class);
        cache.put("abc123", oldKey);

        Map<String, SecretKey> keysInStore = new HashMap<String, SecretKey>() {{
            put(TEST_COLLECTION_ID, secretKey);
        }};

        when(keyStore.readAll())
                .thenReturn(keysInStore);

        keyring.load();

        assertThat(cache.size(), equalTo(1));
        assertTrue(cache.containsKey(TEST_COLLECTION_ID));
        assertTrue(!cache.containsKey("abc123"));
        assertThat(cache.get(TEST_COLLECTION_ID), equalTo(secretKey));
        verify(keyStore, times(1)).readAll();
    }

    @Test
    public void testInit_keystoreNull_shouldThrowException() {
        KeyringException ex = assertThrows(KeyringException.class, () -> KeyringImpl.init(null));

        assertThat(ex.getMessage(), equalTo(KEYSTORE_NULL_ERR));
    }

    @Test
    public void testInit_keystoreException_shouldThrowException() throws Exception {
        when(keyStore.readAll())
                .thenThrow(new KeyringException("boom"));

        KeyringException ex = assertThrows(KeyringException.class, () -> KeyringImpl.init(keyStore));

        assertThat(ex.getMessage(), equalTo("boom"));
        verify(keyStore, times(1)).readAll();
    }

    @Test
    public void testInit_keystoreReturnsNull_shouldThrowException() throws Exception {
        when(keyStore.readAll())
                .thenReturn(null);

        KeyringException ex = assertThrows(KeyringException.class, () -> KeyringImpl.init(keyStore));

        assertThat(ex.getMessage(), equalTo(LOAD_KEYS_NULL_ERR));
        verify(keyStore, times(1)).readAll();
    }

    @Test
    public void testInit_success_shouldContainKey() throws Exception {
        Map<String, SecretKey> keys = new HashMap<>();
        keys.put(TEST_COLLECTION_ID, secretKey);

        when(keyStore.readAll())
                .thenReturn(keys);

        Keyring keyring = KeyringImpl.init(keyStore);

        assertThat(keyring.get(TEST_COLLECTION_ID), equalTo(secretKey));
        verify(keyStore, times(1)).readAll();
    }

}
