package org.wildfly.a2a.jakarta.test.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.arquillian.setup.SnapshotServerSetupTask;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;

public class ElytronSetupTask extends SnapshotServerSetupTask {

    private static final String USERS_FILE = "a2a-test-users.properties";
    private static final String ROLES_FILE = "a2a-test-roles.properties";

    private Path usersPropertiesFile;
    private Path rolesPropertiesFile;

    @Override
    protected void doSetup(ManagementClient managementClient, String containerId) throws Exception {
        Path configDir = getConfigDir(managementClient);
        createPropertiesFiles(configDir);
        createElytronResources(managementClient);
    }

    @Override
    protected void nonManagementCleanUp() throws Exception {
        if (usersPropertiesFile != null) {
            Files.deleteIfExists(usersPropertiesFile);
        }
        if (rolesPropertiesFile != null) {
            Files.deleteIfExists(rolesPropertiesFile);
        }
    }

    private Path getConfigDir(ManagementClient managementClient) throws IOException {
        ModelNode address = Operations.createAddress("core-service", "server-environment");
        ModelNode op = Operations.createReadAttributeOperation(address, "config-dir");
        ModelNode result = managementClient.getControllerClient().execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            throw new RuntimeException("Failed to read config-dir: "
                    + Operations.getFailureDescription(result).asString());
        }
        return Path.of(Operations.readResult(result).asString());
    }

    private void createPropertiesFiles(Path configDir) throws IOException {
        usersPropertiesFile = configDir.resolve(USERS_FILE);
        Files.writeString(usersPropertiesFile, "testuser=testpass\n");

        rolesPropertiesFile = configDir.resolve(ROLES_FILE);
        Files.writeString(rolesPropertiesFile, "testuser=user\n");
    }

    private void createElytronResources(ManagementClient managementClient) throws IOException {
        // 1. Properties realm
        ModelNode realmOp = Operations.createAddOperation(
                Operations.createAddress("subsystem", "elytron", "properties-realm", "a2a-test-realm"));
        ModelNode usersProps = new ModelNode();
        usersProps.get("path").set(USERS_FILE);
        usersProps.get("relative-to").set("jboss.server.config.dir");
        usersProps.get("plain-text").set(true);
        realmOp.get("users-properties").set(usersProps);
        ModelNode groupsProps = new ModelNode();
        groupsProps.get("path").set(ROLES_FILE);
        groupsProps.get("relative-to").set("jboss.server.config.dir");
        realmOp.get("groups-properties").set(groupsProps);
        executeOperation(managementClient, realmOp);

        // 2. Security domain
        ModelNode domainOp = Operations.createAddOperation(
                Operations.createAddress("subsystem", "elytron", "security-domain", "a2a-test-domain"));
        ModelNode realmRef = new ModelNode();
        realmRef.get("realm").set("a2a-test-realm");
        realmRef.get("role-decoder").set("groups-to-roles");
        ModelNode realms = new ModelNode().setEmptyList();
        realms.add(realmRef);
        domainOp.get("realms").set(realms);
        domainOp.get("default-realm").set("a2a-test-realm");
        domainOp.get("permission-mapper").set("default-permission-mapper");
        executeOperation(managementClient, domainOp);

        // 3. HTTP authentication factory
        ModelNode factoryOp = Operations.createAddOperation(
                Operations.createAddress("subsystem", "elytron", "http-authentication-factory", "a2a-test-http-auth"));
        factoryOp.get("security-domain").set("a2a-test-domain");
        factoryOp.get("http-server-mechanism-factory").set("global");
        ModelNode mechConfig = new ModelNode();
        mechConfig.get("mechanism-name").set("BASIC");
        ModelNode realmConfig = new ModelNode();
        realmConfig.get("realm-name").set("A2ATestRealm");
        ModelNode realmConfigs = new ModelNode().setEmptyList();
        realmConfigs.add(realmConfig);
        mechConfig.get("mechanism-realm-configurations").set(realmConfigs);
        ModelNode mechConfigs = new ModelNode().setEmptyList();
        mechConfigs.add(mechConfig);
        factoryOp.get("mechanism-configurations").set(mechConfigs);
        executeOperation(managementClient, factoryOp);

        // 4. Application security domain
        ModelNode appDomainOp = Operations.createAddOperation(
                Operations.createAddress("subsystem", "undertow", "application-security-domain", "a2a-test"));
        appDomainOp.get("http-authentication-factory").set("a2a-test-http-auth");
        executeOperation(managementClient, appDomainOp);
    }
}
