package com.github.onsdigital.zebedee.model;

import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.reader.ContentReader;
import com.github.onsdigital.zebedee.reader.Resource;
import com.github.onsdigital.zebedee.util.EncryptionUtils;
import org.apache.commons.io.IOUtils;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A content reader that handles encrypted files.
 */
public class CollectionContentReader extends ContentReader {

    private Collection collection;
    private SecretKey key;

    public CollectionContentReader(Collection collection, SecretKey key, Path rootFolder) throws UnauthorizedException, IOException {
        super(rootFolder);
        this.collection = collection;
        this.key = key;
    }

    @Override
    protected long calculateContentLength(Path path) throws IOException {
        if (collection.description.isEncrypted) {
            InputStream inputStream = EncryptionUtils.encryptionInputStream(path, key);
            return IOUtils.copy(inputStream, new ByteArrayOutputStream());
        } else {
            return super.calculateContentLength(path);
        }
    }

    @Override
    protected Resource buildResource(Path path) throws IOException {
        Resource resource = new Resource();
        resource.setName(path.getFileName().toString());
        resource.setMimeType(determineMimeType(path));
        // have to read the stream to determine length when content is encrypted.
        resource.setUri(toRelativeUri(path));
        resource.setData(getInputStream(path));
        return resource;
    }

    private InputStream getInputStream(Path path) throws IOException {
        InputStream inputStream;

        if (collection.description.isEncrypted) {
            inputStream = EncryptionUtils.encryptionInputStream(path, key);
        } else {
            inputStream = Files.newInputStream(path);
        }
        return inputStream;
    }
}
