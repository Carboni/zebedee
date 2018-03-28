package com.github.onsdigital.zebedee.model.publishing.scheduled.task;

import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.model.ZebedeeCollectionReader;
import com.github.onsdigital.zebedee.model.publishing.Publisher;

import java.util.concurrent.Callable;

import static com.github.onsdigital.zebedee.logging.ZebedeeLogBuilder.logInfo;

/**
 * A wrapper around the publish process of a single collection, allowing it to be executed on its own thread.
 */
public class PublishCollectionTask implements Callable<Boolean> {

    private static String publisherSystemEmail = "System";
    protected boolean published;
    private Collection collection;
    private ZebedeeCollectionReader collectionReader;
    private String encryptionPassword;
    private Publisher publisher;

    /**
     * Create a new task for a collection to be published.
     *  @param collection         - The collection to publish.
     * @param collectionReader   - The collection reader to read collection content.
     * @param encryptionPassword
     * @param publisher
     */
    public PublishCollectionTask(Collection collection,
                                 ZebedeeCollectionReader collectionReader,
                                 String encryptionPassword,
                                 Publisher publisher) {
        this.collection = collection;
        this.collectionReader = collectionReader;
        this.encryptionPassword = encryptionPassword;
        this.publisher = publisher;
    }

    /**
     * Publish the collection.
     * @return
     * @throws Exception
     */
    @Override
    public Boolean call() throws Exception {

        logInfo("PUBLISH: Running collection publish task").collectionName(collection).log();

        return publisher.DoPublish(collection,
                collectionReader,
                encryptionPassword,
                publisherSystemEmail);
    }

    /**
     * Return true if the publish was a success.
     * @return
     */
    public boolean isPublished() {
        return published;
    }

    /**
     * Get the collection associated with this task.
     * @return
     */
    public Collection getCollection() {
        return collection;
    }

    /**
     * Get the collection reader associated with this task.
     * @return
     */
    public ZebedeeCollectionReader getCollectionReader() {
        return collectionReader;
    }
}
