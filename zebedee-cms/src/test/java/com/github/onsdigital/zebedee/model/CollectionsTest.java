package com.github.onsdigital.zebedee.model;

import com.github.davidcarboni.cryptolite.Keys;
import com.github.onsdigital.zebedee.Zebedee;
import com.github.onsdigital.zebedee.data.json.DirectoryListing;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.ConflictException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.json.ApprovalStatus;
import com.github.onsdigital.zebedee.json.CollectionDescription;
import com.github.onsdigital.zebedee.json.CollectionType;
import com.github.onsdigital.zebedee.json.Event;
import com.github.onsdigital.zebedee.json.EventType;
import com.github.onsdigital.zebedee.keyring.CollectionKeyring;
import com.github.onsdigital.zebedee.model.approval.ApproveTask;
import com.github.onsdigital.zebedee.model.encryption.EncryptionKeyFactory;
import com.github.onsdigital.zebedee.model.publishing.PublishNotification;
import com.github.onsdigital.zebedee.permissions.service.PermissionsService;
import com.github.onsdigital.zebedee.reader.CollectionReader;
import com.github.onsdigital.zebedee.reader.ContentReader;
import com.github.onsdigital.zebedee.reader.ZebedeeReader;
import com.github.onsdigital.zebedee.session.model.Session;
import com.github.onsdigital.zebedee.user.model.User;
import com.github.onsdigital.zebedee.user.service.UsersService;
import com.github.onsdigital.zebedee.util.ZebedeeCmsService;
import com.github.onsdigital.zebedee.util.versioning.VersionsService;
import org.apache.commons.fileupload.FileUploadException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Created by dave on 19/05/2017.
 */
public class CollectionsTest {
    private static final String TEST_EMAIL = "TEST@ons.gov.uk";
    private static final String COLLECTION_ID = "123";

    @Rule
    public TemporaryFolder rootDir = new TemporaryFolder();

    @Mock
    private PermissionsService permissionsServiceMock;

    @Mock
    private ZebedeeCmsService zebedeeCmsService;

    @Mock
    private VersionsService versionsService;

    @Mock
    private ZebedeeReader zebedeeReader;

    @Mock
    private Zebedee zebedeeMock;

    @Mock
    private Session sessionMock;

    @Mock
    private UsersService usersServiceMock;

    @Mock
    private Collection collectionMock;

    @Mock
    private CollectionDescription collectionDescriptionMock;

    @Mock
    private Content publishedContentMock;

    @Mock
    private HttpServletRequest requestMock;

    @Mock
    private CollectionReaderWriterFactory collectionReaderWriterFactoryMock;

    @Mock
    private CollectionWriter collectionWriterMock;

    @Mock
    private CollectionReader collectionReaderMock;

    @Mock
    private PublishNotification publishNotification;

    @Mock
    private ContentWriter contentWriterMock;

    @Mock
    private ContentReader contentReaderMock;

    @Mock
    private Future<Boolean> futureMock;

    @Mock
    private EncryptionKeyFactory encryptionKeyFactory;

    @Mock
    private CollectionKeyring collectionKeyring;

