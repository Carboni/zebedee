package com.github.onsdigital.zebedee.reader.api;

import com.github.onsdigital.zebedee.content.base.Content;
import com.github.onsdigital.zebedee.content.dynamic.browse.ContentNode;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.reader.Resource;
import com.github.onsdigital.zebedee.reader.ZebedeeReader;
import com.github.onsdigital.zebedee.reader.data.filter.DataFilter;
import com.github.onsdigital.zebedee.reader.util.AuthorisationHandler;
import com.github.onsdigital.zebedee.util.URIUtils;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by bren on 31/07/15.
 * <p>
 * This class checks to see if Zebedee Cms is running to authorize collection views, if not serves published content
 */
public class ReadRequestHandler {

    /**
     * Authorisation handler is used to check permission on collection reads.
     * If Zebedee Reader is running standalone, no authorisation handler registered, thus no collection reads are allowed
     */
    private static AuthorisationHandler authorisationHandler;


    /**
     * Finds requested content , if a collection is required handles authorisation
     *
     * @param request
     * @param dataFilter
     * @return Content
     * @throws ZebedeeException
     * @throws IOException
     */
    public Content findContent(HttpServletRequest request, DataFilter dataFilter) throws ZebedeeException, IOException {
        String uri = extractUri(request);
        String collectionId = getCollectionId(request);
        if (collectionId != null) {
            authorise(request, collectionId);
            try {
                return ZebedeeReader.getInstance().getCollectionContent(collectionId, uri, dataFilter);
            } catch (NotFoundException e) {
                System.out.println("Could not found "+ uri +  " under collection "  + collectionId+  " , trying published content");
            }
        }

        return ZebedeeReader.getInstance().getPublishedContent(uri, dataFilter);

    }

    /**
     * Finds requested resource , if a collection resource is required handles authorisation
     *
     * @param request
     * @return
     * @throws ZebedeeException
     * @throws IOException
     */
    public Resource findResource(HttpServletRequest request) throws ZebedeeException, IOException {
        String uri = extractUri(request);
        String collectionId = getCollectionId(request);
        if (collectionId != null) {
            authorise(request, collectionId);
            try {
                return ZebedeeReader.getInstance().getCollectionResource(collectionId, uri);
            } catch (NotFoundException e) {
                System.out.println("Could not found " + uri + " under collection "+ collectionId+  ", trying published content");
            }
        }
        return ZebedeeReader.getInstance().getPublishedResource(uri);

    }

    public Set<ContentNode> listChildren(HttpServletRequest request) throws ZebedeeException, IOException {
        Set<ContentNode> children = new HashSet<>();
        String uri = extractUri(request);
        String collectionId = getCollectionId(request);
        if (collectionId != null) {
            authorise(request, collectionId);
        }

        try {
            children.addAll(ZebedeeReader.getInstance().getPublishedChildren(uri));
        } catch (NotFoundException n) {
            System.out.println("No children to list under published content with uri:" + uri);
        }
        //Collection uris overwrites published uris
        children.addAll(ZebedeeReader.getInstance().getCollectionChildren(collectionId, uri));
        return children;
    }

    private void authorise(HttpServletRequest request, String collectionId) throws UnauthorizedException, IOException, NotFoundException {
        if (authorisationHandler == null) {
            throw new UnauthorizedException("Collection reads are not available");
        }
        authorisationHandler.authorise(request, collectionId);
    }


    private String getCollectionId(HttpServletRequest request) {
        return URIUtils.getPathSegment(request.getRequestURI(), 2);
    }


    public String extractUri(HttpServletRequest request) throws BadRequestException {
        String uri = request.getParameter("uri");
        if (StringUtils.isEmpty(uri)) {
            throw new BadRequestException("Please specify uri");
        }
        return uri;
    }

    /**
     * set authorisation handler
     */
    public static void setAuthorisationHandler(AuthorisationHandler handler) {
        authorisationHandler = handler;
    }


}
