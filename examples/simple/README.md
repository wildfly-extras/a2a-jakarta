# A2A Java SDK Server Jakarta - Simple Example
This example provides a simple client and server demonstrating the use of the [A2A Java SDK](https://github.com/a2aproject/a2a-java) in a Jakarta server. In this case we are using WildFly.

The example consists of a server, implementing an A2A Agent, as well as a client interacting with the agent. It is a very simple "Hello world" style application, and the focus of this example is on how to package your application for use with the various transports used by the A2A protocol. 

## Server
The server is a Jakarta EE application that exposes an A2A agent. It is located in the [`server`](./server) directory.

The server's [`pom.xml`](./server/pom.xml) has two profiles:

* `jsonrpc`: Provisions the server for use with the A2A protocol using JSON-RPC as the transport.
* `grpc`: Provisions the server for use with the A2A protocol using gRPC as the transport. This uses the gRPC feature pack which is at the 'preview' stability level.

Currently, the `grpc` and `rest` dependencies also include the `jsonrpc` ones, mainly because the agent card lookup depends on JSON-RPC.  

Note that we need to exclude some transitive dependencies of jars which are provided by WildFly. Other Jakarta runtimes may provide different dependencies and need different exclusions.

To create your own agent, you need to implement `AgentCard` and `AgentExecutor` beans.

* The `AgentCard` shows the capabilities of the agent, including supported transports and protocol version. This is the public version of the AgentCard, as this example does not provide an extended AgentCard. To provide an extended AgentCard, you produce it the same way, but with the @ExtendedAgentCard qualifier.
* The `AgentExecutor` handles the interaction with the LLM and task execution.

You can see our simple implementations at:

* [`SimpleExampleAgentCardProducer.java`](./server/src/main/java/org/wildfly/extras/a2a/examples/simple/SimpleExampleAgentCardProducer.java) - configures the agent card with transport URLs and protocol version. Note that this has logic to determine which transports exist on the classpath, which are then added to the supportedInterfaces of the AgentCard.
* [`SimpleExampleAgentExecutorProducer.java`](./server/src/main/java/org/wildfly/extras/a2a/examples/simple/SimpleExampleAgentExecutorProducer.java) - implements task execution using `AgentEmitter` to manage task lifecycle and artifacts

The `AgentExecutor` interface provides two methods:
* `execute(RequestContext context, AgentEmitter emitter)` - handles incoming requests, using the `AgentEmitter` to signal work progress, add artifacts, and complete tasks
* `cancel(RequestContext context, AgentEmitter emitter)` - handles cancellation requests for running tasks

In this example, the LLM interaction is mocked. For more advanced examples on how to interact with an LLM, please refer to the a2a-samples repository.

The application is packaged as a `.war` with the relevant dependencies for the targeted transport(s). For more details on the dependencies, please see [`server/pom.xml`](./server/pom.xml). Note that the deployment is renamed to `ROOT.war` in the `wildfly-maven-plugin` so that it is deployed under the root web context.

## Client
The client is a simple Java application that demonstrates how to interact with the server. The client implementation is in [`SimpleExampleClient.java`](./client/src/main/java/org/wildfly/extras/a2a/examples/simple/client/SimpleExampleClient.java).

When invoked from the command-line it takes the following parameters:
1) The name of the transport to use. This is either `JSONRPC` or `GRPC`.
2) The name of the user. This will be sent to the agent.

The client:
* Obtains the AgentCard from the server.
* Connects with the specified transport.
* Sends a message with the user's name.
* The server agent returns a `Task` object containing an `Artifact` with a `TextPart` with the text `Hello <name>`.

The client then prints the response to the console.

## Building and Running the Example
Run the following commands from the current directory (i.e. examples/simple under the root  checkout folder).

### JSON-RPC

1. Build the server

````shell
mvn clean install -Pjsonrpc -f server/pom.xml
````

2. Start the server

````shell
./server/target/wildfly/bin/standalone.sh
````

3. Run the client (in a different terminal)

````shell
mvn exec:java -f client/pom.xml -Prun-jsonrpc -Duser.name=Kabir
````

You should see the output: `Agent responds: Hello Kabir`

### gRPC

1. Build the server
````shell
mvn clean install -Pgrpc -f server/pom.xml
````

2. Start the server

````shell
./server/target/wildfly/bin/standalone.sh --stability=preview
````

3. Run the client

````shell
mvn exec:java -f client/pom.xml -Prun-grpc -Duser.name=Kabir
````

You should see the output: `Agent responds: Hello Kabir`

### HTTP+JSON/REST

1. Build the server
````shell
mvn clean install -Prest -f server/pom.xml
````

2. Start the server

````shell
./server/target/wildfly/bin/standalone.sh --stability=preview
````

3. Run the client

````shell
mvn exec:java -f client/pom.xml -Prun-rest -Duser.name=Kabir
````

You should see the output: `Agent responds: Hello Kabir`

