package me.mikidev06.filc_mod;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import me.mikidev06.filc_mod.api.FilcJson;

/**
 * Loads the API responses captured from filc.petrik.hu into test resources.
 * Testing against real payloads rather than hand-written ones is the point:
 * every field name, null and nesting quirk here came off the wire.
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static String raw(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            assertNotNull(in, "missing fixture: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The fixture's payload with the success envelope removed. */
    public static JsonElement data(String name) {
        return FilcJson.unwrap(raw(name), 200, -1);
    }
}
