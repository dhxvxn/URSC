package org.ursc.trajectory.search;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.ursc.trajectory.util.Json;

/** Answer-provider settings, stored as {@code ai.json} in the workspace (never holds secrets). */
public final class AiConfig {

    public Ollama ollama = new Ollama();
    public Cloud cloud = new Cloud();

    public static final class Ollama {
        public boolean enabled = false;
        public String url = "http://127.0.0.1:11434";
        public String model = "";
    }

    public static final class Cloud {
        public boolean enabled = false;
        /** anthropic | openai-compatible */
        public String provider = "anthropic";
        public String endpoint = "";
        public String model = "";
        /** Name of the environment variable holding the API key (the key itself is never stored). */
        public String apiKeyEnv = "URSC_AI_API_KEY";
    }

    static AiConfig load(final Path workspace) throws IOException {
        final Path f = workspace.resolve("ai.json");
        final AiConfig c = new AiConfig();
        if (Files.exists(f)) {
            Json.bind(Json.parseObject(Files.readString(f)), c);
        } else {
            Files.writeString(f, Json.write(c));
        }
        return c;
    }
}
