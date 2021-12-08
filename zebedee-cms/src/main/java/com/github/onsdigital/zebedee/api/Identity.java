package com.github.onsdigital.zebedee.api;

import com.github.davidcarboni.restolino.framework.Api;
import com.github.onsdigital.zebedee.authorisation.AuthorisationService;
import com.github.onsdigital.zebedee.authorisation.AuthorisationServiceImpl;
import com.github.onsdigital.zebedee.authorisation.UserIdentity;
import com.github.onsdigital.zebedee.authorisation.UserIdentityException;
import com.github.onsdigital.zebedee.json.response.Error;
import com.github.onsdigital.zebedee.model.ServiceAccount;
import com.github.onsdigital.zebedee.reader.util.RequestUtils;
import com.github.onsdigital.zebedee.service.ServiceStore;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import java.io.IOException;

import static com.github.onsdigital.zebedee.logging.CMSLogEvent.error;
import static com.github.onsdigital.zebedee.logging.CMSLogEvent.warn;
import static com.github.onsdigital.zebedee.service.ServiceTokenUtils.*;
import static com.github.onsdigital.zebedee.util.JsonUtils.writeResponseEntity;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

@Deprecated
@Api
public class Identity {

    private AuthorisationService authorisationService;
    private ServiceStore serviceStore;

    static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * Construct the default Identity api endpoint.
     */
    public Identity() {
        this(Root.zebedee.getServiceStore(), new AuthorisationServiceImpl());
    }

    /**
     * Construct and Identity api endpoint explicitly enabling/disabling the datasetImportEnabled feature.
     */
    public Identity(ServiceStore serviceStore, AuthorisationService authorisationService) {
        this.serviceStore = serviceStore;
        this.authorisationService = authorisationService;
    }

    @GET
    public void identifyUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // TODO: Remove after migration from X-Florence-Token to Authorization header is complete
        String florenceHeader = request.getHeader(RequestUtils.FLORENCE_TOKEN_HEADER);
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (florenceHeader != null || (StringUtils.isNotBlank(authHeader) && authHeader.contains("."))) {
            findUser(request, response);
            return;
        }

        // TODO: Remove after new service user JWT auth is implemented
        if (StringUtils.isNotBlank(authHeader)) {
            ServiceAccount serviceAccount = handleServiceAccountRequest(request);
            if (serviceAccount != null) {
                writeResponseEntity(response, new UserIdentity(serviceAccount.getID()), SC_OK);
                return;
            }
        }
        writeResponseEntity(response, new Error("service not authenticated"), SC_UNAUTHORIZED);
    }

    private void findUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String sessionID = RequestUtils.getSessionId(request);

        if (StringUtils.isEmpty(sessionID)) {
            Error responseBody = new Error("user not authenticated");
            warn().log(responseBody.getMessage());
            writeResponseEntity(response, responseBody, SC_UNAUTHORIZED);
            return;
        }

        try {
            UserIdentity identity = authorisationService.identifyUser(sessionID);
            writeResponseEntity(response, identity, SC_OK);
        } catch (UserIdentityException e) {
            error().logException(e, "identity endpoint: identify user failure, returning error response");
            writeResponseEntity(response, new Error(e.getMessage()), e.getResponseCode());
        }
    }

    private ServiceAccount handleServiceAccountRequest(HttpServletRequest request) throws IOException {
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);

        ServiceAccount serviceAccount = null;

        if (isValidServiceAuthorizationHeader(authorizationHeader)) {
            String serviceToken = extractServiceAccountTokenFromAuthHeader(authorizationHeader);

            if (isValidServiceToken(serviceToken)) {
                serviceAccount = getServiceAccount(serviceToken);
            }
        }
        return serviceAccount;
    }

    private ServiceAccount getServiceAccount(String serviceToken) throws IOException {
        ServiceAccount serviceAccount = null;
        try {
            serviceAccount = serviceStore.get(serviceToken);
            if (serviceAccount == null) {
                warn().log("service account not found for service token");
            }
        } catch (Exception ex) {
            error().exception(ex).log("unexpected error getting service account from service store");
            throw new IOException(ex);
        }
        return serviceAccount;
    }
}
