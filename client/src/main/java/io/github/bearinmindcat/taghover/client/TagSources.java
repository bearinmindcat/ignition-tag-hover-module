package io.github.bearinmindcat.taghover.client;

import com.inductiveautomation.ignition.client.model.ClientContext;
import java.util.Collection;

/** Where tags get their values (PLC address, reference, expression), read from the Gateway and kept. */
public interface TagSources {

    /** Nothing is read; the tooltip shows tag paths only. */
    TagSources NONE = new TagSources() {
        @Override
        public TagSource get(String tagPath) {
            return null;
        }

        @Override
        public void request(Collection<String> tagPaths, Runnable onChange) {
            // Nothing to read.
        }
    };

    /**
     * What is known about the tag: its source, {@link TagSource#PENDING} while it is being read, or {@code null}
     * when it can't be read (or hasn't been asked for).
     */
    TagSource get(String tagPath);

    /**
     * Starts reading the tags that aren't known yet, or were read a while ago. Calls {@code onChange} on the event
     * dispatch thread when more is known. Never blocks.
     */
    void request(Collection<String> tagPaths, Runnable onChange);

    /** Reads from the Gateway through the client's (or Designer's) tag manager. */
    static TagSources forClient(ClientContext context) {
        return TagSourceLookup.forClient(context);
    }
}
