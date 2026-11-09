package org.ursc.trajectory.search;

import java.util.List;

/**
 * Turns retrieved passages into an answer. Providers are tried in priority order:
 * offline extractive (always available), local Ollama model, cloud API. Only the retrieved
 * passages are ever given to a generator, and answers cite them.
 */
public interface AnswerProvider {

    /** Answer text with [n] citations into the passage list. */
    final class Answer {
        public final String provider;
        public final String text;

        public Answer(final String provider, final String text) {
            this.provider = provider;
            this.text = text;
        }
    }

    String name();

    /** Whether the provider is enabled and reachable right now. */
    boolean available();

    /** Human-readable state for the GUI (e.g. "disabled", "not configured"). */
    String status();

    Answer answer(String question, List<Passage> passages) throws Exception;
}
