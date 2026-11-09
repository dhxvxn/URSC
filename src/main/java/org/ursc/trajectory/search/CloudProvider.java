package org.ursc.trajectory.search;

import java.util.List;

/**
 * Priority 3: a cloud model reached with an API key (e.g. Claude, OpenAI/Codex or any
 * OpenAI-compatible endpoint). Requires internet, so it is never used on an air-gapped
 * network. Disabled in this release; the key is read from the environment variable named in
 * the configuration and is never written to disk.
 */
final class CloudProvider implements AnswerProvider {

    private final AiConfig.Cloud cfg;

    CloudProvider(final AiConfig.Cloud cfg) {
        this.cfg = cfg;
    }

    @Override
    public String name() {
        return "cloud";
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String status() {
        return cfg.enabled ? "configured (" + cfg.provider + ") but not implemented in this release"
                           : "disabled (planned: Claude / OpenAI-compatible API key; needs internet)";
    }

    @Override
    public Answer answer(final String question, final List<Passage> passages) {
        throw new UnsupportedOperationException("cloud answers are not implemented in this release");
    }
}
