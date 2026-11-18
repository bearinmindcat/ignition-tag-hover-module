package io.github.bearinmindcat.taghover.client;

import java.util.List;

/**
 * Where a tag gets its value, from the tag's configuration on the Gateway.
 *
 * @param kind       the tag's value source
 * @param opcServer  OPC tags: the OPC server, or {@code null}
 * @param plcAddress OPC tags: the OPC item path without its {@code ns=1;s=} prefix, e.g. {@code [CP300]F8:23}
 * @param sourceTags reference and derived tags: the tag they point at; expression tags: the tags the expression
 *                   reads. Full paths.
 */
public record TagSource(Kind kind, String opcServer, String plcAddress, List<String> sourceTags) {

    public enum Kind { OPC, REFERENCE, DERIVED, EXPRESSION, MEMORY, QUERY, OTHER, PENDING }

    /** Being read from the Gateway. */
    public static final TagSource PENDING = new TagSource(Kind.PENDING, null, null, List.of());

    /** The OPC server built into Ignition, which most tags use, so it isn't worth showing. */
    static final String BUILT_IN_SERVER = "Ignition OPC UA Server";

    public TagSource {
        sourceTags = sourceTags == null ? List.of() : List.copyOf(sourceTags);
    }

    static TagSource opc(String server, String plcAddress) {
        return new TagSource(Kind.OPC, server, plcAddress, List.of());
    }

    static TagSource of(Kind kind, List<String> sourceTags) {
        return new TagSource(kind, null, null, sourceTags);
    }

    /** The OPC server worth naming: set, and not Ignition's own. */
    String notableServer() {
        return opcServer != null && !opcServer.isBlank() && !BUILT_IN_SERVER.equalsIgnoreCase(opcServer.trim())
            ? opcServer.trim()
            : null;
    }
}