    private Collections collections;
    private Path collectionsPath;
    private User testUser;
    private Supplier<Zebedee> zebedeeSupplier;
    private BiConsumer<Collection, EventType> publishingNotificationConsumer;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);

        testUser = new User();
        testUser.setEmail(TEST_EMAIL);
        rootDir.create();
        collectionsPath = rootDir.newFolder("collections").toPath();

        // Test target.
        collections = new Collections(collectionsPath, permissionsServiceMock, versionsService, publishedContentMock);

        zebedeeSupplier = () -> zebedeeMock;
        publishingNotificationConsumer = (c, e) -> publishNotification.sendNotification(e);

        when(sessionMock.getEmail())
                .thenReturn(TEST_EMAIL);

        when(collectionMock.getDescription())
                .thenReturn(collectionDescriptionMock);

        when(collectionDescriptionMock.getId())
                .thenReturn(COLLECTION_ID);

        ReflectionTestUtils.setField(collections, "zebedeeSupplier", zebedeeSupplier);
        ReflectionTestUtils.setField(collections, "collectionReaderWriterFactory", collectionReaderWriterFactoryMock);
        ReflectionTestUtils.setField(collections, "publishingNotificationConsumer", publishingNotificationConsumer);
        ReflectionTestUtils.setField(collections, "zebedeeCmsService", zebedeeCmsService);
    }

    @Test
    public void shouldFindCollection() throws Exception {
        CollectionDescription desc = new CollectionDescription();
        desc.setName("test");
        desc.setType(CollectionType.manual);

        when(zebedeeMock.getCollections())
                .thenReturn(collections);
        when(zebedeeMock.getUsersService())
                .thenReturn(usersServiceMock);
        when(zebedeeMock.getEncryptionKeyFactory())
                .thenReturn(encryptionKeyFactory);
        when(zebedeeMock.getCollectionKeyring())
                .thenReturn(collectionKeyring);
        when(collectionReaderWriterFactoryMock.getWriter(zebedeeMock, collectionMock, sessionMock))
                .thenReturn(collectionWriterMock);

        SecretKey key = Keys.newSecretKey();
        when(encryptionKeyFactory.newCollectionKey())
                .thenReturn(key);

        when(collectionKeyring.get(any(), any()))
                .thenReturn(key);

        Collection created = Collection.create(desc, zebedeeMock, sessionMock);
        Collection found = collections.getCollection(created.getDescription().getId());

        assertThat(created.getDescription().getId(), equalTo(found.getDescription().getId()));
        assertThat(created.getDescription().getName(), equalTo(found.getDescription().getName()));
        assertThat(created.getDescription().getType(), equalTo(found.getDescription().getType()));
    }

    @Test
    public void shouldReturnNullIfNotFound() throws Exception {
        assertThat(collections.getCollection("A_Girl_Is_No_One"), equalTo(null));
    }

    @Test(expected = BadRequestException.class)
    public void shouldThrowBadRequestForNullCollectionOnApprove() throws IOException, ZebedeeException {
        collections.approve(null, sessionMock, null);
    }

    @Test(expected = BadRequestException.class)
    public void shouldThrowBadRequestForNullCollectionOnListDirectory() throws IOException, UnauthorizedException,
            BadRequestException, ConflictException, NotFoundException {
        collections.listDirectory(null, "somefile.json", sessionMock);
    }

    @Test(expected = BadRequestException.class)
    public void shouldThrowBadRequestForNullCollectionOnComplete() throws IOException, ZebedeeException {
        collections.complete(null, "someURI", sessionMock, false);
    }

    @Test(expected = BadRequestException.class)
    public void shouldThrowBadRequestForNullCollectionOnDelete() throws IOException, ZebedeeException {
        collections.delete(null, sessionMock);
    }

    @Test(expected = NotFoundException.class)
    public void shouldThrowNotFoundForNullCollectionOnWriteContent() throws IOException, ZebedeeException,
            FileUploadException {
        HttpServletRequest request = null;
        InputStream inputStream = null;

        when(collectionReaderWriterFactoryMock.getWriter(zebedeeMock, null, sessionMock))
                .thenThrow(new NotFoundException(""));

        collections.writeContent(null, "someURI", sessionMock, request,
                inputStream, false, false);
    }

    @Test(expected = BadRequestException.class)
    public void shouldThrowBadRequestForNullCollectionOnDeleteContent() throws IOException, ZebedeeException {
        collections.deleteContent(null, "someURI", sessionMock);
    }

    @Test(expected = BadRequestException.class)
    public void shouldThrowBadRequestForNullCollectionOnMoveContent() throws IOException, ZebedeeException {
        collections.moveContent(sessionMock, null, "to", "from");
    }

    @Test(expected = BadRequestException.class)
    public void shouldThrowBadRequestForBlankUriOnMoveContent() throws IOException, ZebedeeException {

        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);

        try {
            collections.moveContent(sessionMock, collectionMock, "", "toURI");
        } catch (BadRequestException e) {
            verify(permissionsServiceMock, times(1))
                    .canEdit(sessionMock);
            verifyNoInteractions(collectionMock, publishedContentMock);
            throw e;
        }
    }

    @Test(expected = BadRequestException.class)
    public void shouldThrowBadRequestForBlankToUriOnMoveContent() throws IOException, ZebedeeException {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);

        try {
            collections.moveContent(sessionMock, collectionMock, "fromURI", "");
        } catch (BadRequestException e) {
            verify(permissionsServiceMock, times(1))
                    .canEdit(sessionMock);
            verifyNoInteractions(collectionMock, publishedContentMock);
            throw e;
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void shouldThrowUnauthorizedIfNotLoggedInOnApprove() throws IOException, ZebedeeException {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(false);
        try {
            collections.approve(collectionMock, sessionMock, null);
        } catch (UnauthorizedException e) {
            verify(permissionsServiceMock, times(1))
                    .canEdit(sessionMock);
            verifyNoInteractions(publishedContentMock, zebedeeMock);
            throw e;
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void shouldThrowUnauthorizedIfNotLoggedInOnListDirectory() throws IOException, UnauthorizedException,
            BadRequestException,
            ConflictException, NotFoundException {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(false);

        try {
            collections.listDirectory(collectionMock, "someURI", sessionMock);
        } catch (UnauthorizedException e) {
            verify(permissionsServiceMock, times(1))
                    .canView(sessionMock, COLLECTION_ID);
            verify(collectionMock, times(1))
                    .getDescription();
            verify(collectionMock, never())
                    .find(any());
            throw e;
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void shouldThrowUnauthorizedIfNotLoggedInOnComplete() throws IOException, ZebedeeException {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(false);

        try {
            collections.complete(collectionMock, "someURI", sessionMock, false);
        } catch (UnauthorizedException e) {
            verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
            verifyNoInteractions(collectionMock);
            throw e;
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void shouldThrowUnauthorizedIfNotLoggedInOnDelete() throws IOException, ZebedeeException {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(false);
        try {
            collections.delete(collectionMock, sessionMock);
        } catch (UnauthorizedException e) {
            verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
            verifyNoInteractions(collectionMock);
            throw e;
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void shouldThrowUnauthorizedIfNotLoggedInOnWriteContent() throws IOException, ZebedeeException,
            FileUploadException {
        InputStream inputStreamMock = mock(InputStream.class);
        when(collectionReaderWriterFactoryMock.getWriter(zebedeeMock, collectionMock, sessionMock))
                .thenThrow(new UnauthorizedException(""));
        try {
            collections.writeContent(collectionMock, "someURI", sessionMock, requestMock,
                    inputStreamMock, false, true);
        } catch (UnauthorizedException e) {
            verify(collectionReaderWriterFactoryMock, times(1)).getWriter(zebedeeMock, collectionMock, sessionMock);
            verifyNoInteractions(collectionMock);
            throw e;
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void shouldThrowUnauthorizedIfNotLoggedInOnDeleteContent() throws IOException, ZebedeeException {
        try {
            when(permissionsServiceMock.canEdit(sessionMock))
                    .thenReturn(false);
            collections.deleteContent(collectionMock, "someURI", sessionMock);
        } catch (UnauthorizedException e) {
            verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
            verifyNoInteractions(collectionMock);
            throw e;
        }
    }

    @Test(expected = UnauthorizedException.class)
    public void shouldThrowUnauthorizedIfNotLoggedInOnMoveContent() throws IOException, ZebedeeException {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(false);
        try {
            collections.moveContent(sessionMock, collectionMock, "from", "to");
        } catch (UnauthorizedException e) {
            verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
            verifyNoInteractions(collectionMock, publishedContentMock);
            throw e;
        }
    }

    @Test(expected = BadRequestException.class)
    public void shouldThrowBadRequestIfNoUriOnWriteContent() throws IOException, ZebedeeException, FileUploadException {
        InputStream inputStreamMock = mock(InputStream.class);

        when(collectionReaderWriterFactoryMock.getWriter(zebedeeMock, collectionMock, sessionMock))
                .thenReturn(collectionWriterMock);
        when(collectionDescriptionMock.getName())
                .thenReturn("AGirlIsNoOne");
        when(collectionDescriptionMock.getApprovalStatus())
                .thenReturn(ApprovalStatus.IN_PROGRESS);

        try {
            collections.writeContent(collectionMock, null, sessionMock, requestMock,
                    inputStreamMock, false, true);
        } catch (BadRequestException e) {
            verify(collectionReaderWriterFactoryMock, times(1)).getWriter(zebedeeMock, collectionMock, sessionMock);
            verify(sessionMock, times(1)).getEmail();
            verify(collectionMock, times(2)).getDescription();
            verifyNoInteractions(permissionsServiceMock);
            verify(collectionMock, never()).find(anyString());
            verify(collectionMock, never()).create(any(Session.class), anyString());
            verify(collectionMock, never()).edit(any(Session.class), anyString(), eq(collectionWriterMock), anyBoolean());
            throw e;
        }
    }

    @Test(expected = BadRequestException.class)
    public void shouldThrowBadRequestIfNoUriOnDeleteContent() throws IOException, ZebedeeException {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);
        try {
            collections.deleteContent(collectionMock, null, sessionMock);
        } catch (BadRequestException e) {
            verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
            verifyNoInteractions(collectionMock, collectionDescriptionMock, zebedeeMock);
            throw e;
        }
    }

    @Test(expected = NotFoundException.class)
    public void shouldThrowNotFoundIfUriNotInProgressOnComplete() throws IOException, ZebedeeException {
        Content inProg = mock(Content.class);

        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);
        when(collectionMock.getInProgress())
                .thenReturn(inProg);
        try {
            collections.complete(collectionMock, "someURI", sessionMock, false);
        } catch (NotFoundException e) {
            verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
            verify(collectionMock, times(1)).getInProgress();
            verify(inProg, times(1)).get(anyString());
            verify(collectionMock, never()).complete(any(Session.class), anyString(), anyBoolean());
            verify(collectionMock, never()).save();
            throw e;
        }
    }

    @Test(expected = BadRequestException.class)
    public void shouldThrowBadRequestIfUriIsADirectoryOnComplete() throws IOException, ZebedeeException {
        String uri = "someURI";
        Path p = rootDir.newFolder("test").toPath();
        Content inProg = mock(Content.class);

        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);
        when(collectionMock.getInProgress())
                .thenReturn(inProg);
        when(inProg.get(uri))
                .thenReturn(p);

        try {
            collections.complete(collectionMock, uri, sessionMock, false);
        } catch (NotFoundException e) {
            verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
            verify(collectionMock, times(1)).getInProgress();
            verify(inProg, times(1)).get(uri);
            verify(collectionMock, never()).complete(any(Session.class), anyString(), anyBoolean());
            verify(collectionMock, never()).save();
            throw e;
        }
    }

    @Test
    public void shouldCompleteContent() throws IOException, ZebedeeException {
        Path p = rootDir.newFile("data.json").toPath();
        Content inProg = mock(Content.class);

        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);
        when(collectionMock.getInProgress())
                .thenReturn(inProg);
        when(inProg.get(p.toString()))
                .thenReturn(p);
        ;
        when(collectionDescriptionMock.getName())
                .thenReturn("AGirlIsNoOne");
        when(collectionDescriptionMock.getId())
                .thenReturn("1234567890");
        when(collectionMock.complete(sessionMock, p.toString(), false))
                .thenReturn(true);

        collections.complete(collectionMock, p.toString(), sessionMock, false);

        verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
        verify(collectionMock, times(1)).getInProgress();
        verify(inProg, times(1)).get(p.toString());
        verify(collectionMock, times(1)).complete(sessionMock, p.toString(), false);
        verify(collectionMock, times(1)).save();
    }

    @Test(expected = BadRequestException.class)
    public void shouldThrowErrorIfCompleteUnsucessful() throws IOException, ZebedeeException {
        Path p = rootDir.newFile("data.json").toPath();
        Content inProg = mock(Content.class);

        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);
        when(collectionMock.getInProgress())
                .thenReturn(inProg);
        when(inProg.get(p.toString()))
                .thenReturn(p);
        when(collectionDescriptionMock.getName())
                .thenReturn("AGirlIsNoOne");
        when(collectionDescriptionMock.getId())
                .thenReturn("1234567890");
        when(collectionMock.complete(sessionMock, p.toString(), false))
                .thenReturn(false);

        try {
            collections.complete(collectionMock, p.toString(), sessionMock, false);
        } catch (BadRequestException e) {
            verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
            verify(collectionMock, times(1)).getInProgress();
            verify(inProg, times(1)).get(p.toString());
            verify(collectionMock, times(1)).complete(sessionMock, p.toString(), false);
            verify(collectionMock, never()).save();
            throw e;
        }
    }

    @Test(expected = ConflictException.class)
    public void shouldNotApproveIfAUriIsInProgress() throws IOException, ZebedeeException {
        List<String> inProgressURIS = new ArrayList<>();
        inProgressURIS.add("data.json");

        CollectionDescription description = mock(CollectionDescription.class);
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);
        when(collectionMock.inProgressUris())
                .thenReturn(inProgressURIS);
        when(collectionMock.completeUris())
                .thenReturn(new ArrayList<String>());
        when(collectionMock.getDescription())
                .thenReturn(description);

        doNothing().when(description).addEvent(eventCaptor.capture());

        try {
            collections.approve(collectionMock, sessionMock, null);
        } catch (ConflictException e) {
            verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
            verify(collectionMock, times(1)).inProgressUris();
            assertThat(eventCaptor.getValue().type, equalTo(EventType.APPROVE_SUBMITTED));
            verify(collectionReaderWriterFactoryMock, never()).getReader(any(), any(), any());
            verify(collectionReaderWriterFactoryMock, never()).getWriter(any(), any(), any());
            throw e;
        }
    }

    @Test(expected = ConflictException.class)
    public void shouldNotApproveIfAUriIsComplete() throws IOException, ZebedeeException {
        List<String> completeURIS = new ArrayList<>();
        completeURIS.add("data.json");

        CollectionDescription description = mock(CollectionDescription.class);
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);

        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);
        when(collectionMock.isAllContentReviewed(false))
                .thenReturn(false);
        when(collectionMock.inProgressUris())
                .thenReturn(new ArrayList<String>());
        when(collectionMock.completeUris())
                .thenReturn(completeURIS);
        when(collectionMock.getDescription())
                .thenReturn(description);

        doNothing().when(description).addEvent(eventCaptor.capture());

        try {
            collections.approve(collectionMock, sessionMock, null);
        } catch (ConflictException e) {
            verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
            verify(collectionMock, times(1)).inProgressUris();
            verify(collectionMock, times(1)).completeUris();
            assertThat(eventCaptor.getValue().type, equalTo(EventType.APPROVE_SUBMITTED));
            verify(collectionReaderWriterFactoryMock, never()).getReader(any(), any(), any());
            verify(collectionReaderWriterFactoryMock, never()).getWriter(any(), any(), any());
            throw e;
        }
    }

    @Test
    public void shouldApproveCollection() throws IOException, ZebedeeException, ExecutionException,
            InterruptedException {
        Function<Path, ContentReader> contentReaderFunction = (p) -> contentReaderMock;
        Function<ApproveTask, Future<Boolean>> addTaskToQueue = (t) -> futureMock;

        ReflectionTestUtils.setField(collections, "contentReaderFactory", contentReaderFunction);
        ReflectionTestUtils.setField(collections, "addTaskToQueue", addTaskToQueue);
        ReflectionTestUtils.setField(collections, "addTaskToQueue", addTaskToQueue);

        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);
        when(collectionMock.isAllContentReviewed(anyBoolean()))
                .thenReturn(true);
        when(collectionReaderWriterFactoryMock.getReader(zebedeeMock, collectionMock, sessionMock))
                .thenReturn(collectionReaderMock);
        when(collectionReaderWriterFactoryMock.getWriter(zebedeeMock, collectionMock, sessionMock))
                .thenReturn(collectionWriterMock);
        when(zebedeeCmsService.getZebedeeReader())
                .thenReturn(zebedeeReader);

        assertThat(futureMock, equalTo(collections.approve(collectionMock, sessionMock, null)));

        verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
        verify(collectionMock, times(1)).isAllContentReviewed(anyBoolean());
        verify(collectionReaderWriterFactoryMock, times(1)).getReader(zebedeeMock, collectionMock, sessionMock);
        verify(collectionReaderWriterFactoryMock, times(1)).getWriter(zebedeeMock, collectionMock, sessionMock);
    }

    @Test
    public void shouldUnlockCollection() throws IOException, ZebedeeException, ExecutionException, InterruptedException {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);
        when(collectionDescriptionMock.getApprovalStatus())
                .thenReturn(ApprovalStatus.COMPLETE);
        when(collectionMock.save())
                .thenReturn(true);

        boolean result = collections.unlock(collectionMock, sessionMock);

        assertThat(result, is(true));
        verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
        verify(collectionMock, times(3)).getDescription();
        verify(collectionDescriptionMock, times(1)).getApprovalStatus();
        verify(collectionDescriptionMock, times(1)).setApprovalStatus(ApprovalStatus.NOT_STARTED);
        verify(collectionDescriptionMock, times(1)).addEvent(any(Event.class));
        verify(publishNotification, times(1)).sendNotification(EventType.UNLOCKED);
        verify(collectionMock, times(1)).save();
    }

    @Test
    public void shouldUnlockWithoutAddingEventIfAlreadyUnlocked() throws Exception {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);
        when(collectionDescriptionMock.getApprovalStatus())
                .thenReturn(ApprovalStatus.IN_PROGRESS);
        when(collectionMock.save())
                .thenReturn(true);

        boolean result = collections.unlock(collectionMock, sessionMock);

        assertThat(result, is(true));
        verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
        verify(collectionMock, times(1)).getDescription();
        verify(collectionDescriptionMock, times(1)).getApprovalStatus();
        verify(collectionDescriptionMock, never()).setApprovalStatus(any(ApprovalStatus.class));
        verify(collectionDescriptionMock, never()).addEvent(any(Event.class));
        verify(publishNotification, never()).sendNotification(any(EventType.class));
        verify(collectionMock, never()).save();
    }

    @Test(expected = UnauthorizedException.class)
    public void shouldThrowUnauthorizedIfNotLoggedInOnUnlock() throws Exception {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(false);
        try {
            collections.unlock(collectionMock, sessionMock);
        } catch (UnauthorizedException e) {
            verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
            verify(collectionMock, never()).getDescription();
            verify(collectionDescriptionMock, never()).getApprovalStatus();
            verify(collectionDescriptionMock, never()).setApprovalStatus(any(ApprovalStatus.class));
            verify(collectionDescriptionMock, never()).addEvent(any(Event.class));
            verify(publishNotification, never()).sendNotification(any(EventType.class));
            verify(collectionMock, never()).save();
            throw e;
        }
    }

    @Test(expected = BadRequestException.class)
    public void shouldThrowBadRequestForNullCollectionOnUnlock() throws IOException, ZebedeeException {
        try {
            collections.unlock(null, sessionMock);
        } catch (UnauthorizedException e) {
            verify(permissionsServiceMock, never()).canEdit(sessionMock);
            verify(collectionMock, never()).getDescription();
            verify(collectionDescriptionMock, never()).getApprovalStatus();
            verify(collectionDescriptionMock, never()).setApprovalStatus(any(ApprovalStatus.class));
            verify(collectionDescriptionMock, never()).addEvent(any(Event.class));
            verify(publishNotification, never()).sendNotification(any(EventType.class));
            verify(collectionMock, never()).save();
            throw e;
        }
    }

    @Test(expected = NotFoundException.class)
    public void shouldGetNotFoundIfAttemptingToListNonexistentDirectory() throws IOException, UnauthorizedException,
            BadRequestException, ConflictException, NotFoundException {
        String uri = "someURI";
        when(permissionsServiceMock.canView(sessionMock, COLLECTION_ID))
                .thenReturn(true);
        when(collectionMock.find(uri))
                .thenReturn(null);
        try {
            collections.listDirectory(collectionMock, uri, sessionMock);
        } catch (NotFoundException e) {
            verify(permissionsServiceMock, times(1)).canView(sessionMock, COLLECTION_ID);
            verify(collectionMock, times(1)).find(uri);
            throw e;
        }
    }

    @Test(expected = BadRequestException.class)
    public void shouldGetBadRequestIfAttemptingToListDirectoryOnAFile() throws IOException, UnauthorizedException,
            BadRequestException, ConflictException, NotFoundException {
        Path uri = rootDir.newFile("data.json").toPath();
        when(permissionsServiceMock.canView(sessionMock, COLLECTION_ID))
                .thenReturn(true);
        when(collectionMock.find(uri.toString()))
                .thenReturn(uri);
        try {
            collections.listDirectory(collectionMock, uri.toString(), sessionMock);
        } catch (BadRequestException e) {
            verify(permissionsServiceMock, times(1)).canView(sessionMock, COLLECTION_ID);
            verify(collectionMock, times(1)).find(uri.toString());
            throw e;
        }
    }

    @Test
    public void shouldListDirectory() throws IOException, UnauthorizedException, BadRequestException, ConflictException,
            NotFoundException {
        Path file1 = rootDir.newFile("data.json").toPath();
        Path file2 = rootDir.newFile("chart.png").toPath();

        DirectoryListing expected = new DirectoryListing();
        expected.getFiles().put(file1.getFileName().toString(), file1.toString());
        expected.getFiles().put(file2.getFileName().toString(), file2.toString());
        expected.getFolders().put(collectionsPath.getFileName().toString(), collectionsPath.toString());

        when(permissionsServiceMock.canView(sessionMock, COLLECTION_ID))
                .thenReturn(true);
        when(collectionMock.find(rootDir.getRoot().toString()))
                .thenReturn(rootDir.getRoot().toPath());

        DirectoryListing result = collections.listDirectory(collectionMock, rootDir.getRoot().toString(), sessionMock);

        assertThat(result, equalTo(expected));
        verify(permissionsServiceMock, times(1)).canView(sessionMock, COLLECTION_ID);
        verify(collectionMock, times(1)).find(rootDir.getRoot().toString());
    }

    @Test
    public void shouldListDirectoryOverlayed() throws IOException, ZebedeeException {
        Path uri = rootDir.getRoot().toPath().resolve("economy");
        uri.toFile().mkdir();

        File f1 = uri.resolve("data.json").toFile();
        File f2 = uri.resolve("data1.json").toFile();
        f1.createNewFile();
        f2.createNewFile();

        DirectoryListing expected = new DirectoryListing();
        expected.getFiles().put(f1.getName(), f1.toPath().toString());
        expected.getFiles().put(f2.getName(), f2.toPath().toString());

        when(permissionsServiceMock.canView(sessionMock, COLLECTION_ID))
                .thenReturn(true);
        when(collectionMock.find(uri.toString()))
                .thenReturn(uri);
        when(publishedContentMock.get(uri.toString()))
                .thenReturn(uri);

        DirectoryListing result = collections.listDirectoryOverlayed(collectionMock, uri.toString(), sessionMock);

        assertThat(result, equalTo(expected));
        verify(permissionsServiceMock, times(1)).canView(sessionMock, COLLECTION_ID);
        verify(collectionMock, times(1)).find(uri.toString());
    }

    @Test(expected = BadRequestException.class)
    public void shouldNotDeleteCollectionIfNotEmpty() throws IOException, ZebedeeException {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);
        when(collectionMock.isEmpty())
                .thenReturn(false);
        try {
            collections.delete(collectionMock, sessionMock);
        } catch (BadRequestException e) {
            verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
            verify(collectionMock, times(1)).isEmpty();
            verify(collectionMock, never()).delete();
            throw e;
        }
    }

    @Test
    public void shouldDeleteCollection() throws IOException, ZebedeeException {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);
        when(collectionMock.isEmpty())
                .thenReturn(true);

        collections.delete(collectionMock, sessionMock);

        verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
        verify(collectionMock, times(1)).isEmpty();
        verify(collectionMock, times(1)).delete();
    }

    @Test(expected = BadRequestException.class)
    public void shouldThrowBadRequestForWritingADirectoryAsAFile() throws IOException, ZebedeeException,
            FileUploadException {
        Path uri = rootDir.newFolder("test").toPath();

        when(collectionReaderWriterFactoryMock.getWriter(zebedeeMock, collectionMock, sessionMock))
                .thenReturn(collectionWriterMock);
        when(collectionDescriptionMock.getApprovalStatus())
                .thenReturn(ApprovalStatus.IN_PROGRESS);
        when(collectionMock.find(uri.toString()))
                .thenReturn(uri);
        try {
            collections.writeContent(collectionMock, uri.toString(), sessionMock, requestMock, mock(InputStream.class),
                    false, false);
        } catch (BadRequestException e) {
            verify(collectionReaderWriterFactoryMock, times(1)).getWriter(zebedeeMock, collectionMock, sessionMock);
            verify(collectionDescriptionMock, times(1)).getApprovalStatus();
            verify(collectionMock, times(1)).find(uri.toString());
            verify(collectionMock, never()).save();
            verify(collectionMock, never()).edit(any(Session.class), anyString(), any(), anyBoolean());
            verify(collectionMock, never()).create(any(Session.class), anyString());
            verify(collectionMock, never()).getInProgressPath(anyString());
            verify(collectionWriterMock, never()).getInProgress();
            throw e;
        }
    }


    @Test(expected = ConflictException.class)
    public void shouldThrowConflictForCreatingFileBeingEditedElsewhere() throws IOException, ZebedeeException,
            FileUploadException {
        Path uri = rootDir.newFile("data.json").toPath();

        when(collectionReaderWriterFactoryMock.getWriter(zebedeeMock, collectionMock, sessionMock))
                .thenReturn(collectionWriterMock);
        when(collectionDescriptionMock.getApprovalStatus())
                .thenReturn(ApprovalStatus.IN_PROGRESS);
        when(collectionMock.find(uri.toString()))
                .thenReturn(null);
        when(collectionMock.edit(sessionMock, uri.toString(), collectionWriterMock, false))
                .thenReturn(false);
        when(zebedeeMock.checkForCollectionBlockingChange(uri.toString()))
                .thenReturn(Optional.empty());
        try {
            collections.writeContent(collectionMock, uri.toString(), sessionMock, requestMock, mock(InputStream.class),
                    false, false);
        } catch (BadRequestException e) {
            verify(collectionReaderWriterFactoryMock, times(1)).getWriter(zebedeeMock, collectionMock, sessionMock);
            verify(collectionDescriptionMock, times(1)).getApprovalStatus();
            verify(collectionMock, times(1)).find(uri.toString());
            verify(collectionMock, times(1)).edit(sessionMock, uri.toString(), collectionWriterMock, false);
            verify(collectionMock, never()).save();
            verify(collectionMock, never()).create(any(Session.class), anyString());
            verify(collectionMock, never()).edit(any(Session.class), anyString(), any(), anyBoolean());
            verify(collectionMock, never()).getInProgressPath(anyString());
            verify(collectionWriterMock, never()).getInProgress();
            throw e;
        }
    }

    @Test(expected = ConflictException.class)
    public void shouldThrowConflictForEditingFileBeingEditedElsewhere() throws IOException, ZebedeeException,
            FileUploadException {
        Path uri = rootDir.newFile("data.json").toPath();

        when(collectionReaderWriterFactoryMock.getWriter(zebedeeMock, collectionMock, sessionMock))
                .thenReturn(collectionWriterMock);
        when(collectionDescriptionMock.getApprovalStatus())
                .thenReturn(ApprovalStatus.IN_PROGRESS);
        when(collectionMock.find(uri.toString()))
                .thenReturn(uri);
        when(collectionMock.edit(sessionMock, uri.toString(), collectionWriterMock, false))
                .thenReturn(false);
        when(zebedeeMock.checkForCollectionBlockingChange(uri.toString()))
                .thenReturn(Optional.empty());
        try {
            collections.writeContent(collectionMock, uri.toString(), sessionMock, requestMock, mock(InputStream.class),
                    false, false);
        } catch (BadRequestException e) {
            verify(collectionReaderWriterFactoryMock, times(1)).getWriter(zebedeeMock, collectionMock, sessionMock);
            verify(collectionDescriptionMock, times(1)).getApprovalStatus();
            verify(collectionMock, times(1)).find(uri.toString());
            verify(collectionMock, times(1)).edit(sessionMock, uri.toString(), collectionWriterMock, false);
            verify(collectionMock, never()).save();
            verify(collectionMock, never()).create(any(Session.class), anyString());
            verify(collectionMock, never()).edit(any(Session.class), anyString(), any(), anyBoolean());
            verify(collectionMock, never()).getInProgressPath(anyString());
            verify(collectionWriterMock, never()).getInProgress();
            throw e;
        }
    }

    @Test
    public void shouldWriteContent() throws IOException, ZebedeeException, FileUploadException {
        Path uri = rootDir.newFile("data.json").toPath();
        InputStream in = mock(InputStream.class);

        when(collectionReaderWriterFactoryMock.getWriter(zebedeeMock, collectionMock, sessionMock))
                .thenReturn(collectionWriterMock);
        when(collectionDescriptionMock.getApprovalStatus())
                .thenReturn(ApprovalStatus.IN_PROGRESS);
        when(collectionMock.find(uri.toString()))
                .thenReturn(uri);
        when(collectionMock.edit(sessionMock, uri.toString(), collectionWriterMock, false))
                .thenReturn(true);
        when(collectionMock.getInProgressPath(uri.toString()))
                .thenReturn(uri);
        when(collectionWriterMock.getInProgress())
                .thenReturn(contentWriterMock);

        collections.writeContent(collectionMock, uri.toString(), sessionMock, requestMock, in, false, false);

        verify(collectionReaderWriterFactoryMock, times(1)).getWriter(zebedeeMock, collectionMock, sessionMock);
        verify(collectionDescriptionMock, times(1)).getApprovalStatus();
        verify(collectionMock, times(1)).find(uri.toString());
        verify(collectionMock, times(1)).edit(sessionMock, uri.toString(), collectionWriterMock, false);
        verify(collectionMock, times(1)).save();
        verify(collectionMock, never()).create(any(Session.class), anyString());
        verify(collectionMock, times(1)).getInProgressPath(uri.toString());
        verify(collectionWriterMock, times(1)).getInProgress();
        verify(contentWriterMock, times(1)).write(in, uri.toString());
    }

    @Test(expected = NotFoundException.class)
    public void shouldThrowNotFoundForDeletingNonexistentFile() throws IOException, ZebedeeException {
        String uri = "someURI";
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);
        try {
            collections.deleteContent(collectionMock, uri, sessionMock);
        } catch (NotFoundException e) {
            verify(permissionsServiceMock, times(1)).canEdit(sessionMock);
            verify(collectionMock, times(1)).find(uri);
            verify(collectionMock, never()).isInCollection(anyString());
            verify(collectionMock, never()).getDescription();
            verify(collectionMock, never()).deleteDataVisContent(any(), any());
            verify(collectionMock, never()).deleteContentDirectory(anyString(), anyString());
            verify(collectionMock, never()).deleteFile(anyString());
            verify(collectionMock, never()).save();
            throw e;
        }
    }

    @Test(expected = ConflictException.class)
    public void shouldThrowConflictExceptionOnCreateContentIfAlreadyPublished() throws Exception {
        String uri = "someURI";
        when(publishedContentMock.exists(uri))
                .thenReturn(true);
        try {
            collections.createContent(collectionMock, uri, sessionMock, null, null, false);
        } catch (ConflictException e) {
            verify(publishedContentMock, times(1)).exists(uri);
            verifyNoInteractions(zebedeeMock, collectionReaderWriterFactoryMock, collectionDescriptionMock,
                    collectionWriterMock, collectionMock);
            throw e;
        }
    }

    @Test(expected = ConflictException.class)
    public void shouldThrowConflictExceptionOnCreateContentIfAlreadyInCollection() throws Exception {
        String uri = "someURI";
        Collection blocker = mock(Collection.class);

        when(publishedContentMock.exists(uri))
                .thenReturn(false);
        when(zebedeeMock.checkForCollectionBlockingChange(collectionMock, uri))
                .thenReturn(Optional.of(blocker));
        when(zebedeeMock.checkForCollectionBlockingChange(uri))
                .thenReturn(Optional.of(blocker));
        when(blocker.getDescription())
                .thenReturn(collectionDescriptionMock);
        when(collectionDescriptionMock.getName())
                .thenReturn("Bob")
                .thenReturn("Steve");

        try {
            collections.createContent(collectionMock, uri, sessionMock, null, null, false);
        } catch (ConflictException e) {
            verify(publishedContentMock, times(1)).exists(uri);
            verify(zebedeeMock, times(1)).checkForCollectionBlockingChange(collectionMock, uri);
            verify(blocker, times(1)).getDescription();
            verify(collectionDescriptionMock, times(1)).getName();
            throw e;
        }
    }

    @Test
    public void shouldReturnEmptyOrphansListIfAllCollectionsValid() throws Exception {
        collectionsPath.resolve("c1").toFile().mkdir();
        collectionsPath.resolve("c1.json").toFile().createNewFile();

        collectionsPath.resolve("c2").toFile().mkdir();
        collectionsPath.resolve("c2.json").toFile().createNewFile();

        collectionsPath.resolve("c3").toFile().mkdir();
        collectionsPath.resolve("c3.json").toFile().createNewFile();

        assertTrue(collections.listOrphaned().isEmpty());
    }

    @Test
    public void shouldReturnExpectedOrphansList() throws Exception {
        collectionsPath.resolve("c1").toFile().mkdir();
        collectionsPath.resolve("c1.json").toFile().createNewFile();

        // create 2 collection dirs without the corresoinding json files.
        collectionsPath.resolve("c2").toFile().mkdir();
        collectionsPath.resolve("c3").toFile().mkdir();

        List<String> orphans = collections.listOrphaned();

        assertThat("incorrect number of orphans returned", orphans.size(), equalTo(2));
        assertThat("incorrect number of orphans returned", orphans, equalTo(Arrays.asList("c2","c3")));
    }

    @Test
    public void deleteContentShouldDeleteDataVizZip() throws IOException, ZebedeeException {
        Path colDir = collectionsPath.resolve("abc/inprogress/visualisations/dvc123");
        assertTrue(colDir.toFile().mkdirs());

        Path filePath = colDir.resolve("dvc123.zip");
        filePath.toFile().createNewFile();

        assertTrue(Files.exists(filePath));

        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);

        when(collectionMock.find(filePath.toString()))
                .thenReturn(collectionsPath.resolve(filePath));

        when(collectionMock.isInCollection(filePath.toString()))
                .thenReturn(true);

        when(collectionMock.deleteDataVisContent(sessionMock, filePath))
                .thenAnswer(i -> Files.deleteIfExists(filePath));

        boolean deleteSuccessful = collections.deleteContent(collectionMock, filePath.toString(), sessionMock);

        assertTrue(deleteSuccessful);
        assertThat(collectionsPath.resolve("abc/inprogress").toFile().list().length, equalTo(0));
        verify(collectionMock, times(1)).save();
        verify(collectionMock, never()).deleteContentDirectory(any(), eq(filePath.toString()));
        verify(collectionMock, never()).deleteFile(filePath.toString());
    }

    @Test
    public void shouldDeleteFolder() throws IOException, ZebedeeException {
        // populate the collection with some content.
        String uri = "col1/inprogress/aboutus";
        Path inprogress = collectionsPath.resolve(uri);
        assertTrue(inprogress.toFile().mkdirs());

        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);

        when(collectionMock.find(uri))
                .thenReturn(inprogress);

        when(collectionMock.isInCollection(uri))
                .thenReturn(true);

        when(collectionMock.deleteContentDirectory(TEST_EMAIL, uri))
                .thenReturn(true);

        boolean deleteSuccessful = collections.deleteContent(collectionMock, uri, sessionMock);

        assertTrue(deleteSuccessful);
        verify(collectionMock, times(1)).save();
        verify(collectionMock, never()).deleteDataVisContent(sessionMock, Paths.get(uri));
        verify(collectionMock, never()).deleteFile(uri);
    }

    @Test
    public void shouldDeleteSingleNonJsonFile() throws IOException, ZebedeeException {
        // populate the collection with some content.
        Path inprogress = collectionsPath.resolve("col1/inprogress/aboutus");
        assertTrue(inprogress.toFile().mkdirs());

        Path uri = inprogress.resolve("nondatajson");

        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);

        when(collectionMock.find(uri.toString()))
                .thenReturn(collectionsPath.resolve(uri));

        when(collectionMock.isInCollection(uri.toString()))
                .thenReturn(true);

        when(collectionMock.deleteFile(uri.toString()))
                .thenReturn(true);

        boolean deleteSuccessful = collections.deleteContent(collectionMock, uri.toString(), sessionMock);

        assertTrue(deleteSuccessful);
        verify(collectionMock, times(1)).save();
        verify(collectionMock, never()).deleteDataVisContent(sessionMock, uri);
        verify(collectionMock, never()).deleteContentDirectory(any(), eq(uri.toString()));
    }

    @Test
    public void shouldDeleteSingleJsonFile() throws IOException, ZebedeeException {
        // populate the collection with some content.
        Path inprogress = collectionsPath.resolve("col1/inprogress/aboutus");
        assertTrue(inprogress.toFile().mkdirs());

        Path uri = inprogress.resolve("data.json");
        assertTrue(uri.toFile().createNewFile());

        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);

        when(collectionMock.find(uri.toString()))
                .thenReturn(collectionsPath.resolve(uri));

        when(collectionMock.isInCollection(uri.toString()))
                .thenReturn(true);

        when(collectionMock.deleteFile(uri.toString()))
                .thenReturn(true);

        boolean deleteSuccessful = collections.deleteContent(collectionMock, uri.toString(), sessionMock);

        assertTrue(deleteSuccessful);
        verify(collectionMock, times(1)).save();
        verify(collectionMock, never()).deleteDataVisContent(sessionMock, uri);
        verify(collectionMock, never()).deleteContentDirectory(any(), eq(uri.toString()));
    }

    @Test
    public void shouldDeleteHomePage() throws IOException, ZebedeeException {
        String uri = "/";
        Path path = Paths.get(uri);

        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);

        when(collectionMock.find(uri.toString()))
                .thenReturn(path);

        when(collectionMock.isInCollection(uri))
                .thenReturn(true);

        when(collectionMock.deleteContentDirectory(TEST_EMAIL, uri))
                .thenReturn(true);

        boolean deleteSuccessful = collections.deleteContent(collectionMock, uri.toString(), sessionMock);

        assertTrue(deleteSuccessful);
        verify(collectionMock, times(1)).save();
        verify(collectionMock, never()).deleteDataVisContent(sessionMock, path);
        verify(collectionMock, never()).deleteFile(anyString());
    }

    @Test
    public void skipDatasetVersionsValidation_userKeyNull_shouldReturnFalse() throws IOException {
        assertFalse(collections.skipDatasetVersionsValidation(null, null, null));
    }

    @Test
    public void skipDatasetVersionsValidation_overrideKeyNull_shouldReturnFalse() throws IOException {
        assertFalse(collections.skipDatasetVersionsValidation(666L, null, null));
    }

    @Test
    public void skipDatasetVersionsValidation_sessionNull_shouldReturnFalse() throws IOException {
        Session expectedSession = null;

        when(permissionsServiceMock.canEdit(expectedSession))
                .thenReturn(false);

        assertFalse(collections.skipDatasetVersionsValidation(666L, 666L, expectedSession));
    }

    @Test
    public void skipDatasetVersionsValidation_userNotPublisher_shouldReturnFalse() throws IOException {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(false);

        assertFalse(collections.skipDatasetVersionsValidation(666L, 666L, sessionMock));
    }

    @Test
    public void skipDatasetVersionsValidation_incorrectKey_shouldReturnFalse() throws IOException {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);

        assertFalse(collections.skipDatasetVersionsValidation(123L, 666L, sessionMock));
    }

    @Test
    public void skipDatasetVersionsValidation_correctKey_shouldReturnTrue() throws IOException {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(true);

        long actual = 666;
        long user = 666;
        assertTrue(collections.skipDatasetVersionsValidation(user, actual, sessionMock));
    }

    @Test
    public void skipDatasetVersionsValidation_correctKeyInvalidPermssions_shouldReturnFalse() throws IOException {
        when(permissionsServiceMock.canEdit(sessionMock))
                .thenReturn(false);

        assertFalse(collections.skipDatasetVersionsValidation(666L, 666L, sessionMock));
    }

    @Test
    public void skipDatasetVersionsValidation_success() throws IOException {
        CollectionDescription description = new CollectionDescription();

        when(collectionMock.getDescription())
                .thenReturn(description);

        collections.skipDatasetVersionsValidation(collectionMock, sessionMock);

        assertThat(description.getEvents().size(), equalTo(1));

        Event event = description.getEvents().get(0);
        assertThat(event.getType(), equalTo(EventType.VERSION_VERIFICATION_BYPASSED));
    }

    @Test
    public void collectionsList_withApprovalInProgressOrError_emptyCollectionsList() {

        // Given an empty list of collections
        Collections.CollectionList collections = new Collections.CollectionList();

        // When the withApprovalInProgressOrError function is called
        final List<Collection> filteredCollections = collections.withApprovalInProgressOrError();

        // Then an empty collection is returned
        assertThat(filteredCollections, hasSize(0));
    }

    @Test
    public void collectionsList_withApprovalInProgressOrError_noInProgressCollections() {

        // Given a list of collections with none in approval state IN_PROGRESS or ERROR
        Collections.CollectionList collections = new Collections.CollectionList();
        Collection collection = getMockCollection(ApprovalStatus.NOT_STARTED);
        collections.add(collection);

        // When the withApprovalInProgressOrError function is called
        final List<Collection> filteredCollections = collections.withApprovalInProgressOrError();

        // Then an empty collection is returned
        assertThat(filteredCollections, hasSize(0));
    }

    @Test
    public void collectionsList_withApprovalInProgressOrError_inProgressCollection() {

        // Given a list of collections with one in approval state IN_PROGRESS
        Collections.CollectionList collections = new Collections.CollectionList();
        Collection collection = getMockCollection(ApprovalStatus.IN_PROGRESS);
        collections.add(collection);

        // When the withApprovalInProgressOrError function is called
        final List<Collection> filteredCollections = collections.withApprovalInProgressOrError();

        // Then the list returned contains the collection
        assertThat(filteredCollections, hasSize(1));
        assertThat(filteredCollections, contains(collection));
    }

    @Test
    public void collectionsList_withApprovalInProgressOrError_erroredCollection() {

        // Given a list of collections with one in approval state ERROR
        Collections.CollectionList collections = new Collections.CollectionList();
        Collection collection = getMockCollection(ApprovalStatus.ERROR);
        collections.add(collection);

        // When the withApprovalInProgressOrError function is called
        final List<Collection> filteredCollections = collections.withApprovalInProgressOrError();

        // Then the list returned contains the collection
        assertThat(filteredCollections, hasSize(1));
        assertThat(filteredCollections, contains(collection));
    }

    @Test
    public void collectionsList_withApprovalInProgressOrError_erroredAndInProgressCollections() {

        // Given a list of collections with collections in both ERROR and IN_PROGRESS approval states
        Collection inProgressCollection = getMockCollection(ApprovalStatus.IN_PROGRESS);
        Collection erroredCollection = getMockCollection(ApprovalStatus.ERROR);
        Collection notStartedCollection = getMockCollection(ApprovalStatus.NOT_STARTED);

        Collections.CollectionList collections = new Collections.CollectionList();
        collections.add(inProgressCollection);
        collections.add(notStartedCollection);
        collections.add(erroredCollection);

        // When the withApprovalInProgressOrError function is called
        final List<Collection> filteredCollections = collections.withApprovalInProgressOrError();

        // Then the list returned contains only the collections in ERROR and IN_PROGRESS approval states
        assertThat(filteredCollections, hasSize(2));
        assertThat(filteredCollections, contains(inProgressCollection, erroredCollection));
    }

    @Test
    public void collectionsList_withApprovalInProgressOrError_multipleCollections() {

        // Given a list of collections with multiple collections in both ERROR and IN_PROGRESS approval states
        Collection inProgressCollection1 = getMockCollection(ApprovalStatus.IN_PROGRESS);
        Collection inProgressCollection2 = getMockCollection(ApprovalStatus.IN_PROGRESS);
        Collection erroredCollection1 = getMockCollection(ApprovalStatus.ERROR);
        Collection erroredCollection2 = getMockCollection(ApprovalStatus.ERROR);
        Collection notStartedCollection1 = getMockCollection(ApprovalStatus.NOT_STARTED);
        Collection notStartedCollection2 = getMockCollection(ApprovalStatus.NOT_STARTED);

        Collections.CollectionList collections = new Collections.CollectionList();
        collections.add(inProgressCollection1);
        collections.add(inProgressCollection2);
        collections.add(notStartedCollection1);
        collections.add(notStartedCollection2);
        collections.add(erroredCollection1);
        collections.add(erroredCollection2);

        // When the withApprovalInProgressOrError function is called
        final List<Collection> filteredCollections = collections.withApprovalInProgressOrError();

        // Then the list returned contains only the collections in ERROR and IN_PROGRESS approval states
        assertThat(filteredCollections, hasSize(4));
        assertThat(filteredCollections, contains(
                inProgressCollection1,
                inProgressCollection2,
                erroredCollection1,
                erroredCollection2));
    }

    private Collection getMockCollection(ApprovalStatus approvalStatus) {
        Collection collection = mock(Collection.class);
        CollectionDescription description = new CollectionDescription();
        description.setApprovalStatus(approvalStatus);
        description.setType(CollectionType.scheduled);
        when(collection.getDescription()).thenReturn(description);
        return collection;
    }
}
