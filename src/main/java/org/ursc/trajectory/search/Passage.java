package org.ursc.trajectory.search;

/** A searchable chunk of a document (a section of a help page, doc or parameter entry). */
public final class Passage {
    public final String source;
    public final String title;
    public final String text;
    public double score;

    public Passage(final String source, final String title, final String text) {
        this.source = source;
        this.title = title;
        this.text = text;
    }
}
