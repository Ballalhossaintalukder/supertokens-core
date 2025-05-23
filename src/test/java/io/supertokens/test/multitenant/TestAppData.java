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

package io.supertokens.test.multitenant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.security.InvalidKeyException;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import javax.crypto.spec.SecretKeySpec;

import io.supertokens.pluginInterface.webauthn.AccountRecoveryTokenInfo;
import io.supertokens.pluginInterface.webauthn.WebAuthNOptions;
import io.supertokens.pluginInterface.webauthn.WebAuthNStorage;
import io.supertokens.pluginInterface.webauthn.WebAuthNStoredCredential;
import io.supertokens.pluginInterface.webauthn.exceptions.DuplicateUserEmailException;
import io.supertokens.pluginInterface.webauthn.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.webauthn.slqStorage.WebAuthNSQLStorage;
import org.apache.commons.codec.binary.Base32;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.google.gson.JsonObject;

import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.oauth.OAuth;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.oauth.OAuthStorage;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.session.Session;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.bulkimport.BulkImportTestUtils;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.totp.Totp;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.userroles.UserRoles;

public class TestAppData {
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

    private String[] removeStrings(String[] arr, String[] toRemove) {
        return Arrays.stream(arr).filter(s -> !Arrays.asList(toRemove).contains(s)).toArray(String[]::new);
    }

    private static String generateTotpCode(Main main, TOTPDevice device, int step)
            throws InvalidKeyException, StorageQueryException {
        final TimeBasedOneTimePasswordGenerator totp = new TimeBasedOneTimePasswordGenerator(
                Duration.ofSeconds(device.period));

        byte[] keyBytes = new Base32().decode(device.secretKey);
        Key key = new SecretKeySpec(keyBytes, "HmacSHA1");

        return totp.generateOneTimePasswordString(key, Instant.now().plusSeconds(step * device.period));
    }

