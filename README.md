# A2A Java SDK for Jakarta Servers

This is the integration of the [A2A Java SDK](https://github.com/a2aproject/a2a-java) for use in Jakarta servers. It is currently tested on **WildFly**, but it should be usable in other compliant Jakarta servers such as Tomcat, Jetty, and OpenLiberty. For Quarkus, use the reference implementation in the [A2A Java SDK](https://github.com/a2aproject/a2a-java) project.

This implementation is aligned with **A2A Protocol Specification 1.0** and uses **A2A Java SDK 1.0.0.CR1**.

For more information about the A2A protocol, see [here](https://github.com/a2aproject/A2A).

## Getting Started

To use the A2A Java SDK in your application, you will need to package it as a `.war` file. This can be done with your standard build tool.

### Packaging your application

The key to enabling A2A in your Java application is to correctly package it. Here are the general steps you need to follow:

1.  **Create a standard `.war` archive:** Your project should be configured to produce a `.war` file. This is a standard format for web applications in Java and is supported by all major runtimes.

2.  **Provide implementations for `AgentExecutor` and `AgentCard`:** The A2A SDK requires you to provide your own implementations of the `AgentExecutor` and `AgentCard` interfaces. These are the core components that define the behavior of your agent. You can find more information about them in the [A2A Java SDK documentation](https://github.com/a2aproject/a2a-java).

3.  **Manage Dependencies:** Your `.war` file must contain all necessary libraries in the `/WEB-INF/lib` directory. However, some libraries may already be provided by the application server itself.

    * **Using the BOM:** To simplify dependency management, you can use the `a2a-java-sdk-bom` (Bill of Materials) which manages versions of all A2A SDK dependencies. Add this to your `<dependencyManagement>` section:
      ```xml
      <dependency>
          <groupId>org.a2aproject.sdk</groupId>
          <artifactId>a2a-java-sdk-bom</artifactId>
          <version>${version.sdk}</version>
          <type>pom</type>
          <scope>import</scope>
      </dependency>
      ```

    * **Bundling Dependencies:** For libraries not provided by the server, you must bundle them inside your `.war`.

    * **Provided Dependencies:** To avoid conflicts and reduce the size of your archive, you should exclude libraries that your target runtime already provides. For example, **WildFly** includes the **Jackson** libraries, so you do not need to package them in your application. Check the documentation for your specific runtime (Tomcat, Jetty, OpenLiberty, etc.) to see which dependencies are provided.

### Example

The [tck/pom.xml](./tck/pom.xml) is a good example of how to package an A2A application. Your application can support JSON-RPC, gRPC, REST, or any combination of these. In this case, the application is deployed in WildFly, so the dependencies included in the `.war` are tailored to what WildFly provides.

In the `tck/pom.xml` we enable JSON-RPC, gRPC, and REST, and have the following dependencies:

* `org.wildfly.a2a:a2a-jakarta-jsonrpc` and `org.wildfly.a2a:a2a-jakarta-jsonrpc-web` - these are the dependencies for **JSON-RPC** support. The `-web` module contains the JAX-RS resources and transitively pulls in the base module. They transitively pull in all the dependencies from the A2A Java SDK project.
    * Since some of these dependencies are provided by WildFly already, we exclude those so they do not become part of the `.war`, in order to avoid inconsistencies.
* `org.wildfly.a2a:a2a-jakarta-grpc` - this is the dependency for **gRPC** support.
    * We exclude the gRPC core libraries (`io.grpc` and `com.google.protobuf:protobuf-java`). This is because when deploying to WildFly with gRPC support, the server is provisioned with the WildFly gRPC feature-pack, which already provides these libraries. Including them in the `.war` would lead to conflicts.
* `org.wildfly.a2a:a2a-jakarta-rest` and `org.wildfly.a2a:a2a-jakarta-rest-web` - these are the dependencies for **REST** (HTTP+JSON) support. The `-web` module contains the JAX-RS resources and transitively pulls in the base module.
* `jakarta.ws.rs:jakarta.ws.rs-api` - this is not part of the dependencies brought in via the A2A dependencies but is needed to compile the TCK module. Since it is provided by WildFly, we make the scope `provided` so it is not included in the `.war`.
* `org.a2aproject.sdk:a2a-java-sdk-tck-sut` - this is the application, which contains the `AgentExecutor` and `AgentCard` implementations for the TCK. In your case, they will most likely be implemented in the project you use to create the `.war`.
    * In this case we exclude all transitive dependencies, since we are doing the main dependency management via the transport-specific dependencies above.

If you are deploying to WildFly and want to use gRPC, you will also need to provision the server with the gRPC feature pack. You can see how this is done in the `wildfly-maven-plugin` configuration in the `tck/pom.xml`. Since the gRPC subsystem and feature pack are currently at the `preview` stability level, you will need to start the server with the `--stability=preview` argument.

There are also some [examples](./examples/README.md) that show how to package an application selecting each transport. 

## v0.3 Protocol Compatibility

This project includes support for the A2A Protocol v0.3, both as a standalone deployment and as a multiversion deployment alongside v1.0. The v0.3 compatibility layer converts incoming v0.3 requests to v1.0 format and delegates to a single `AgentExecutor`, so you do not need a separate executor for v0.3 support.

### Standalone v0.3

To deploy an application that only supports v0.3, use the `compat-0.3` modules instead of the v1.0 ones. The [compat-0.3/tck/pom.xml](./compat-0.3/tck/pom.xml) is a good example. The dependencies follow the same pattern as v1.0, but with the `compat-0.3` prefix:

* `org.wildfly.a2a:a2a-jakarta-compat-0.3-jsonrpc` and `a2a-jakarta-compat-0.3-jsonrpc-web` for **JSON-RPC**
* `org.wildfly.a2a:a2a-jakarta-compat-0.3-grpc` for **gRPC** (same exclusions as v1.0)
* `org.wildfly.a2a:a2a-jakarta-compat-0.3-rest` and `a2a-jakarta-compat-0.3-rest-web` for **REST**

Your application must provide an `AgentExecutor` and an `AgentCard_v0_3` (instead of `AgentCard`).

### Multiversion (v0.3 + v1.0)

To support both protocol versions in the same deployment, activate the `multi-version` Maven profile. Both the `tck/pom.xml` and `compat-0.3/tck/pom.xml` have a `multi-version` profile that demonstrates this.

The multiversion setup adds the `compat-0.3-multiversion-jsonrpc` and `compat-0.3-multiversion-rest` modules, which handle dispatching requests to the correct protocol version. The standalone `-web` JAX-RS modules for JSON-RPC are excluded (set to `provided` scope) since the multiversion modules replace them.

The approach differs depending on which version is your primary:

* **Primary v1.0** (see `tck/pom.xml` `multi-version` profile): Your application provides a v1.0 `AgentCard` as the primary agent card. You must also provide a minimal `AgentCard_v0_3` to satisfy CDI injection for the v0.3 handlers — see [StubAgentCardProducer_v0_3.java](./tck/src/multi-version/java/org/wildfly/extras/a2a/server/jakarta/tck/StubAgentCardProducer_v0_3.java) for an example.
* **Primary v0.3** (see `compat-0.3/tck/pom.xml` `multi-version` profile): Your application provides a v0.3 `AgentCard_v0_3` as the primary agent card. You must also produce a v1.0 `AgentCard` — this can be derived from the v0.3 card. See [DerivedAgentCardProducer.java](./compat-0.3/tck/src/multi-version/java/org/wildfly/extras/a2a/server/jakarta/compat03/tck/DerivedAgentCardProducer.java) for an example that converts `AgentCard_v0_3` to `AgentCard`.

In both cases, only a single `AgentExecutor` is needed — the v0.3 conversion layer handles protocol translation automatically.

### Agent Card Compatibility

When serving both protocol versions, the v1.0 `AgentCard` is the one served to clients. To ensure v0.3 clients can parse it, the card must include backward-compatibility fields such as `url`, `preferredTransport`, and `additionalInterfaces`. You can set these manually on the `AgentCard.Builder`, or use the `Compat03Fields.addCompat03FieldsIfAvailable()` utility to add them — see [DerivedAgentCardProducer.java](./compat-0.3/tck/src/multi-version/java/org/wildfly/extras/a2a/server/jakarta/compat03/tck/DerivedAgentCardProducer.java) for an example.

For full details on these fields, see the [Making the v1.0 Agent Card Compatible with v0.3 Clients](https://github.com/a2aproject/a2a-java#making-the-v10-agent-card-compatible-with-v03-clients) section of the A2A Java SDK README.

## Running the TCK

The project includes a TCK (Technology Compatibility Kit) that you can use to test the integration with WildFly. 

To run the TCK, build the full project
```bash
mvn clean install -DskipTests
```

You now have a server provisioned with the `.war` deployed in the `tck/target/wildfly` folder.

We can start the server using the following command:

```bash
 SUT_JSONRPC_URL=http://localhost:8080 SUT_GRPC_URL=http://localhost:9555 SUT_REST_URL=http://localhost:8080 tck/target/wildfly/bin/standalone.sh --stability=preview
```

`--stability=preview` is needed since the TCK server is provisioned with the gRPC subsystem, which is currently at the `preview` stability level.

The `SUT_JSONRPC_URL`, `SUT_GRPC_URL`, and `SUT_REST_URL` are used by the TCK server's `AgentCardProducer` to specify the transports supported by the server agent.

Once the server is up and running, run the TCK with the instructions in [a2aproject/a2a-tck](https://github.com/a2aproject/a2a-tck).

**Note:** This implementation targets **A2A Protocol Specification 1.0.0**. Make sure you check out the corresponding 1.0.0 tag of `a2aproject/a2a-tck`.

Be sure to set `TCK_STREAMING_TIMEOUT=4.0` when running the TCK to ensure the tests wait long enough to receive the events for streaming methods.

Then to run the TCK, run the following command from the clone of [a2aproject/a2a-tck](https://github.com/a2aproject/a2a-tck):

```shell
./run_tck.py --sut-url http://localhost:8080 --category all --transports jsonrpc,grpc,rest --compliance-report report.json
```

## Running the v0.3 TCK

Build the project:
```bash
mvn clean install -DskipTests
```

Start the v0.3 TCK server:
```bash
SUT_JSONRPC_URL=http://localhost:8080 SUT_GRPC_URL=http://localhost:9555 SUT_REST_URL=http://localhost:8080 compat-0.3/tck/target/wildfly/bin/standalone.sh --stability=preview
```

Then run the v0.3 TCK suite from your clone of [a2aproject/a2a-tck](https://github.com/a2aproject/a2a-tck), using the appropriate tag/branch for v0.3.

## Running the Multiversion TCK

The multiversion build enables both v0.3 and v1.0 protocol support in the same server. Build with the `multi-version` profile:

```bash
mvn clean install -DskipTests -Pmulti-version
```

This produces two multiversion WAR deployments:

### v0.3 TCK against multiversion server

Start the multiversion server from `compat-0.3/tck/`:
```bash
SUT_JSONRPC_URL=http://localhost:8080 SUT_GRPC_URL=http://localhost:9555 SUT_REST_URL=http://localhost:8080 compat-0.3/tck/target/wildfly/bin/standalone.sh --stability=preview
```
Then run the **v0.3 TCK** suite against it.

### v1.0 TCK against multiversion server

Start the multiversion server from `tck/`:
```bash
SUT_JSONRPC_URL=http://localhost:8080 SUT_GRPC_URL=http://localhost:9555 SUT_REST_URL=http://localhost:8080 tck/target/wildfly/bin/standalone.sh --stability=preview
```
Then run the **v1.0 TCK** suite against it.
