package org.a2aproject.sdk.itk;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        String httpPort = "10102";
        String grpcPort = "11002";

        for (int i = 0; i < args.length; i++) {
            if ("--httpPort".equals(args[i]) && i + 1 < args.length) {
                httpPort = args[++i];
            } else if ("--grpcPort".equals(args[i]) && i + 1 < args.length) {
                grpcPort = args[++i];
            }
        }

        Path scriptPath = Path.of("target", "wildfly", "bin", "standalone.sh");
        if (!scriptPath.toFile().exists()) {
            System.err.println("standalone.sh not found at " + scriptPath.toAbsolutePath());
            System.exit(1);
        }

        Path tmpDir = Path.of("target", "wildfly", "standalone", "tmp");
        if (Files.exists(tmpDir)) {
            new ProcessBuilder("chmod", "-R", "u+rwx", tmpDir.toString())
                    .inheritIO()
                    .start()
                    .waitFor();
            new ProcessBuilder("rm", "-rf", tmpDir.toString())
                    .inheritIO()
                    .start()
                    .waitFor();
        }

        List<String> command = new ArrayList<>();
        command.add(scriptPath.toAbsolutePath().toString());
        command.add("-Djboss.http.port=" + httpPort);
        command.add("-Djboss.grpc.port=" + grpcPort);
        command.add("--stability=preview");

        ProcessBuilder pb = new ProcessBuilder(command)
                .inheritIO()
                .directory(new File("."));

        Process process = pb.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> process.destroyForcibly()));

        System.exit(process.waitFor());
    }
}
