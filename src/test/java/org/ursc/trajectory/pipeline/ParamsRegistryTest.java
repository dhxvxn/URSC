package org.ursc.trajectory.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.ursc.trajectory.util.Json;

/**
 * Every run setting is exposed in the GUI: the parameter registry (form fields, info popovers,
 * help search) must cover exactly the leaves of {@link RunConfig}, each with an explanation.
 */
class ParamsRegistryTest {

    private static void leaves(final String prefix, final Object v, final TreeSet<String> out) {
        if (v instanceof Map) {
            for (final Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                leaves(prefix.isEmpty() ? String.valueOf(e.getKey()) : prefix + "." + e.getKey(), e.getValue(), out);
            }
        } else {
            out.add(prefix);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void registryCoversEveryRunSettingWithAnExplanation() throws Exception {
        final String json;
        try (InputStream in = getClass().getResourceAsStream("/gui/params.json")) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        final Map<String, Object> reg = Json.parseObject(json);
        final TreeSet<String> keys = new TreeSet<>();
        for (final Object o : (List<Object>) reg.get("params")) {
            final Map<String, Object> p = (Map<String, Object>) o;
            keys.add(String.valueOf(p.get("key")));
            assertFalse(String.valueOf(p.get("what")).isBlank(), p.get("key") + " has no explanation");
            assertFalse(String.valueOf(p.get("how")).isBlank(), p.get("key") + " has no 'how to choose'");
            assertTrue(((List<Object>) reg.get("groups")).contains(p.get("group")), p.get("key") + " has an unknown group");
            if ("enum".equals(p.get("type"))) {
                assertFalse(((List<Object>) p.get("options")).isEmpty(), p.get("key") + " enum without options");
            }
        }
        final TreeSet<String> settings = new TreeSet<>();
        leaves("", Json.parseObject(new RunConfig().toJson()), settings);
        assertEquals(settings, keys, "registry keys must match RunConfig settings exactly");
    }
}
