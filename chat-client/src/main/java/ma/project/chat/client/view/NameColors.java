package ma.project.chat.client.view;

import javafx.scene.paint.Color;

import java.util.Locale;

/**
 * Gives each sender name a stable text colour in the chat, like group chats on phones.
 *
 * <p>Deterministic on purpose (unlike the server's random colours): the colour is computed from the
 * name, so "Sara" has the same colour in every client window and after every reconnect.
 */
final class NameColors {

    private NameColors() {
    }

    /** A dark-enough CSS hex colour for this name, readable on a light grey bubble. */
    static String colourFor(String name) {
        int hash = name.toLowerCase(Locale.ROOT).hashCode();
        double hue = Math.floorMod(hash * 31, 360);  // 0..359, spread around the colour wheel
        Color c = Color.hsb(hue, 0.70, 0.62);         // saturated but darkish: good contrast on light grey
        return String.format("#%02x%02x%02x",
                Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
    }
}
