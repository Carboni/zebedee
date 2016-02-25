package com.github.onsdigital.zebedee.api;

import com.github.davidcarboni.restolino.framework.Api;
import com.github.onsdigital.zebedee.audit.Audit;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.ConflictException;
import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.json.Session;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.POST;
import java.io.IOException;

@Api
public class Unlock {

    /**
     * Unlock a collection after it has been approved.
     *
     * @param request
     * @param response
     * @return
     * @throws IOException
     * @throws ConflictException
     * @throws com.github.onsdigital.zebedee.exceptions.BadRequestException
     * @throws UnauthorizedException
     */
    @POST
    public boolean unlockCollection(HttpServletRequest request, HttpServletResponse response) throws IOException, ConflictException, BadRequestException, UnauthorizedException {

        com.github.onsdigital.zebedee.model.Collection collection = Collections.getCollection(request);
        Session session = Root.zebedee.sessions.get(request);
        boolean result = Root.zebedee.collections.unlock(collection, session);
        if (result) {
            Audit.log(request, "Collection %s unlocked by %s", collection.path, session.email);
        }

        return result;
    }
}
