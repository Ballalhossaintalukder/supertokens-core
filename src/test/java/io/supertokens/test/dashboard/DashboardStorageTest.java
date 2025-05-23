/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.test.dashboard;

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.dashboard.DashboardSessionInfo;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.dashboard.exceptions.UserIdNotFoundException;
import io.supertokens.pluginInterface.dashboard.sqlStorage.DashboardSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class DashboardStorageTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testCreateNewDashboardUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a dashboard user
        String userId = io.supertokens.utils.Utils.getUUID();
        String email = "test@example.com";
        String passwordHash = "testPasswordHash";

        DashboardSQLStorage dashboardSQLStorage = (DashboardSQLStorage) StorageLayer.getStorage(process.getProcess());

        {
            DashboardUser user = new DashboardUser(userId, email, passwordHash, System.currentTimeMillis());
            dashboardSQLStorage.createNewDashboardUser(process.getAppForTesting().toAppIdentifier(), user);

            // get dashboard users to verify that user was created
            DashboardUser[] users = dashboardSQLStorage.getAllDashboardUsers(process.getAppForTesting().toAppIdentifier());
            assertEquals(1, users.length);
            assertEquals(user, users[0]);
        }

        {
            // test creating a user with a duplicate userId
            DashboardUser user = new DashboardUser(userId, "test2@example.com", passwordHash,
                    System.currentTimeMillis());
            Exception error = null;
            try {
                dashboardSQLStorage.createNewDashboardUser(process.getAppForTesting().toAppIdentifier(), user);
                throw new Exception("Should never come here");
            } catch (DuplicateUserIdException e) {
                error = e;
            }

            assertNotNull(error);
        }

        {
            // test creating a user with a duplicate email
            DashboardUser user = new DashboardUser("newUserId", "test@example.com", passwordHash,
                    System.currentTimeMillis());
            Exception error = null;
            try {
                dashboardSQLStorage.createNewDashboardUser(process.getAppForTesting().toAppIdentifier(), user);
                throw new Exception("Should never come here");
            } catch (DuplicateEmailException e) {
                error = e;
            }

            assertNotNull(error);

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetDashboardUserFunctions() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a dashboard user
        String userId = io.supertokens.utils.Utils.getUUID();
        String email = "test@example.com";
        String passwordHash = "testPasswordHash";

        DashboardSQLStorage dashboardSQLStorage = (DashboardSQLStorage) StorageLayer.getStorage(process.getProcess());

        // create a dashboardUser
        DashboardUser user = new DashboardUser(userId, email, passwordHash, System.currentTimeMillis());
        dashboardSQLStorage.createNewDashboardUser(process.getAppForTesting().toAppIdentifier(), user);

        {
            // retrieve user with email
            DashboardUser retrievedUser = dashboardSQLStorage.getDashboardUserByEmail(process.getAppForTesting().toAppIdentifier(),
                    email);
            assertEquals(user, retrievedUser);
        }

        {
            // retrieve user with userId
            DashboardUser retrievedUser = dashboardSQLStorage.getDashboardUserByUserId(process.getAppForTesting().toAppIdentifier(),
                    userId);
            assertEquals(user, retrievedUser);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetAllDashboardUsers() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create 10 dashboard users and make sure they are returned in order of them
        // being created
        for (int i = 0; i < 10; i++) {

            DashboardUser user = new DashboardUser(io.supertokens.utils.Utils.getUUID(), "test" + i + "@example.com",
                    "testPasswordHash", System.currentTimeMillis());
            ((DashboardSQLStorage) StorageLayer.getStorage(process.getProcess()))
                    .createNewDashboardUser(process.getAppForTesting().toAppIdentifier(), user);
            Thread.sleep(10);
        }

        // retrieve all dashboard users, check that correctly created and returned in
        // the correct order.

        DashboardUser[] users = ((DashboardSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllDashboardUsers(process.getAppForTesting().toAppIdentifier());
        assertEquals(10, users.length);

        for (int i = 0; i < 10; i++) {
            assertEquals("test" + i + "@example.com", users[i].email);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // test the deleteDashboardUserWithUserId function
    @Test
    public void testTheDeleteDashboardUserWithUserIdFunction() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        DashboardSQLStorage dashboardSQLStorage = (DashboardSQLStorage) StorageLayer.getStorage(process.getProcess());

        // check no user exists
        assertEquals(0, dashboardSQLStorage.getAllDashboardUsers(process.getAppForTesting().toAppIdentifier()).length);

        // create a user
        DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), "test@example.com", "testPass123");
        assertNotNull(user);

        // check that user was created
        assertEquals(1, dashboardSQLStorage.getAllDashboardUsers(process.getAppForTesting().toAppIdentifier()).length);

        // delete dashboard user
        assertTrue(dashboardSQLStorage.deleteDashboardUserWithUserId(process.getAppForTesting().toAppIdentifier(), user.userId));

        // check that no users exist
        assertEquals(0, dashboardSQLStorage.getAllDashboardUsers(process.getAppForTesting().toAppIdentifier()).length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testTheCreateNewDashboardUserSession() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        DashboardSQLStorage dashboardSQLStorage = (DashboardSQLStorage) StorageLayer.getStorage(process.getProcess());

        // create a dashboard session for a user that does not exist
        Exception error = null;
        try {
            dashboardSQLStorage.createNewDashboardUserSession(process.getAppForTesting().toAppIdentifier(), "unknownUserId",
                    "testSessionId", 0, 0);
            throw new Exception("Should never come here");
        } catch (UserIdNotFoundException e) {
            error = e;
        }

        assertNotNull(error);

        // create a user and create a session for the user
        DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), "test@example.com", "password123");

        dashboardSQLStorage.createNewDashboardUserSession(process.getAppForTesting().toAppIdentifier(), user.userId,
                io.supertokens.utils.Utils.getUUID(), 0, 0);

        // check that the session was successfully created
        DashboardSessionInfo[] sessionInfo = dashboardSQLStorage.getAllSessionsForUserId(process.getAppForTesting().toAppIdentifier(),
                user.userId);
        assertEquals(1, sessionInfo.length);
        assertEquals(user.userId, sessionInfo[0].userId);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingMultipleSessionsForAUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        DashboardSQLStorage dashboardSQLStorage = (DashboardSQLStorage) StorageLayer.getStorage(process.getProcess());

        // create a user
        DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), "test@example.com", "password123");
        ArrayList<String> sessionIds = new ArrayList<>();

        // create 5 sessions for the user
        for (int i = 0; i < 5; i++) {
            String sessionId = io.supertokens.utils.Utils.getUUID();
            sessionIds.add(sessionId);
            dashboardSQLStorage.createNewDashboardUserSession(process.getAppForTesting().toAppIdentifier(), user.userId, sessionId, 0,
                    0);
        }

        // get all sessions for userId
        DashboardSessionInfo[] sessionInfoArray = dashboardSQLStorage.getAllSessionsForUserId(
                process.getAppForTesting().toAppIdentifier(), user.userId);
        assertEquals(5, sessionInfoArray.length);

        // test retrieving sessions
        for (int i = 0; i < 2; i++) {
            DashboardSessionInfo sessionInfo = dashboardSQLStorage.getSessionInfoWithSessionId(
                    process.getAppForTesting().toAppIdentifier(), sessionIds.get(i));
            assertNotNull(sessionInfo);
            assertEquals(user.userId, sessionInfo.userId);
        }

        // delete some user sessions
        dashboardSQLStorage.revokeSessionWithSessionId(process.getAppForTesting().toAppIdentifier(), sessionIds.get(0));
        dashboardSQLStorage.revokeSessionWithSessionId(process.getAppForTesting().toAppIdentifier(), sessionIds.get(1));

        // retrieve all sessions
        DashboardSessionInfo[] dashboardSessionInfo = dashboardSQLStorage.getAllSessionsForUserId(
                process.getAppForTesting().toAppIdentifier(), user.userId);
        assertEquals(3, dashboardSessionInfo.length);

        // check that two sessions were deleted
        for (int i = 0; i < 2; i++) {
            assertNull(
                    dashboardSQLStorage.getSessionInfoWithSessionId(process.getAppForTesting().toAppIdentifier(), sessionIds.get(i)));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRevokeExpiredSessionsFunction() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        DashboardSQLStorage dashboardSQLStorage = (DashboardSQLStorage) StorageLayer.getStorage(process.getProcess());

        // create a user
        DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), "test@example.com", "password123");
        ArrayList<String> sessionIds = new ArrayList<>();

        // create 3 sessions for the user with expiry in 3 seconds after creation
        for (int i = 0; i < 3; i++) {
            String sessionId = io.supertokens.utils.Utils.getUUID();
            sessionIds.add(sessionId);
            dashboardSQLStorage.createNewDashboardUserSession(process.getAppForTesting().toAppIdentifier(), user.userId, sessionId,
                    System.currentTimeMillis(),
                    System.currentTimeMillis() + 3000);
        }

        // create 3 sessions for the user with the regular expiry time
        for (int i = 0; i < 3; i++) {
            String sessionId = io.supertokens.utils.Utils.getUUID();
            sessionIds.add(sessionId);
            dashboardSQLStorage.createNewDashboardUserSession(process.getAppForTesting().toAppIdentifier(), user.userId, sessionId,
                    System.currentTimeMillis(),
                    System.currentTimeMillis() + Dashboard.DASHBOARD_SESSION_DURATION);
        }

        // check that sessions were successfully created
        assertEquals(6, dashboardSQLStorage.getAllSessionsForUserId(process.getAppForTesting().toAppIdentifier(), user.userId).length);

        // wait for 5 seconds so sessions expire
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            // Ignored
        }

        // revoke expired sessions
        dashboardSQLStorage.revokeExpiredSessions();

        // check that half the sessions were revoked
        assertEquals(3, dashboardSQLStorage.getAllSessionsForUserId(process.getAppForTesting().toAppIdentifier(), user.userId).length);

        for (int i = 0; i < sessionIds.size(); i++) {
            if (i < 3) {
                assertNull(dashboardSQLStorage.getSessionInfoWithSessionId(process.getAppForTesting().toAppIdentifier(),
                        sessionIds.get(i)));
            } else {
                assertNotNull(dashboardSQLStorage.getSessionInfoWithSessionId(process.getAppForTesting().toAppIdentifier(),
                        sessionIds.get(i)));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingUsersEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        DashboardSQLStorage dashboardSQLStorage = (DashboardSQLStorage) StorageLayer.getStorage(process.getProcess());

        {
            // try updating the email for a user that does not exist
            Exception error = null;
            try {
                dashboardSQLStorage.startTransaction(transaction -> {
                    try {
                        dashboardSQLStorage.updateDashboardUsersEmailWithUserId_Transaction(
                                process.getAppForTesting().toAppIdentifier(), transaction,
                                "unknownUserId", "test@example.com");
                        return null;
                    } catch (Exception e) {
                        throw new StorageTransactionLogicException(e);
                    }
                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof UserIdNotFoundException) {
                    error = (UserIdNotFoundException) e.actualException;
                }
            }
            assertNotNull(error);
            assertTrue(error instanceof UserIdNotFoundException);
        }

        // create a user
        DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), "test@example.com", "password123");

        // update the users email
        String newEmail = "updatedTest@example.com";
        try {
            dashboardSQLStorage.startTransaction(transaction -> {
                try {
                    dashboardSQLStorage.updateDashboardUsersEmailWithUserId_Transaction(process.getAppForTesting().toAppIdentifier(),
                            transaction,
                            user.userId, newEmail);
                    return null;
                } catch (Exception e) {
                    throw new StorageTransactionLogicException(e);
                }
            });
        } catch (StorageTransactionLogicException e) {
            throw new Exception(e);
        }

        // check that the retrieving the user with the original email does not work
        assertNull(dashboardSQLStorage.getDashboardUserByEmail(process.getAppForTesting().toAppIdentifier(), user.email));

        // check that retrieving the user with the new email works
        DashboardUser updatedUser = dashboardSQLStorage.getDashboardUserByEmail(process.getAppForTesting().toAppIdentifier(),
                newEmail);
        assertNotNull(updatedUser);

        // check that the userIds are the same
        assertEquals(user.userId, updatedUser.userId);

        // check that no additional users have been created.
        assertEquals(1, dashboardSQLStorage.getAllDashboardUsers(process.getAppForTesting().toAppIdentifier()).length);

        // create another user 
        DashboardUser user2 = new DashboardUser(io.supertokens.utils.Utils.getUUID(), "test2@example.com",
                "testpassword", System.currentTimeMillis());
        dashboardSQLStorage.createNewDashboardUser(process.getAppForTesting().toAppIdentifier(), user2);

        // try updating user2s email with the user1s email

        {
            // try updating the email for a user that does not exist
            Exception error = null;
            try {
                dashboardSQLStorage.startTransaction(transaction -> {
                    try {
                        dashboardSQLStorage.updateDashboardUsersEmailWithUserId_Transaction(
                                process.getAppForTesting().toAppIdentifier(), transaction,
                                user2.userId, newEmail);
                        return null;
                    } catch (Exception e) {
                        throw new StorageTransactionLogicException(e);
                    }
                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof DuplicateEmailException) {
                    error = (DuplicateEmailException) e.actualException;
                }
            }
            assertNotNull(error);
            assertTrue(error instanceof DuplicateEmailException);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

}
