package org.ursc.trajectory.search;

import java.util.List;

/**
 * Priority 2: a local model served by Ollama (fully offline once the model is installed).
 * Wired into configuration, the provider chain and the GUI, but intentionally disabled in
 * this release: enabling it only requires implementing {@link #answer} as a POST of the
 * question and cited passages to {@code url + "/api/generate"} with the configured model.
 */
final class OllamaProvider implements AnswerProvider {

    private final AiConfig.Ollama cfg;

    OllamaProvider(final AiConfig.Ollama cfg) {
        this.cfg = cfg;
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String status() {
        return cfg.enabled ? "configured (" + cfg.model + " at " + cfg.url + ") but not implemented in this release"
                           : "disabled (planned: local model via Ollama)";
    }

    @Override
    public Answer answer(final String question, final List<Passage> passages) {
        throw new UnsupportedOperationException("Ollama answers are not implemented in this release");
    }
}
