package ma.project.chat.client.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Where the client connects to.
 *
 * <p>Priority, highest first (decision D1):
 * <ol>
 *   <li>command-line arguments: {@code java TCPClient <ServerIPAddress> <PortNumber>} (spec 4.1);</li>
 *   <li>{@code client.properties} in the current working directory;</li>
 *   <li>{@code client.properties} in the folder that contains the running JAR (spec 3.3, no recompiling);</li>
 *   <li>{@code client.properties} bundled inside the JAR;</li>
 *   <li>built-in defaults: localhost, port 3001.</li>
 * </ol>
 *
 * @param host   server host name or IP address
 * @param port   server TCP port (1-65535)
 * @param source where these values came from, for messages such as "Connecting to ... (from ...)"
 */
public record ClientConfig(String host, int port, String source) {

    public static final String FILE_NAME = "client.properties";
    public static final String KEY_IP = "server.ip";
    public static final String KEY_PORT = "server.port";
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 3001;
    public static final String USAGE = "Usage: java TCPClient <ServerIPAddress> <PortNumber>   e.g. java TCPClient localhost 3001";

    public ClientConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Server address must not be empty");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535, got " + port);
        }
    }

    /**
     * Applies the priority rules above.
     *
     * @param args the program arguments: none, or exactly host and port
     * @throws IllegalArgumentException with {@link #USAGE} when the arguments are malformed
     */
    public static ClientConfig resolve(String[] args) {
        if (args.length == 2) {
            return new ClientConfig(args[0].strip(), parsePort(args[1], "the command line"), "command line");
        }
        if (args.length != 0) {
            throw new IllegalArgumentException(USAGE);
        }
        return loadFile();
    }

    /** Reads client.properties: working directory, JAR folder, classpath, else defaults. */
    public static ClientConfig loadFile() {
        for (Path candidate : externalCandidates()) {
            if (Files.isRegularFile(candidate)) {
                try (Reader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
                    Properties props = new Properties();
                    props.load(reader);
                    return fromProperties(props, candidate.toAbsolutePath().normalize().toString());
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read " + candidate.toAbsolutePath(), e);
                }
            }
        }
        try (InputStream in = ClientConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                return fromProperties(props, "classpath:" + FILE_NAME);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read bundled " + FILE_NAME, e);
        }
        return new ClientConfig(DEFAULT_HOST, DEFAULT_PORT, "built-in defaults");
    }

    /** Builds a config from loaded properties; missing keys fall back to the defaults. */
    public static ClientConfig fromProperties(Properties props, String source) {
        String host = props.getProperty(KEY_IP, DEFAULT_HOST).strip();
        String rawPort = props.getProperty(KEY_PORT, String.valueOf(DEFAULT_PORT));
        return new ClientConfig(host, parsePort(rawPort, source), source);
    }

    /** Files to try before the bundled copy: working directory, then the JAR's own folder. */
    private static List<Path> externalCandidates() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of(FILE_NAME));
        Path jarFolder = jarFolder();
        if (jarFolder != null) {
            candidates.add(jarFolder.resolve(FILE_NAME));
        }
        return candidates;
    }

    /** The folder containing the running JAR, or null when running from target/classes. */
    static Path jarFolder() {
        try {
            Path location = Path.of(ClientConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(location) ? location.getParent() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static int parsePort(String raw, String where) {
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port must be a number, got '" + raw.strip() + "' in " + where);
        }
    }
}
