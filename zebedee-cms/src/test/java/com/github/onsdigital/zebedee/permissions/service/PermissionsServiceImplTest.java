package com.github.onsdigital.zebedee.permissions.service;

import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.json.PermissionDefinition;
import com.github.onsdigital.zebedee.permissions.model.AccessMapping;
import com.github.onsdigital.zebedee.permissions.store.PermissionsStore;
import com.github.onsdigital.zebedee.session.model.Session;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Created by dave on 31/05/2017.
 */
public class PermissionsServiceImplTest {

    private static final String EMAIL = "admin@ons.gov.uk";
    private static final String ADMIN_GROUP = "role-admin";
    private static final String PUBLISHER_GROUP = "role-publisher";

    private Set<String> digitalPublishingTeam = new HashSet<>();
    private Set<String> admins = new HashSet<>();

    /**
     * Class under test
     */
    private PermissionsServiceImpl permissions;

    @Mock
    private PermissionsStore permissionsStore;

    @Mock
    private AccessMapping accessMapping;

    @Mock
    private Session session;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        when(session.getEmail())
                .thenReturn(EMAIL);
        when(permissionsStore.getAccessMapping())
                .thenReturn(accessMapping);

        permissions = new PermissionsServiceImpl(permissionsStore);
    }


    @Test
    public void isAdministrator_Session_Admin_ShouldReturnTrue() throws Exception {
        List<String> sessionGroups = new ArrayList<>();
        sessionGroups.add(ADMIN_GROUP);

        when(session.getGroups())
                .thenReturn(sessionGroups);

        assertTrue(permissions.isAdministrator(session));
    }

    @Test
    public void isAdministrator_Session_Publisher_ShouldReturnFalse() throws Exception {
        List<String> sessionGroups = new ArrayList<>();
        sessionGroups.add(PUBLISHER_GROUP);

        when(session.getGroups())
                .thenReturn(sessionGroups);

        assertFalse(permissions.isAdministrator(session));
    }

    @Test
    public void isAdministrator_SessionNull_ShouldReturnFalse() throws Exception {
        Session session = null;
        assertFalse(permissions.isAdministrator(session));
        verifyNoInteractions(permissionsStore);
    }

    @Test
    public void isAdministrator_Session_NotAdmin_ShouldReturnFalse() throws Exception {
        when(session.getGroups())
                .thenReturn(new ArrayList<>());

        assertFalse(permissions.isAdministrator(session));
        verifyNoInteractions(permissionsStore);
    }

    @Test
    public void isAdministrator_Session_NullGroup_ShouldReturnFalse() throws Exception {
        assertFalse(permissions.isAdministrator(session));
        verifyNoInteractions(permissionsStore);
    }

    @Test
    public void hasAdministrator_ShouldReturnFalseIfAdminsIsNull() throws Exception {
        when(accessMapping.getAdministrators())
                .thenReturn(null);

        assertFalse(permissions.hasAdministrator());

        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(1)).getAdministrators();
    }

    @Test
    public void hasAdministrator_ShouldReturnFalseIfAdminsIsEmpty() throws Exception {
        when(accessMapping.getAdministrators())
                .thenReturn(new HashSet<>());

        assertFalse(permissions.hasAdministrator());

        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
    }

    @Test
    public void hasAdministrator_ShouldReturnTrueIfAdminsIsNotEmpty() throws Exception {
        Set<String> admins = new HashSet<>();
        admins.add(EMAIL);

        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        assertTrue(permissions.hasAdministrator());

        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
    }


    @Test
    public void removeAdministrator_ShouldThrowExceptionIfSessionNull() throws Exception {
        Exception exception = assertThrows(UnauthorizedException.class, () ->
                permissions.removeAdministrator(EMAIL, null));

        verifyNoInteractions(permissionsStore, accessMapping);
    }

    @Test
    public void removeAdministrator_ShouldThrowExceptionIfEmailNull() throws Exception {
        Exception exception = assertThrows(UnauthorizedException.class, () ->
                permissions.removeAdministrator(null, session));

        verifyNoInteractions(permissionsStore, accessMapping);
    }

    @Test
    public void removeAdministrator_ShouldThrowExceptionIfUserIsNotAnAdmin() throws Exception {
        when(session.getGroups())
                .thenReturn(new ArrayList<>());
        when(accessMapping.getAdministrators())
                .thenReturn(new HashSet<>());

        Exception exception = assertThrows(UnauthorizedException.class, () ->
                permissions.removeAdministrator(EMAIL, session));

        verifyNoInteractions(permissionsStore, accessMapping);
    }

    @Test
    public void removeAdministrator_Success() throws Exception {
        Set<String> admins = new HashSet<>();
        admins.add(EMAIL);
        List<String> sessionGroups = new ArrayList<>();
        sessionGroups.add(ADMIN_GROUP);

        when(session.getGroups())
                .thenReturn(sessionGroups);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        permissions.removeAdministrator(EMAIL, session);

        assertFalse(admins.contains(EMAIL));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verify(permissionsStore, times(1)).saveAccessMapping(accessMapping);
    }


    @Test
    public void removeEditor_ShouldThrowExceptionIfSessionNull() throws Exception {
        Exception exception = assertThrows(UnauthorizedException.class, () ->
                permissions.removeEditor(EMAIL, null));

        verifyNoInteractions(permissionsStore, accessMapping);
    }

    @Test
    public void removeEditor_ShouldThrowExceptionIfEmailNull() throws Exception {
        Exception exception = assertThrows(UnauthorizedException.class, () ->
                permissions.removeEditor(null, session));

        verifyNoInteractions(permissionsStore, accessMapping);
    }

    @Test
    public void removeEditor_ShouldThrowExceptionIfUserIsNotAnAdmin() throws Exception {
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(new HashSet<>());
        when(session.getGroups())
                .thenReturn(new ArrayList<>());

        Exception exception = assertThrows(UnauthorizedException.class, () ->
                permissions.removeEditor(EMAIL, session));

        verifyNoInteractions(permissionsStore, accessMapping);
    }

    @Test
    public void removeEditor_Success() throws Exception {
        Set<String> publishers = new HashSet<>();
        publishers.add(EMAIL);
        List<String> sessionGroups = new ArrayList<>();
        sessionGroups.add(ADMIN_GROUP);

        when(session.getGroups())
                .thenReturn(sessionGroups);
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(publishers);

        permissions.removeEditor(EMAIL, session);

        assertFalse(publishers.contains(EMAIL));
        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(1)).getDigitalPublishingTeam();
        verify(permissionsStore, times(1)).saveAccessMapping(accessMapping);
    }

    @Test
    public void addAdministrator_ShouldThrowErrorSessionNull() throws Exception {
        Set<String> admins = new HashSet<>();
        admins.add(EMAIL);

        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        Exception exception = assertThrows(UnauthorizedException.class, () ->
                permissions.addAdministrator(EMAIL, null));

        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verifyNoMoreInteractions(permissionsStore, accessMapping);
    }

    @Test
    public void addAdministrator_ShouldThrowErrorSessionEmailNull() throws Exception {
        Set<String> admins = new HashSet<>();
        admins.add(EMAIL);

        when(session.getEmail())
                .thenReturn(null);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        Exception exception = assertThrows(UnauthorizedException.class, () ->
                permissions.addAdministrator(EMAIL, session));

        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verifyNoMoreInteractions(permissionsStore, accessMapping);
    }

    @Test
    public void addAdministrator_ShouldThrowErrorIfUserNotAdmin() throws Exception {
        String email2 = "test2@ons.gov.uk";
        Set<String> admins = new HashSet<>();
        admins.add(EMAIL);

        when(session.getEmail())
                .thenReturn(EMAIL);
        when(session.getGroups())
                .thenReturn(new ArrayList<>());
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        Exception exception = assertThrows(UnauthorizedException.class, () ->
                permissions.addAdministrator(email2, session));

        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verifyNoMoreInteractions(permissionsStore, accessMapping);
    }


    @Test
    public void addAdministrator_Success() throws Exception {
        Set<String> admins = new HashSet<>();
        admins.add(EMAIL);
        String email2 = "test2@ons.gov.uk";
        List<String> sessionGroups = new ArrayList<>();
        sessionGroups.add(ADMIN_GROUP);

        when(session.getGroups())
                .thenReturn(sessionGroups);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        permissions.addAdministrator(email2, session);

        assertTrue(admins.contains(email2));
        verify(permissionsStore, times(2)).getAccessMapping();
        verify(accessMapping, times(4)).getAdministrators();
        verify(permissionsStore, times(1)).saveAccessMapping(accessMapping);
    }

    @Test
    public void addEditor_ShouldThrowErrorSessionNull() throws Exception {
        Set<String> admins = new HashSet<>();
        admins.add(EMAIL);

        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        Exception exception = assertThrows(UnauthorizedException.class, () ->
                permissions.addEditor(EMAIL, null));

        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verifyNoMoreInteractions(permissionsStore, accessMapping);
    }

    @Test
    public void addEditor_ShouldThrowErrorSessionEmailNull() throws Exception {
        Set<String> admins = new HashSet<>();
        admins.add(EMAIL);

        when(session.getEmail())
                .thenReturn(null);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        Exception exception = assertThrows(UnauthorizedException.class, () ->
                permissions.addEditor(EMAIL, session));

        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verifyNoMoreInteractions(permissionsStore, accessMapping);
    }

    @Test
    public void addEditor_ShouldThrowErrorIfUserNotAdmin() throws Exception {
        Set<String> admins = new HashSet<>();
        admins.add(EMAIL);

        when(session.getEmail())
                .thenReturn(EMAIL);
        when(session.getGroups())
                .thenReturn(new ArrayList<>());
        when(accessMapping.getAdministrators())
                .thenReturn(admins);

        Exception exception = assertThrows(UnauthorizedException.class, () ->
                permissions.addEditor(EMAIL, session));

        verify(permissionsStore, times(1)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verifyNoMoreInteractions(permissionsStore, accessMapping);
    }

    @Test
    public void addEditor_Success() throws Exception {
        Set<String> admins = new HashSet<>();
        admins.add(EMAIL);
        Set<String> publishers = new HashSet<>();
        publishers.add(EMAIL);
        String email2 = "test2@ons.gov.uk";
        List<String> sessionGroups = new ArrayList<>();
        sessionGroups.add(ADMIN_GROUP);

        when(session.getGroups())
                .thenReturn(sessionGroups);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(publishers);

        permissions.addEditor(email2, session);

        assertTrue(publishers.contains(email2));
        verify(permissionsStore, times(2)).getAccessMapping();
        verify(accessMapping, times(2)).getAdministrators();
        verify(accessMapping, times(1)).getDigitalPublishingTeam();
        verify(permissionsStore, times(1)).saveAccessMapping(accessMapping);
    }


    @Test
    public void userPermissions_Success_Self() throws Exception {
        Set<String> admins = new HashSet<>();
        admins.add(EMAIL);
        Set<String> publishers = new HashSet<>();
        publishers.add(EMAIL);

        when(session.getEmail())
                .thenReturn(EMAIL);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(publishers);

        PermissionDefinition result = permissions.userPermissions(EMAIL, session);

        assertTrue(result.isAdmin());
        assertTrue(result.isEditor());
        assertEquals(EMAIL, result.getEmail());
    }

    @Test
    public void userPermissions_Success_SelfNoPermissions() throws Exception {
        Set<String> admins = new HashSet<>();
        Set<String> publishers = new HashSet<>();

        when(session.getEmail())
                .thenReturn(EMAIL);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(publishers);

        PermissionDefinition result = permissions.userPermissions(EMAIL, session);

        assertFalse(result.isAdmin());
        assertFalse(result.isEditor());
        assertEquals(EMAIL, result.getEmail());
    }

    @Test
    public void userPermissions_Success_Other() throws Exception {
        String anotherEmail = "test@ons.gov.uk";
        Set<String> admins = new HashSet<>();
        admins.add(anotherEmail);
        Set<String> publishers = new HashSet<>();
        publishers.add(anotherEmail);
        List<String> sessionGroups = new ArrayList<>();
        sessionGroups.add(ADMIN_GROUP);

        when(session.getEmail())
                .thenReturn(EMAIL);
        when(session.getGroups())
                .thenReturn(sessionGroups);
        when(accessMapping.getAdministrators())
                .thenReturn(admins);
        when(accessMapping.getDigitalPublishingTeam())
                .thenReturn(publishers);

        PermissionDefinition result = permissions.userPermissions(anotherEmail, session);

        assertTrue(result.isAdmin());
        assertTrue(result.isEditor());
        assertEquals(anotherEmail, result.getEmail());
    }
}
