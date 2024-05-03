package com.github.onsdigital.zebedee;

import com.github.onsdigital.zebedee.api.Root;
import com.github.onsdigital.zebedee.exceptions.CollectionNotFoundException;
import com.github.onsdigital.zebedee.json.Credentials;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.session.model.Session;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.onsdigital.zebedee.Builder.COLLECTION_ONE_NAME;
import static com.github.onsdigital.zebedee.Builder.COLLECTION_TWO_NAME;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


public class ZebedeeTest extends ZebedeeTestBaseFixture {

    Path expectedPath;
    Map<String, String> env;
    private static String PASSWORD = "1234";

    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        env = Root.env;
        Root.env = new HashMap<>();
        expectedPath = builder.parent;
    }

    @Override
    public void tearDown() throws Exception {
        // do nothing.
    }

    @Test
    public void shouldInstantiate() throws IOException {

        // Given
        // An existing Zebedee structure

        // When
        new Zebedee(new ZebedeeConfiguration(expectedPath, false));

        // Then
        // No error should occur.
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotInstantiate() throws IOException {

        // Given
        // No Zebedee structure
        FileUtils.deleteDirectory(expectedPath.toFile());

        // When
        new Zebedee(new ZebedeeConfiguration(expectedPath, false));

        // Then
        // An exception should be thrown
    }

    @Test
    public void shouldListReleases() throws IOException {

        // Given
        Zebedee zebedee = new Zebedee(new ZebedeeConfiguration(expectedPath, false));

        // When
        List<Collection> releases = zebedee.getCollections().list();

        // Then
        assertEquals(builder.collections.size(), releases.size());
    }

    @Test
    public void shouldNotBeBeingEdited() throws IOException {

        // Given
        Zebedee zebedee = new Zebedee(new ZebedeeConfiguration(expectedPath, false));

        // When
        int actual = zebedee.isBeingEdited(builder.contentUris.get(0));

        // Then
        assertEquals(0, actual);
    }

    @Test
    public void shouldBeBeingEdited() throws IOException {

        // Given
        Zebedee zebedee = new Zebedee(new ZebedeeConfiguration(expectedPath, false));
        String path = builder.contentUris.get(0).substring(1);
        Path reviewed = builder.collections.get(0).resolve(Collection.REVIEWED);
        Path beingEdited = reviewed.resolve(path);
        Files.createDirectories(beingEdited.getParent());
        Files.createFile(beingEdited);

        // When
        int actual = zebedee.isBeingEdited(builder.contentUris.get(0));

        // Then
        assertEquals(1, actual);
    }

    @Test
    public void toUri_givenCollectionFilePath_shouldReturnUri() throws IOException {
        // Given
        // a zebedee implementation
        Zebedee zebedee = new Zebedee(new ZebedeeConfiguration(expectedPath, false));
        String expectedURI = "/expected";
        String inprogress = "collections/mycollection/inprogress/expected/data.json";
        String complete = "collections/mycollection/complete/expected/data.json";
        String reviewed = "collections/mycollection/reviewed/expected/data.json";

        // When
        // we convert these to URIs
        String inProgressURI = zebedee.toUri(zebedee.getPath().resolve(inprogress));
        String completeURI = zebedee.toUri(zebedee.getPath().resolve(complete));
        String reviewedURI = zebedee.toUri(zebedee.getPath().resolve(reviewed));

        // Then
        // we expect the uri
        assertEquals(expectedURI, inProgressURI);
        assertEquals(expectedURI, completeURI);
        assertEquals(expectedURI, reviewedURI);
    }

    @Test
    public void toUri_givenCollectionFilePathAsString_shouldReturnUri() throws IOException {
        // Given
        // a zebedee implementation
        Zebedee zebedee = new Zebedee(new ZebedeeConfiguration(expectedPath, false));
        String expectedURI = "/expected";
        String inprogress = "collections/mycollection/inprogress/expected/data.json";
        String complete = "collections/mycollection/complete/expected/data.json";
        String reviewed = "collections/mycollection/reviewed/expected/data.json";

        // When
        // we convert these to URIs
        String inProgressURI = zebedee.toUri(zebedee.getPath().resolve(inprogress).toString());
        String completeURI = zebedee.toUri(zebedee.getPath().resolve(complete).toString());
        String reviewedURI = zebedee.toUri(zebedee.getPath().resolve(reviewed).toString());

        // Then
        // we expect the uri
        assertEquals(expectedURI, inProgressURI);
        assertEquals(expectedURI, completeURI);
        assertEquals(expectedURI, reviewedURI);
    }

    @Test
    public void toUri_givenPathOutsideZebedee_shouldReturnNull() throws IOException {
        // Given
        // a zebedee implementation
        Zebedee zebedee = new Zebedee(new ZebedeeConfiguration(expectedPath, false));
        String notZebedee = "/NotZebedee/data.json"; // (non zebedee path)
        Path notZebedeePath = Paths.get(notZebedee);

        // When
        // we convert these to URIs
        String notZebedeeUri = zebedee.toUri(notZebedeePath);

        // Then
        // we expect the uri to be null
        assertNull(notZebedeeUri);
    }

    @Test
    public void shouldReturnCollectionThatContainsSpecifiedURIIfExists() throws IOException, CollectionNotFoundException {
        Zebedee zebedee = new Zebedee(new ZebedeeConfiguration(expectedPath, false));
        Collection collectionOne = zebedee.getCollections().getCollectionByName(COLLECTION_ONE_NAME);
        Collection collectionTwo = zebedee.getCollections().getCollectionByName(COLLECTION_TWO_NAME);

        String contentPath = "/aboutus/data.json";

        // create content in collection 01.
        builder.createInProgressFile(contentPath);

        Session session = new Session("1234", "makingData@greatagain.com");
        Optional<Collection> blockingCollection = zebedee.checkForCollectionBlockingChange(collectionOne, contentPath);

        assertThat(blockingCollection.isPresent(), is(true));
        assertThat(blockingCollection.get().getDescription().getName(), equalTo(collectionTwo.getDescription().getName()));
        assertThat(collectionTwo.inProgressUris().contains(contentPath), is(true));
        assertThat(collectionTwo.completeUris().contains(contentPath), is(false));
        assertThat(collectionTwo.reviewedUris().contains(contentPath), is(false));
    }

    @Test
    public void shouldReturnEmptyOptionalIfNoCollectionContainsSpecifiedURI() throws IOException, CollectionNotFoundException {
        Zebedee zebedee = new Zebedee(new ZebedeeConfiguration(expectedPath, false));
        String contentPath = "/aboutus/data.json";
        Collection collectionOne = zebedee.getCollections().getCollectionByName(COLLECTION_ONE_NAME);

        Session session = new Session("1234", "makingData@greatagain.com");
        Optional<Collection> blockingCollection = zebedee.checkForCollectionBlockingChange(collectionOne, contentPath);
        assertThat(blockingCollection.isPresent(), is(false));

        Collection collectionTwo = zebedee.getCollections().getCollectionByName(COLLECTION_TWO_NAME);
        assertThat(collectionTwo.inProgressUris().contains(contentPath), is(false));
        assertThat(collectionTwo.completeUris().contains(contentPath), is(false));
        assertThat(collectionTwo.reviewedUris().contains(contentPath), is(false));
    }

    @Test
    public void testOpenSession_credentialsNull() throws Exception {
        setUpOpenSessionsTestMocks();

        // Given null credentials
        Credentials credentials = null;

        Zebedee zebedee = new Zebedee(zebCfg);

        // When openSession is called
        Session actual = zebedee.openSession(credentials);

        // Then null is returned
        assertThat(actual, is(nullValue()));
        verifyNoInteractions(sessions, usersService, collectionKeyring);
    }

    @Test
    public void testOpenSession_getUserByEmailException() throws Exception {
        // Given userService.getUserByEmail throws an exception
        setUpOpenSessionsTestMocks();

        when(credentials.getEmail())
                .thenReturn(TEST_EMAIL);

        when(usersService.getUserByEmail(any()))
                .thenThrow(new IOException("boom"));

        Zebedee zebedee = new Zebedee(zebCfg);

        // When openSession is called
        IOException ex = assertThrows(IOException.class, () -> zebedee.openSession(credentials));

        // Then an exception is thrown
        assertThat(ex.getMessage(), equalTo("boom"));
        verify(usersService, times(1)).getUserByEmail(TEST_EMAIL);
        verifyNoMoreInteractions(sessions, usersService, collectionKeyring);
    }

    @Test
    public void testOpenSession_getUserByEmailReturnsNull() throws Exception {
        // Given userService.getUserByEmail returns null
        setUpOpenSessionsTestMocks();

        when(credentials.getEmail())
                .thenReturn(TEST_EMAIL);

        when(usersService.getUserByEmail(any()))
                .thenReturn(null);

        Zebedee zebedee = new Zebedee(zebCfg);

        // When openSession is called
        Session actual = zebedee.openSession(credentials);

        // Then null is returned
        assertThat(actual, is(nullValue()));
        verify(usersService, times(1)).getUserByEmail(TEST_EMAIL);

        verifyNoInteractions(sessions, collectionKeyring);
    }

    @Test
    public void testOpenSession_createSessionThrowsException() throws Exception {
        // Given sessions.create throws an exception
        setUpOpenSessionsTestMocks();

        when(credentials.getEmail())
                .thenReturn(TEST_EMAIL);

        when(usersService.getUserByEmail(any()))
                .thenReturn(user);

        when(user.getEmail())
                .thenReturn(TEST_EMAIL);

        when(sessions.create(TEST_EMAIL))
                .thenThrow(new IOException("boom"));

        Zebedee zebedee = new Zebedee(zebCfg);

        // When openSession is called
        IOException ex = assertThrows(IOException.class, () -> zebedee.openSession(credentials));

        // Then an exception is thrown
        assertThat(ex.getCause().getMessage(), equalTo("boom"));
        verify(usersService, times(1)).getUserByEmail(TEST_EMAIL);
        verify(sessions, times(1)).create(TEST_EMAIL);

        verifyNoInteractions(collectionKeyring);
    }

    @Test
    public void testOpenSession_success_shouldReturnSession() throws Exception {
        // Given open session is successful
        setUpOpenSessionsTestMocks();

        when(credentials.getEmail())
                .thenReturn(TEST_EMAIL);

        when(credentials.getPassword())
                .thenReturn(PASSWORD);

        when(usersService.getUserByEmail(TEST_EMAIL))
                .thenReturn(user);

        when(user.getEmail())
                .thenReturn(TEST_EMAIL);

        when(sessions.create(TEST_EMAIL))
                .thenReturn(userSession);

        Zebedee zebedee = new Zebedee(zebCfg);

        // When openSession is called
        Session actual = zebedee.openSession(credentials);

        // Then the expected session is returned
        assertThat(actual, equalTo(userSession));
        verify(usersService, times(1)).getUserByEmail(TEST_EMAIL);
        verify(sessions, times(1)).create(TEST_EMAIL);
    }
}
