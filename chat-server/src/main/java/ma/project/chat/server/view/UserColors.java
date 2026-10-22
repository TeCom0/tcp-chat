package ma.project.chat.server.view;

import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Gives every user a random pastel background colour for the server's user list (spec 2.2).
 *
 * <p>Colours are a presentation concern, so this class lives in the view package (decision D8):
 * the model only knows names.
 *
 * <p>Pure random hues often produce two near-identical colours. Instead, the first hue is random and
 * each next one jumps by the golden angle (137.5 degrees): still a different palette every run, but
 * consecutive users always get clearly different colours. Lightness is kept high so dark text stays
 * readable.
 *
 * <p>Used only from the JavaFX Application Thread, so it needs no synchronisation.
 */
final class UserColors {

    private static final double GOLDEN_ANGLE = 137.508;

    private final Map<String, String> colourByName = new HashMap<>();
    private final Random random;
    private double nextHue;

    UserColors() {
        this(new Random());
    }

    /** Lets tests pass a seeded Random to get repeatable colours. */
    UserColors(Random random) {
        this.random = random;
        this.nextHue = random.nextDouble() * 360;
    }

    /**
     * The colour of this user as a CSS hex string such as "#d9f0e4". The same name (ignoring case)
     * keeps its colour for the whole session, even after leaving and coming back.
     */
    String colourFor(String name) {
        return colourByName.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> nextColour());
    }

    private String nextColour() {
        double hue = nextHue;
        nextHue = (nextHue + GOLDEN_ANGLE) % 360;
        double saturation = 0.22 + random.nextDouble() * 0.12; // 22-34%: soft, never neon
        Color c = Color.hsb(hue, saturation, 0.97);              // 97% brightness: pastel
        return String.format("#%02x%02x%02x",
                Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
    }
}
