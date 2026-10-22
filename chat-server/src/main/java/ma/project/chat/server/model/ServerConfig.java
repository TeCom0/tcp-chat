package ma.project.chat.server.model;

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
 * Network settings of the server, read at runtime from {@code server.properties}.
 *
 * <p>Lookup order (first found wins):
 * <ol>
 *   <li>{@code server.properties} in the current working directory;</li>
 *   <li>{@code server.properties} in the folder that contains the running JAR (edit it, restart, no
 *       recompiling: spec 3.3);</li>
 *   <li>{@code server.properties} bundled inside the JAR (src/main/resources);</li>
 *   <li>built-in defaults: 0.0.0.0, port 3001.</li>
 * </ol>
 *
 * @param ip     address to listen on (0.0.0.0 = all network interfaces)
 * @param port   TCP port (1-65535; 0 = let the OS pick a free one, used by tests)
 * @param source human-readable origin of these values, shown in the log
 */
public record ServerConfig(String ip, int port, String source) {

    public static final String FILE_NAME = "server.properties";
    public static final String KEY_IP = "server.ip";
    public static final String KEY_PORT = "server.port";
    public static final String DEFAULT_IP = "0.0.0.0";
    public static final int DEFAULT_PORT = 3001;

    /** Compact constructor: runs on every creation and rejects impossible values early. */
    public ServerConfig {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException(KEY_IP + " must not be empty");
        }
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException(KEY_PORT + " must be between 0 and 65535, got " + port);
        }
    }

    /** Loads the configuration using the lookup order described above. */
    public static ServerConfig load() {
        for (Path candidate : externalCandidates()) {
            if (Files.isRegularFile(candidate)) {
                try (Reader reader = Files.newBufferedReader(candidate, StandardCharsets.UTF_8)) {
                    return fromProperties(read(reader), candidate.toAbsolutePath().normalize().toString());
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read " + candidate.toAbsolutePath(), e);
                }
            }
        }
        try (InputStream in = ServerConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                return fromProperties(props, "classpath:" + FILE_NAME);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read bundled " + FILE_NAME, e);
        }
        return new ServerConfig(DEFAULT_IP, DEFAULT_PORT, "built-in defaults");
    }

    /** Builds a config from already-loaded properties; missing keys fall back to the defaults. */
    public static ServerConfig fromProperties(Properties props, String source) {
        String ip = props.getProperty(KEY_IP, DEFAULT_IP).strip();
        String rawPort = props.getProperty(KEY_PORT, String.valueOf(DEFAULT_PORT)).strip();
        int port;
        try {
            port = Integer.parseInt(rawPort);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(KEY_PORT + " must be a number, got '" + rawPort + "' in " + source);
        }
        return new ServerConfig(ip, port, source);
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

    /**
     * The folder containing the running JAR, or null when not running from a JAR (e.g. from
     * target/classes in IntelliJ). getCodeSource().getLocation() is where this class was loaded from.
     */
    static Path jarFolder() {
        try {
            Path location = Path.of(ServerConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(location) ? location.getParent() : null; // a file = a JAR, a folder = classes
        } catch (Exception e) {
            return null; // unusual class loader or security restrictions: just skip this location
        }
    }

    private static Properties read(Reader reader) throws IOException {
        Properties props = new Properties();
        props.load(reader);
        return props;
    }
}
