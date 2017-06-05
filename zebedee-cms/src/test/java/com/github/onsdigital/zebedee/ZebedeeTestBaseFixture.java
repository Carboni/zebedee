package com.github.onsdigital.zebedee;

import com.github.onsdigital.zebedee.json.User;
import com.github.onsdigital.zebedee.json.UserList;
import com.github.onsdigital.zebedee.model.Collection;
import com.github.onsdigital.zebedee.persistence.dao.CollectionHistoryDao;
import com.github.onsdigital.zebedee.service.ServiceSupplier;
import com.github.onsdigital.zebedee.service.UsersService;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Created by dave on 02/06/2017.
 */
public abstract class ZebedeeTestBaseFixture {

    @Mock
    private UsersService usersService;

    @Mock
    private KeyManangerUtil keyManangerUtil;

    @Mock
    private CollectionHistoryDao collectionHistoryDao;

    protected Zebedee zebedee;
    protected Builder builder;

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);

        builder = new Builder();
        zebedee = new Zebedee(builder.zebedee, false);

        UserList usersList = new UserList();
        usersList.add(builder.publisher1);

        ReflectionTestUtils.setField(zebedee, "usersService", usersService);

        ServiceSupplier<CollectionHistoryDao> collectionHistoryDaoServiceSupplier = () -> collectionHistoryDao;

        Collection.setCollectionHistoryDaoServiceSupplier(collectionHistoryDaoServiceSupplier);
        Collection.setKeyManagerUtil(keyManangerUtil);

        Map<String, User> usersMap = new HashMap<>();
        usersMap.put(builder.publisher1.getEmail(), builder.publisher1);

        when(usersService.getUserByEmail(builder.publisher1.getEmail()))
                .thenReturn(builder.publisher1);
        when(usersService.getUserByEmail(builder.reviewer1.getEmail()))
                .thenReturn(builder.reviewer1);
        when(usersService.getUserByEmail(builder.administrator.getEmail()))
                .thenReturn(builder.administrator);
        when(usersService.list())
                .thenReturn(usersList);

        Map<String, String> emailToCreds = new HashMap<>();
        emailToCreds.put(builder.publisher1.getEmail(), builder.publisher1Credentials.password);

        // UsersService is now mocked so needs to add this mannually.
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                User u = invocationOnMock.getArgumentAt(1, User.class);
                String id = invocationOnMock.getArgumentAt(2, String.class);
                SecretKey key = invocationOnMock.getArgumentAt(3, SecretKey.class);

                if (emailToCreds.containsKey(u.getEmail())) {
                    u.keyring().unlock(emailToCreds.get(u.getEmail()));
                    u.keyring().put(id, key);
                }
                return null;
            }
        }).when(keyManangerUtil).assignKeyToUser(any(), any(), any(), any());

        setUp();
    }

    public abstract void setUp() throws Exception;

    @After
    public void tearDown() throws Exception {
        builder.delete();
    }
}
