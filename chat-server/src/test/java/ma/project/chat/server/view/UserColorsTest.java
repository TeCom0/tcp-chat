package ma.project.chat.server.view;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserColorsTest {

    @Test
    void sameNameKeepsItsColourIgnoringCase() {
        UserColors colours = new UserColors(new Random(42));
        String first = colours.colourFor("Sara");
        colours.colourFor("Omar");
        assertEquals(first, colours.colourFor("sara"));
    }

    @Test
    void differentUsersGetDifferentColours() {
        UserColors colours = new UserColors(new Random(7));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            seen.add(colours.colourFor("user" + i));
        }
        assertEquals(20, seen.size());
    }

    @Test
    void coloursArePastelHexStrings() {
        UserColors colours = new UserColors(new Random(1));
        for (int i = 0; i < 20; i++) {
            String hex = colours.colourFor("u" + i);
            assertTrue(hex.matches("#[0-9a-f]{6}"), hex);
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            assertTrue(Math.max(r, Math.max(g, b)) > 230, "light enough for dark text: " + hex);
            assertTrue(Math.min(r, Math.min(g, b)) > 140, "not too saturated: " + hex);
        }
    }
}
