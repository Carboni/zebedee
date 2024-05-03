package com.github.onsdigital.zebedee.model;

import com.github.davidcarboni.fileupload.encrypted.EncryptedFileItemFactory;
import com.github.onsdigital.zebedee.Zebedee;
import com.github.onsdigital.zebedee.api.Root;
import com.github.onsdigital.zebedee.content.util.ContentUtil;
import com.github.onsdigital.zebedee.data.json.DirectoryListing;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.CollectionNotFoundException;
import com.github.onsdigital.zebedee.exceptions.ConflictException;
import com.github.onsdigital.zebedee.exceptions.DeleteContentRequestDeniedException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.exceptions.UnexpectedErrorException;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.json.ApprovalStatus;
import com.github.onsdigital.zebedee.json.CollectionDescription;
import com.github.onsdigital.zebedee.json.Event;
import com.github.onsdigital.zebedee.json.EventType;
import com.github.onsdigital.zebedee.logging.CMSLogEvent;
import com.github.onsdigital.zebedee.model.approval.ApprovalQueue;
import com.github.onsdigital.zebedee.model.approval.ApproveTask;
import com.github.onsdigital.zebedee.model.publishing.PostPublisher;
import com.github.onsdigital.zebedee.model.publishing.PublishNotification;
import com.github.onsdigital.zebedee.model.publishing.Publisher;
import com.github.onsdigital.zebedee.permissions.service.PermissionsService;
import com.github.onsdigital.zebedee.reader.CollectionReader;
import com.github.onsdigital.zebedee.reader.ContentReader;
import com.github.onsdigital.zebedee.reader.FileSystemContentReader;
import com.github.onsdigital.zebedee.session.model.Session;
import com.github.onsdigital.zebedee.util.ZebedeeCmsService;
import com.github.onsdigital.zebedee.util.versioning.VersionNotFoundException;
import com.github.onsdigital.zebedee.util.versioning.VersionsService;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.ProgressListener;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;

import static com.github.onsdigital.zebedee.configuration.CMSFeatureFlags.cmsFeatureFlags;
import static com.github.onsdigital.zebedee.configuration.Configuration.getUnauthorizedMessage;
import static com.github.onsdigital.zebedee.json.EventType.VERSION_VERIFICATION_BYPASSED;
import static com.github.onsdigital.zebedee.json.EventType.VERSION_VERIFICATION_FAILED;
import static com.github.onsdigital.zebedee.logging.CMSLogEvent.error;
import static com.github.onsdigital.zebedee.logging.CMSLogEvent.info;
import static com.github.onsdigital.zebedee.logging.CMSLogEvent.warn;
import static com.github.onsdigital.zebedee.model.Content.isDataVisualisationFile;
import static java.time.temporal.ChronoUnit.MINUTES;

public class Collections {

    private final Path path;
    private PermissionsService permissionsService;
    private Content published;
    private Supplier<Zebedee> zebedeeSupplier = () -> Root.zebedee;
    private ZebedeeCmsService zebedeeCmsService = ZebedeeCmsService.getInstance();
    private CollectionReaderWriterFactory collectionReaderWriterFactory;
    private Function<ApproveTask, Future<Boolean>> addTaskToQueue = ApprovalQueue::add;
    private BiConsumer<Collection, EventType> publishingNotificationConsumer = (c, e) -> new PublishNotification(c).sendNotification(e);
    private Function<Path, ContentReader> contentReaderFactory = FileSystemContentReader::new;
    private Comparator<String> strComparator = Comparator.comparing(String::toString);
    private VersionsService versionsService;

    public Collections(Path path,
                       PermissionsService permissionsService,
                       VersionsService versionsService,
                       Content published) {
        this.path = path;
        this.permissionsService = permissionsService;
        this.versionsService = versionsService;
        this.published = published;
        this.collectionReaderWriterFactory = new CollectionReaderWriterFactory();
    }