    @Test
    public void testThatDeletingAppDeleteDataFromAllTables() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                        new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY, EE_FEATURES.MFA});
        Utils.setValueInConfig("oauth_client_secret_encryption_key", "secret");
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String[] tablesToIgnore = new String[]{"tenant_thirdparty_provider_clients", "tenant_thirdparty_providers",
                "tenant_first_factors", "tenant_required_secondary_factors"};

        TenantIdentifier app = new TenantIdentifier(null, "a1", null);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                app,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        Storage appStorage = (
                StorageLayer.getStorage(app, process.getProcess()));

        String[] allTableNames = appStorage.getAllTablesInTheDatabase();
        allTableNames = removeStrings(allTableNames, tablesToIgnore);
        Arrays.sort(allTableNames);

        // Add all recipe data
        AuthRecipeUserInfo epUser = EmailPassword.signUp(app, appStorage, process.getProcess(), "test@example.com",
                "password");
        EmailPassword.generatePasswordResetTokenBeforeCdi4_0(app, appStorage, process.getProcess(),
                epUser.getSupertokensUserId());

        ThirdParty.SignInUpResponse tpUser = ThirdParty.signInUp(app, appStorage, process.getProcess(), "google",
                "googleid", "test@example.com");

        Passwordless.CreateCodeResponse code = Passwordless.createCode(app, appStorage, process.getProcess(),
                "test@example.com", null, null, null);
        Passwordless.ConsumeCodeResponse plUser = Passwordless.consumeCode(app, appStorage, process.getProcess(),
                code.deviceId, code.deviceIdHash, code.userInputCode, null);
        Passwordless.createCode(app, appStorage, process.getProcess(), "test@example.com", null, null, null);

        Dashboard.signUpDashboardUser(app.toAppIdentifier(), appStorage, process.getProcess(),
                "user@example.com", "password");
        Dashboard.signInDashboardUser(app.toAppIdentifier(), appStorage, process.getProcess(),
                "user@example.com", "password");

        String evToken = EmailVerification.generateEmailVerificationToken(app, appStorage, process.getProcess(),
                epUser.getSupertokensUserId(), epUser.loginMethods[0].email);
        EmailVerification.verifyEmail(app, appStorage, evToken);
        EmailVerification.generateEmailVerificationToken(app, appStorage, process.getProcess(),
                tpUser.user.getSupertokensUserId(),
                tpUser.user.loginMethods[0].email);

        Session.createNewSession(app, appStorage, process.getProcess(), epUser.getSupertokensUserId(),
                new JsonObject(), new JsonObject());

        UserRoles.createNewRoleOrModifyItsPermissions(app.toAppIdentifier(), appStorage, "role",
                new String[]{"permission1", "permission2"});
        UserRoles.addRoleToUser(process.getProcess(), app, appStorage, epUser.getSupertokensUserId(), "role");

        TOTPDevice totpDevice = Totp.registerDevice(app.toAppIdentifier(), appStorage, process.getProcess(),
                epUser.getSupertokensUserId(), "test", 1, 3);
        Totp.verifyDevice(app, appStorage, process.getProcess(), epUser.getSupertokensUserId(), totpDevice.deviceName,
                generateTotpCode(process.getProcess(), totpDevice, -1));
        Totp.verifyCode(app, appStorage, process.getProcess(), epUser.getSupertokensUserId(),
                generateTotpCode(process.getProcess(), totpDevice, 0));

        ActiveUsers.updateLastActive(app.toAppIdentifier(), process.getProcess(),
                epUser.getSupertokensUserId());

        UserMetadata.updateUserMetadata(app.toAppIdentifier(), appStorage,
                epUser.getSupertokensUserId(), new JsonObject());

        UserIdMapping.createUserIdMapping(process.getProcess(), app.toAppIdentifier(), appStorage,
                plUser.user.getSupertokensUserId(), "externalid", null, false);

        if(!StorageLayer.isInMemDb(process.getProcess())) {
            BulkImport.addUsers(app.toAppIdentifier(), appStorage, BulkImportTestUtils.generateBulkImportUser(1));
        }

        OAuth.addOrUpdateClient(process.getProcess(), app.toAppIdentifier(), appStorage, "test", "secret123", false, false);
        OAuth.createLogoutRequestAndReturnRedirectUri(process.getProcess(), app.toAppIdentifier(), appStorage, "test", "http://localhost", "sessionHandle", "state");
        ((OAuthStorage) appStorage).addOAuthM2MTokenForStats(app.toAppIdentifier(), "test", 1000, 2000);
        OAuth.revokeSessionHandle(process.getProcess(), app.toAppIdentifier(), appStorage, "sessionHandle");
        OAuth.createOrUpdateOauthSession(process.getProcess(), app.toAppIdentifier(), appStorage, "test", "test-gid", null, null, "sessionHandle", "jti", 0);

        WebAuthNStoredCredential credential = new WebAuthNStoredCredential();
        credential.id = "abcd";
        credential.appId = app.getAppId();
        credential.rpId = "example.com";
        credential.userId = "userId";
        credential.counter = 0;
        credential.publicKey = new byte[0];
        credential.transports = "abcd";
        credential.createdAt = 0;
        credential.updatedAt = 0;

        ((WebAuthNSQLStorage) appStorage).startTransaction((con) -> {
            try {
                ((WebAuthNSQLStorage) appStorage).signUpWithCredentialsRegister_Transaction(app, con, "userId", "test2@example.com", "example.com", credential);
            } catch (DuplicateUserIdException e) {
                throw new RuntimeException(e);
            } catch (DuplicateUserEmailException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
        ((WebAuthNStorage) appStorage).addRecoverAccountToken(app, new AccountRecoveryTokenInfo("userId", "test@example.com", "abcd", System.currentTimeMillis() + 3600));
        WebAuthNOptions options = new WebAuthNOptions();
        options.generatedOptionsId = "abcd";
        options.relyingPartyId = "example.com";
        options.relyingPartyName = "Example";
        options.userEmail = "test@example.com";
        options.timeout = 3600L;
        options.challenge = "abcd"; //base64 url encoded
        options.origin = "abcd";
        options.expiresAt = System.currentTimeMillis() + 3600;
        options.createdAt = System.currentTimeMillis();
        options.userPresenceRequired = false;
        options.userVerification = "required";
        ((WebAuthNStorage) appStorage).saveGeneratedOptions(app, options);

        String[] tablesThatHaveData = appStorage
                .getAllTablesInTheDatabaseThatHasDataForAppId(app.getAppId());
        tablesThatHaveData = removeStrings(tablesThatHaveData, tablesToIgnore);
        Arrays.sort(tablesThatHaveData);

        assertEquals(allTableNames, tablesThatHaveData);

        // Delete the app
        Multitenancy.deleteApp(app.toAppIdentifier(), process.getProcess());

        // Check no data is remaining in any of the tables
        tablesThatHaveData = appStorage.getAllTablesInTheDatabaseThatHasDataForAppId(app.getAppId());
        tablesThatHaveData = removeStrings(tablesThatHaveData, tablesToIgnore);
        assertEquals(0, tablesThatHaveData.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}