package ma.project.chat.client.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameColorsTest {

    @Test
    void sameNameAlwaysGetsTheSameColourIgnoringCase() {
        assertEquals(NameColors.colourFor("Sara"), NameColors.colourFor("sara"));
        assertEquals(NameColors.colourFor("Omar"), NameColors.colourFor("Omar"));
    }

    @Test
    void coloursAreDarkEnoughToReadOnALightBubble() {
        for (String name : new String[]{"Sara", "Omar", "Youness", "Guest#1", "a", "Zineb", "Karim"}) {
            String hex = NameColors.colourFor(name);
            assertTrue(hex.matches("#[0-9a-f]{6}"), hex);
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            double luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b; // perceived brightness, 0-255
            assertTrue(luminance < 170, name + " -> " + hex + " is too light (" + luminance + ")");
        }
    }
}