    /**
     * Get the collections directory path.
     *
     * @return the collections directory path.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Populate a list of files / folders for a given path.
     *
     * @param path
     * @return
     * @throws IOException
     */
    public static DirectoryListing listDirectory(Path path) throws IOException {
        DirectoryListing listing = new DirectoryListing();
        try (DirectoryStream<Path> stream = Files
                .newDirectoryStream(path)) {
            for (Path directory : stream) {
                if (Files.isDirectory(directory)) {
                    listing.getFolders().put(directory.getFileName().toString(),
                            directory.toString());
                } else {
                    listing.getFiles().put(directory.getFileName().toString(),
                            directory.toString());
                }
            }
        }
        return listing;
    }

    /**
     * Trim the guid part of the collection ID to infer the name
     *
     * @return
     */
    private static String getCollectionNameFromId(String id) {
        try {
            int guidLength = 65; // length of GUID plus the hyphen.
            return id.substring(0, id.length() - guidLength);
        } catch (StringIndexOutOfBoundsException e) {
            return id;
        }
    }

    public static void removeEmptyCollectionDirectories(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            path = path.getParent();
        }
        if (isEmpty(path)) {
            Path temp = path;
            while (!isCollectionRoot(temp.getFileName())) {
                if (isEmpty(temp)) {
                    Files.deleteIfExists(temp);
                }
                temp = temp.getParent();
            }
        }
    }

    private static boolean isCollectionRoot(Path path) {
        return Collection.IN_PROGRESS.equals(path.toString())
                || Collection.REVIEWED.equals(path.toString())
                || Collection.COMPLETE.equals(path.toString());
    }

    private static boolean isEmpty(Path path) {
        File[] files = path.toFile().listFiles();

        if (files == null)
            return true;

        return Arrays.asList(files).isEmpty();
    }

    /**
     * Mark a file in a collection as 'complete'
     *
     * @param collection
     * @param uri
     * @param session
     * @param recursive
     * @throws IOException
     * @throws NotFoundException
     * @throws UnauthorizedException
     * @throws BadRequestException
     */
    public void complete(
            Collection collection, String uri,
            Session session,
            boolean recursive
    ) throws IOException, ZebedeeException {

        // Check the collection
        if (collection == null) {
            throw new BadRequestException("Please specify a valid collection.");
        }

        // Check authorisation
        if (!permissionsService.canEdit(session)) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        // Locate the path:
        Path path = collection.getInProgress().get(uri);
        if (path == null) {
            throw new NotFoundException("URI not in progress.");
        }

        // Check we're requesting a file:
        if (java.nio.file.Files.isDirectory(path)) {
            throw new BadRequestException("URI does not represent a file.");
        }

        // Attempt to complete:
        if (collection.complete(session, uri, recursive)) {
            removeEmptyCollectionDirectories(path);
            collection.save();
        } else {
            throw new BadRequestException("URI was not completed.");
        }
    }

    /**
     * @return A list of all {@link Collection}s.
     * @throws IOException If a filesystem error occurs.
     */
    public CollectionList list() throws IOException {

        CollectionList result = new CollectionList();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    try {
                        result.add(new Collection(path, zebedeeSupplier.get()));
                    } catch (CollectionNotFoundException e) {
                        error().data("collection_path", path.toString())
                                .logException(e, "failed to deserialise collection");
                    }
                }
            }
        }

        return result;
    }

    /**
     * Returns a {@link List} of {@link Collection} matching the provided {@link Predicate} filter criteria.
     *
     * @param f the filter to apply
     * @return
     * @throws IOException problem listing the collections
     */
    public List<Collection> filterBy(Predicate<Collection> f) throws IOException {
        return list().stream().filter(f).collect(Collectors.toList());
    }

    public List<String> listOrphaned() throws IOException {
        List<String> orphans = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path collectionPath : stream) {
                if (Files.isDirectory(collectionPath)) {
                    String collectionDirName = collectionPath.toFile().getName();
                    if (!path.resolve(collectionDirName + ".json").toFile().exists()) {
                        orphans.add(collectionDirName);
                    }
                }
            }
        }
        if (!orphans.isEmpty()) {
            orphans.sort(strComparator);
            warn().data("orphaned_collections", orphans)
                    .log("orphaned collections found. It's recommended you investiagte and fix these. Encryption keys " +
                            "for these collections will not be removed from users");
        }
        return orphans;
    }

    public Map<String, Collection> mapByID() throws IOException {
        return list().stream().collect(Collectors.toMap(
                collection -> collection.getDescription().getId(), collection -> collection));
    }

    /**
     * Get the collection with the given collection ID.
     *
     * @param collectionId
     * @return
     * @throws IOException
     */
    public Collection getCollection(String collectionId)
            throws IOException {
        try {
            String collectionName = getCollectionNameFromId(collectionId);
            Collection collection = getCollectionByName(collectionName);
            return collection;
        } catch (IOException | CollectionNotFoundException e) {
            return list().getCollection(collectionId);
        }
    }

    public Collection getCollectionByName(String collectionName) throws IOException, CollectionNotFoundException {
        return new Collection(this.path.resolve(collectionName), zebedeeSupplier.get());
    }

    /**
     * Approve the given collection.
     * <p>
     * Uses the environment variable use_beta_publisher to choose publisher
     *
     * @param collection
     * @param session
     * @param userOverrideKey
     * @return
     * @throws IOException
     * @throws UnauthorizedException
     * @throws BadRequestException
     * @throws ConflictException
     */
    public Future<Boolean> approve(Collection collection, Session session, Long userOverrideKey)
            throws IOException, ZebedeeException {

        // Collection exists
        if (collection == null) {
            error().log("approve collection: collection null check failed");
            throw new BadRequestException("Please provide a valid collection.");
        }

        // User has permission
        if (session == null || !permissionsService.canEdit(session)) {
            error().log("approve collection: user permission check failed");
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        collection.getDescription().addEvent(new Event(new Date(), EventType.APPROVE_SUBMITTED, session.getEmail()));
        String collectionId = collection.getDescription().getId();

        boolean isDatasetImportEnabled = cmsFeatureFlags().isEnableDatasetImport();

        // Everything is completed
        if (!collection.isAllContentReviewed(isDatasetImportEnabled)) {
            collection.getDescription().addEvent(new Event(new Date(), EventType.APPROVE_SUBMITTED, session.getEmail()));

            error().data("inProgressEmpty", collection.inProgressUris().isEmpty())
                    .data("completeEmpty", collection.completeUris().isEmpty())
                    .data("collectionId", collectionId)
                    .log("approve collection: can approve check failure");
            throw new ConflictException(
                    "This collection can't be approved because it's not empty");
        }

        CollectionReader collectionReader = collectionReaderWriterFactory.getReader(zebedeeSupplier.get(), collection, session);
        CollectionWriter collectionWriter = collectionReaderWriterFactory.getWriter(zebedeeSupplier.get(), collection, session);
        ContentReader publishedReader = contentReaderFactory.apply(this.published.getPath());

        if (skipDatasetVersionsValidation(userOverrideKey, getDatasetVersionVerificationOverrideKey(), session)) {
            skipDatasetVersionsValidation(collection, session);
        } else {
            verifyDatasetVersions(collection, collectionReader, session);
        }

        info().data("collectionId", collectionId)
                .log("approve collection: setting collection status to approved");

        collection.getDescription().setApprovalStatus(ApprovalStatus.IN_PROGRESS);

        info().data("collectionId", collectionId)
                .log("approve collection: saving collection");
        collection.save();

        info().data("collectionId", collectionId).log("approve collection: adding approval task to queue");

        Future<Boolean> future = null;
        try {
            future = addTaskToQueue.apply(
                    new ApproveTask(collection, session, collectionReader, collectionWriter, publishedReader,
                            zebedeeSupplier.get().getDataIndex(), zebedeeSupplier.get().getSlackNotifier()));
        } catch (Exception e) {
            error().data("collectionId", collectionId).logException(e, "approve collection: submit collection approval task failure");
        }

        info().data("collectionId", collectionId).log("approve collection: saving collection history event");

        info().data("collectionId", collectionId).log("approve collection: API approve step compeleted successfully");
        return future;
    }

    /**
     * Unlock the given collection.
     *
     * @param collection
     * @param session
     * @return
     * @throws IOException
     * @throws UnauthorizedException
     * @throws BadRequestException
     * @throws ConflictException
     */
    public boolean unlock(Collection collection, Session session)
            throws IOException, ZebedeeException {

        // Collection exists
        if (collection == null) {
            throw new BadRequestException("Please provide a valid collection.");
        }

        // User has permission
        if (session == null || !permissionsService.canEdit(session)) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        // don't do anything if the approval status is not complete.
        if (collection.getDescription().getApprovalStatus() != ApprovalStatus.COMPLETE) {
            return true;
        }

        // Go ahead
        collection.getDescription().setApprovalStatus(ApprovalStatus.NOT_STARTED);
        collection.getDescription().addEvent(new Event(new Date(), EventType.UNLOCKED, session.getEmail()));

        publishingNotificationConsumer.accept(collection, EventType.UNLOCKED);
        return collection.save();
    }

    /**
     * Publish the files
     *
     * @param collection       the collection to publish
     * @param session          a session with editor priviledges
     * @param skipVerification
     * @return success
     * @throws IOException
     * @throws UnauthorizedException
     * @throws BadRequestException
     * @throws ConflictException     - If there
     */
    public boolean publish(Collection collection, Session session, boolean breakBeforePublish, boolean skipVerification)
            throws IOException, UnauthorizedException, BadRequestException,
            ConflictException, NotFoundException {

        // Collection exists
        if (collection == null) {
            throw new BadRequestException("Please provide a valid collection.");
        }

        // User has permission
        if (session == null || !permissionsService.canEdit(session)) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        // Check approval status
        if (collection.getDescription().getApprovalStatus() != ApprovalStatus.COMPLETE) {
            throw new ConflictException("This collection cannot be published because it is not approved");
        }

        // Break before transfer allows us to run tests on the prepublish-hook without messing up the content
        if (breakBeforePublish) {
            info().log("Breaking before publish");
            return true;
        }

        ZebedeeCollectionReader collectionReader = new ZebedeeCollectionReader(zebedeeSupplier.get(), collection, session);
        long publishStart = System.currentTimeMillis();
        boolean publishComplete = Publisher.publish(collection, session.getEmail(), collectionReader);

        if (publishComplete) {
            long onPublishCompleteStart = System.currentTimeMillis();

            new PublishNotification(collection).sendNotification(EventType.PUBLISHED);

            PostPublisher.postPublish(zebedeeSupplier.get(), collection, skipVerification, collectionReader);

            info().data("collectionId", collection.getDescription().getId())
                    .data("timeTaken", (System.currentTimeMillis() - onPublishCompleteStart))
                    .log("collection post publish process completed");

            info().data("collectionId", collection.getDescription().getId())
                    .data("timeTaken", (System.currentTimeMillis() - publishStart))
                    .log("collection publish complete");
        }

        return publishComplete;
    }

    public DirectoryListing listDirectory(
            Collection collection, String uri,
            Session session
    ) throws NotFoundException, UnauthorizedException,
            IOException, BadRequestException {

        if (collection == null) {
            throw new BadRequestException("Please specify a valid collection.");
        }

        // Check view permissionsServiceImpl
        if (!permissionsService.canView(session,
                collection.getDescription().getId())) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        // Locate the path:
        Path path = collection.find(uri);
        if (path == null) {
            throw new NotFoundException("URI not found in collection: " + uri);
        }

        // Check we're requesting a directory:
        if (!Files.isDirectory(path)) {
            throw new BadRequestException(
                    "Please provide a URI to a directory: " + uri);
        }

        return listDirectory(path);
    }

    /**
     * List the given directory of a collection including the files that have already been published.
     *
     * @param collection the collection to overlay on master content
     * @param uri        the uri of the directory
     * @param session    the session (used to determine user permissionsServiceImpl)
     * @return a DirectoryListing object with system content overlaying master content
     * @throws NotFoundException
     * @throws UnauthorizedException
     * @throws IOException
     * @throws BadRequestException
     */
    public DirectoryListing listDirectoryOverlayed(
            Collection collection, String uri,
            Session session
    ) throws NotFoundException, UnauthorizedException,
            IOException, BadRequestException {

        DirectoryListing listing = listDirectory(collection, uri, session);

        Path publishedPath = this.published.get(uri);
        DirectoryListing publishedListing = listDirectory(publishedPath);

        listing.getFiles().putAll(publishedListing.getFiles());
        listing.getFolders().putAll(publishedListing.getFolders());

        return listing;
    }

    public void delete(Collection collection, Session session)
            throws IOException, ZebedeeException {

        // Collection exists
        if (collection == null) {
            throw new BadRequestException("Please specify a valid collection");
        }

        // User has permission
        if (!permissionsService.canEdit(session)) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        // Collection is empty
        if (!collection.isEmpty()) {
            throw new BadRequestException("The collection is not empty.");
        }

        // Go ahead
        collection.delete();
    }

    /**
     * Create new content if it does not already exist.
     *
     * @param collection
     * @param uri
     * @param session
     * @param request
     * @param requestBody
     * @throws ConflictException
     * @throws NotFoundException
     * @throws BadRequestException
     * @throws UnauthorizedException
     * @throws IOException
     */
    public void createContent(
            Collection collection, String uri, Session session, HttpServletRequest request,
            InputStream requestBody, boolean validateJson
    ) throws ZebedeeException, IOException,
            FileUploadException {

        if (this.published.exists(uri)) {
            throw new ConflictException("This URI already exists");
        }

        Optional<Collection> blockingCollection = zebedeeSupplier.get().checkForCollectionBlockingChange(collection, uri);
        if (blockingCollection.isPresent()) {
            Collection blocker = blockingCollection.get();
            info().data("saveOrEditConflict", collection.generateCollectionSaveConflictMap(blocker, uri))
                    .data("user", session.getEmail())
                    .log("Cannot create content as it's existing in another collection.");

            throw new ConflictException("This URI exists in another collection", blocker.getDescription().getName());
        }

        try {
            zebedeeSupplier.get().checkAllCollectionsForDeleteMarker(uri);
        } catch (DeleteContentRequestDeniedException ex) {
            throw new ConflictException("This URI is marked for deletion in another collection", ex.getCollectionName());
        }

        writeContent(collection, uri, session, request, requestBody, false, validateJson);
    }

    public void writeContent(
            Collection collection, String uri,
            Session session, HttpServletRequest request, InputStream requestBody, Boolean recursive,
            boolean validateJson
    ) throws IOException, ZebedeeException, FileUploadException {

        CollectionWriter collectionWriter = collectionReaderWriterFactory.getWriter(zebedeeSupplier.get(), collection, session);

        info().data("collectionId", collection.getDescription().getId()).data("path", uri).data("user", session.getEmail()).log("Attempting to write content.");

        if (collection.getDescription().getApprovalStatus() == ApprovalStatus.COMPLETE) {
            throw new BadRequestException("This collection has been approved and cannot be saved to.");
        }

        // Requested path
        if (StringUtils.isBlank(uri)) {
            throw new BadRequestException("Please provide a URI");
        }

        // Find the file if it exists
        Path path = collection.find(uri);

        // Check we're writing a file:
        if (path != null && Files.isDirectory(path)) {
            throw new BadRequestException("Please provide a URI to a file");
        }

        // Create / edit
        if (path == null) {
            // create the file
            if (!collection.create(session, uri)) {
                // file may be being edited in a different collection
                Optional<Collection> otherCollection = zebedeeSupplier.get().checkForCollectionBlockingChange(uri);
                if (otherCollection.isPresent()) {
                    throw new ConflictException(
                            "This URI is being edited in another collection", otherCollection.get().getDescription().getName());
                }
                throw new ConflictException(
                        "It could be this URI is being edited in another collection");
            }
        } else {
            // edit the file
            boolean result = collection.edit(session, uri, collectionWriter, recursive);
            if (!result) {
                // file may be being edited in a different collection
                Optional<Collection> otherCollection = zebedeeSupplier.get().checkForCollectionBlockingChange(uri);
                if (otherCollection.isPresent()) {
                    throw new ConflictException(
                            "This URI is being edited in another collection", otherCollection.get().getDescription().getName());
                }
                throw new ConflictException(
                        "It could be this URI is being edited in another collection");
            }
        }

        collection.save();
        info().data("collectionId", collection.getDescription().getId())
                .data("path", uri).data("user", session.getEmail())
                .log("content save successful.");

        path = collection.getInProgressPath(uri);
        if (!Files.exists(path)) {
            throw new NotFoundException(
                    "Somehow we weren't able to edit the requested URI");
        }

        // Detect whether this is a multipart request
        if (ServletFileUpload.isMultipartContent(request)) {
            postDataFile(request, uri, collectionWriter);

        } else {
            if (validateJson) {
                try (InputStream inputStream = validateJsonStream(requestBody)) {
                    collectionWriter.getInProgress().write(inputStream, uri);
                }
            } else {
                collectionWriter.getInProgress().write(requestBody, uri);
            }
        }
    }

    /**
     * Take an input stream that contains json content and ensure its valid.
     *
     * @param inputStream
     * @return
     */
    public InputStream validateJsonStream(InputStream inputStream) throws BadRequestException {
        try {
            byte[] bytes = IOUtils.toByteArray(inputStream);

            try (ByteArrayInputStream validationInputStream = new ByteArrayInputStream(bytes)) {
                ContentUtil.deserialiseContent(validationInputStream);
            }

            return new ByteArrayInputStream(bytes);
        } catch (Exception e) {
            throw new BadRequestException("Validation of page content failed. Please try again");
        }
    }

    public boolean deleteContent(
            Collection collection, String uri,
            Session session
    ) throws IOException, ZebedeeException {

        // Collection (null check before authorisation check)
        if (collection == null) {
            throw new BadRequestException("Please specify a collection");
        }

        // Authorisation
        if (session == null || !permissionsService.canEdit(session)) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        // Requested path
        if (StringUtils.isBlank(uri)) {
            throw new BadRequestException("Please provide a URI");
        }

        // Find the file if it exists
        Path contentTargetPath = collection.find(uri);

        // Check the user has access to the given file
        if (contentTargetPath == null || !collection.isInCollection(uri)) {
            throw new NotFoundException(
                    "This URI cannot be found in the collection");
        }

        boolean deleted = deleteContentFromCollection(collection, session, contentTargetPath, uri);
        collection.save();

        if (deleted) {
            removeEmptyCollectionDirectories(contentTargetPath);
        }
        return deleted;
    }

    boolean deleteContentFromCollection(Collection collection, Session session, Path contentTargetPath, String uri) throws IOException {
        boolean deleted;

        CMSLogEvent event = info().collectionID(collection).uri(contentTargetPath);

        if (isDataVisualisationFile(contentTargetPath)) {
            event.log("deleting data viz zip from collection content");
            deleted = collection.deleteDataVisContent(session, Paths.get(uri));
        } else if (Files.isDirectory(contentTargetPath)) {
            event.log("deleting directory from collection content");
            deleted = collection.deleteContentDirectory(session.getEmail(), uri);
        } else {
            event.log("deleting single file from collection content");
            deleted = collection.deleteFile(uri);
        }

        return deleted;
    }

    /**
     * Save uploaded files.
     *
     * @param request
     * @param uri
     * @param collectionWriter
     * @throws FileUploadException
     * @throws IOException
     */
    private void postDataFile(
            HttpServletRequest request, String uri, CollectionWriter collectionWriter)
            throws FileUploadException, IOException {

        ServletFileUpload upload = getServletFileUpload();

        try {
            for (FileItem item : upload.parseRequest(request)) {
                try (InputStream inputStream = item.getInputStream()) {
                    collectionWriter.getInProgress().write(inputStream, uri);
                }
            }
        } catch (Exception e) {
            throw new IOException("Error processing uploaded file", e);
        }
    }

    /**
     * get a file upload object with progress listener
     *
     * @return an upload object
     */
    public ServletFileUpload getServletFileUpload() {
        // Set up the objects that do all the heavy lifting
        EncryptedFileItemFactory factory = new EncryptedFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);

        ProgressListener progressListener = getProgressListener();
        upload.setProgressListener(progressListener);
        return upload;
    }

    /**
     * get a progress listener
     *
     * @return a ProgressListener object
     */
    private ProgressListener getProgressListener() {
        // Set up a progress listener that we can use to power a progress bar
        return new ProgressListener() {
            private long megaBytes = -1;

            @Override
            public void update(long pBytesRead, long pContentLength, int pItems) {
                long mBytes = pBytesRead / 1000000;
                if (megaBytes == mBytes) {
                    return;
                }
                megaBytes = mBytes;
            }
        };
    }

    /**
     * Move or rename content within a collection
     *
     * @param session
     * @param collection
     * @param uri
     * @param newUri
     * @throws BadRequestException
     * @throws IOException
     * @throws UnauthorizedException
     */
    public void moveContent(Session session, Collection collection, String uri, String newUri) throws ZebedeeException, IOException {

        if (collection == null) {
            throw new BadRequestException("Please specify a collection");
        }

        // Authorisation
        if (session == null || !permissionsService.canEdit(session)) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        if (StringUtils.isBlank(uri)) {
            throw new BadRequestException("Please provide a URI");
        }

        if (StringUtils.isBlank(newUri)) {
            throw new BadRequestException("Please provide a new URI");
        }

        if (this.published.exists(uri)) {
            throw new BadRequestException("You cannot move or rename a file that is already published.");
        }

        collection.moveContent(session, uri, newUri);
        collection.save();
    }

    public void renameContent(Session session, Collection collection, String uri, String toUri) throws IOException,
            ZebedeeException {

        if (collection == null) {
            throw new BadRequestException("Please specify a collection");
        }

        // Authorisation
        if (session == null || !permissionsService.canEdit(session)) {
            throw new UnauthorizedException(getUnauthorizedMessage(session));
        }

        if (StringUtils.isBlank(uri)) {
            throw new BadRequestException("Please provide a URI");
        }

        if (StringUtils.isBlank(toUri)) {
            throw new BadRequestException("Please provide a new URI");
        }

        if (this.published.exists(uri)) {
            throw new BadRequestException("You cannot move or rename a file that is already published.");
        }

        collection.renameContent(session.getEmail(), uri, toUri);
        collection.save();
    }

    /**
     * Represents the list of all collections currently in the system. This adds
     * a couple of utility methods to {@link ArrayList}.
     */
    public static class CollectionList extends ArrayList<Collection> {

        /**
         * Retrieves a collection with the given id.
         *
         * @param id The id to look for.
         * @return If a {@link Collection} matching the given name exists,
         * (according to {@link PathUtils#toFilename(String)}) the
         * collection. Otherwise null.
         */
        public Collection getCollection(String id) {
            Collection result = null;

            if (StringUtils.isNotBlank(id)) {
                for (Collection collection : this) {
                    if (StringUtils.equalsIgnoreCase(collection.getDescription().getId(), id)) {
                        result = collection;
                        break;
                    }
                }
            }

            return result;
        }

        /**
         * Retrieves a collection with the given name.
         *
         * @param name The name to look for.
         * @return If a {@link Collection} matching the given name exists,
         * (according to {@link PathUtils#toFilename(String)}) the
         * collection. Otherwise null.
         */
        public Collection getCollectionByName(String name) {
            Collection result = null;

            if (StringUtils.isNotBlank(name)) {

                String filename = PathUtils.toFilename(name);
                for (Collection collection : this) {
                    String collectionFilename = collection.getPath().getFileName()
                            .toString();
                    if (StringUtils.equalsIgnoreCase(collectionFilename,
                            filename)) {
                        result = collection;
                        break;
                    }
                }
            }

            return result;
        }

        /**
         * Determines whether a collection with the given name exists.
         *
         * @param name The name to check for.
         * @return If {@link #getCollection(String)} returns non-null, true.
         */
        public boolean hasCollection(String name) {
            return getCollectionByName(name) != null;
        }

        public List<Collection> withApprovalInProgressOrError() {
            return this.stream()
                    .filter(c -> c.getDescription().getApprovalStatus() == ApprovalStatus.IN_PROGRESS
                            || c.getDescription().getApprovalStatus() == ApprovalStatus.ERROR)
                    .collect(Collectors.toList());
        }
    }

    public void verifyDatasetVersions(Collection collection, CollectionReader collectionReader, Session session) throws ZebedeeException, IOException {
        if (!cmsFeatureFlags().isDatasetVersionVerificationEnabled()) {
            info().collectionID(collection).log("skipping dataset version verification feature not enabled");
            return;
        }

        info().collectionID(collection).log("feature enabled verifying collection dataset versions");

        try {
            versionsService.verifyCollectionDatasets(zebedeeCmsService.getZebedeeReader(), collection, collectionReader, session);
        } catch (VersionNotFoundException ex) {
            CollectionDescription description = collection.getDescription();
            description.addEvent(new Event(new Date(), VERSION_VERIFICATION_FAILED, session.getEmail()));

            error().collectionID(collection)
                    .user(session)
                    .exception(ex)
                    .reason("error verifying dataset(s), version(s) not found in either collection or published content")
                    .log("collection approval denied");
            throw new UnexpectedErrorException(ex.getMessage(), 409);
        } finally {
            collection.save();
        }
    }

    /**
     * Allow the verify datasets process to skipped by a publisher user by providing a valid key - temp until defect is
     * fixed properly.
     */
    public boolean skipDatasetVersionsValidation(Long userKey, Long overrideKey, Session session) throws IOException {
        if (userKey == null) {
            info().reason("no user overrideKey provided")
                    .log("dataset version validation not bypassed");
            return false;
        }

        if (overrideKey == null) {
            info().reason("expected overrideKey not provided")
                    .log("dataset version validation not bypassed");
            return false;
        }

        if (!permissionsService.canEdit(session)) {
            info().user(session)
                    .reason("edit permissions required")
                    .log("dataset version validation not bypassed");
            return false;
        }

        if (!overrideKey.equals(userKey)) {
            info().user(session)
                    .reason("user overrideKey incorrect")
                    .log("dataset version validation not bypassed");
            return false;
        }

        info().user(session)
                .reason("correct user permissions and overrideKey provided")
                .log("bypassing dataset version validation ");
        return true;
    }

    void skipDatasetVersionsValidation(Collection collection, Session session) throws IOException {
        try {
            CollectionDescription description = collection.getDescription();
            description.addEvent(new Event(new Date(), VERSION_VERIFICATION_BYPASSED, session.getEmail()));

            warn().collectionID(collection)
                    .user(session)
                    .log("warning valid dataset versions validation override key provided bypassing validation");
        } finally {
            collection.save();
        }
    }

    private Long getDatasetVersionVerificationOverrideKey() {
        Instant now = Instant.now();
        Instant midnight = now.plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        return now.until(midnight, MINUTES);
    }

    public String getCollectionId(HttpServletRequest request) {
        com.github.davidcarboni.restolino.helpers.Path path = com.github.davidcarboni.restolino.helpers.Path.newInstance(request);
        List<String> segments = path.segments();

        String collectionId = "";
        if (segments.size() > 1) {
            collectionId = segments.get(1);
        }
        return collectionId;
    }

}
