package io.github.bearinmindcat.taghover.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bearinmindcat.taghover.client.TagSource.Kind;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TagSourceLookupTest {

    private final FakeTagConfig gateway = new FakeTagConfig();
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final AtomicInteger changes = new AtomicInteger();
    private final TagSourceLookup lookup = new TagSourceLookup(gateway, () -> "default", now::get, Runnable::run);

    @Test
    void opcTagShowsItsPlcAddress() {
        gateway.opc("[default]CP300/FLOATS/23", "ns=1;s=[CP300]F8:23");

        lookup.request(List.of("[default]CP300/FLOATS/23"), changes::incrementAndGet);

        TagSource s = lookup.get("[default]CP300/FLOATS/23");
        assertEquals(Kind.OPC, s.kind());
        assertEquals("[CP300]F8:23", s.plcAddress());
        assertNull(s.notableServer(), "Ignition's own server isn't worth naming");
        assertEquals(1, changes.get());
    }

    @Test
    void otherOpcServersAreNamed() {
        gateway.opc("[default]Flow", "Kepware", "ns=2;s=Channel1.CP400.F8_119");

        lookup.request(List.of("[default]Flow"), changes::incrementAndGet);

        assertEquals("Kepware", lookup.get("[default]Flow").notableServer());
        assertEquals("Channel1.CP400.F8_119", lookup.get("[default]Flow").plcAddress());
    }

    @Test
    void pendingUntilTheGatewayAnswers() {
        gateway.opc("[default]CP300/FLOATS/23", "ns=1;s=[CP300]F8:23");
        gateway.hold = true;

        lookup.request(List.of("[default]CP300/FLOATS/23"), changes::incrementAndGet);
        assertSame(TagSource.PENDING, lookup.get("[default]CP300/FLOATS/23"));
        assertEquals(0, changes.get());

        gateway.release();
        assertEquals("[CP300]F8:23", lookup.get("[default]CP300/FLOATS/23").plcAddress());
        assertEquals(1, changes.get());
    }

    @Test
    void eachTagIsReadOnceUntilItGoesStale() {
        gateway.opc("[default]A", "ns=1;s=[CP1]N7:0");

        lookup.request(List.of("[default]A"), changes::incrementAndGet);
        lookup.request(List.of("[default]a", "[default]A"), changes::incrementAndGet); // Tag paths ignore case.
        assertEquals(1, gateway.reads.size());

        now.addAndGet(TagSourceLookup.FRESH_MILLIS + 1);
        lookup.request(List.of("[default]A"), changes::incrementAndGet);
        assertEquals(2, gateway.reads.size());
    }

    @Test
    void readsAreBatched() {
        gateway.opc("[default]A", "ns=1;s=[CP1]N7:0").opc("[default]B", "ns=1;s=[CP1]N7:1");

        lookup.request(List.of("[default]A", "[default]B"), changes::incrementAndGet);

        assertEquals(1, gateway.reads.size());
        assertEquals(2 * TagSourceLookup.PROPERTIES.size(), gateway.reads.get(0).size());
    }

    @Test
    void referenceTagsAreFollowedToTheirTag() {
        gateway.reference("[default]TagHoverTest/Tank1HighAlarm", "[.]PLC/HighSwitch")
            .opc("[default]TagHoverTest/PLC/HighSwitch", "ns=1;s=[CP100]Global.F8.F8[17]");

        lookup.request(List.of("[default]TagHoverTest/Tank1HighAlarm"), changes::incrementAndGet);

        TagSource ref = lookup.get("[default]TagHoverTest/Tank1HighAlarm");
        assertEquals(Kind.REFERENCE, ref.kind());
        assertEquals(List.of("[default]TagHoverTest/PLC/HighSwitch"), ref.sourceTags());
        assertEquals("[CP100]Global.F8.F8[17]", lookup.get(ref.sourceTags().get(0)).plcAddress());
        assertEquals(2, changes.get(), "redrawn once per answer");
    }

    @Test
    void expressionTagsListTheTagsTheyRead() {
        gateway.expression("[default]Plant/TotalEffluent", "{[.]FIT704} + {[~]Plant/FIT718} + tag('[.]../Other')")
            .opc("[default]Plant/FIT704", "ns=1;s=[CP400]Global.F8.F8[119]")
            .opc("[default]Plant/FIT718", "ns=1;s=[CP400]Global.F8.F8[121]")
            .memory("[default]Other");

        lookup.request(List.of("[default]Plant/TotalEffluent"), changes::incrementAndGet);

        TagSource expr = lookup.get("[default]Plant/TotalEffluent");
        assertEquals(Kind.EXPRESSION, expr.kind());
        assertEquals(List.of("[default]Plant/FIT704", "[default]Plant/FIT718", "[default]Other"), expr.sourceTags());
        assertEquals("[CP400]Global.F8.F8[121]", lookup.get("[default]Plant/FIT718").plcAddress());
        assertEquals(Kind.MEMORY, lookup.get("[default]Other").kind());
    }

    @Test
    void followingStopsAfterOneStep() {
        gateway.reference("[default]R1", "[default]R2").reference("[default]R2", "[default]R3").memory("[default]R3");

        lookup.request(List.of("[default]R1"), changes::incrementAndGet);

        assertEquals(List.of("[default]R1", "[default]R2"), gateway.tagsRead());
    }

    @Test
    void otherKindsOfTags() {
        gateway.memory("[default]M").query("[default]Q").derived("[default]D", "[default]M")
            .container("[default]Motor1", "UdtInstance").container("[default]PLC", "Folder");

        lookup.request(List.of("[default]M", "[default]Q", "[default]D", "[default]Motor1", "[default]PLC",
            "[default]Missing"), changes::incrementAndGet);

        assertEquals(Kind.MEMORY, lookup.get("[default]M").kind());
        assertEquals(Kind.QUERY, lookup.get("[default]Q").kind());
        assertEquals(Kind.DERIVED, lookup.get("[default]D").kind());
        // Ignition says "memory" for these, but they aren't memory tags.
        assertNull(lookup.get("[default]Motor1"), "a UDT instance has no source of its own");
        assertNull(lookup.get("[default]PLC"), "a folder has no source");
        assertNull(lookup.get("[default]Missing"), "a missing tag has no source");
    }

    @Test
    void aGatewayFailureLeavesTagPathsOnlyAndRetriesLater() {
        gateway.opc("[default]A", "ns=1;s=[CP1]N7:0");
        gateway.fail = true;

        lookup.request(List.of("[default]A"), changes::incrementAndGet);
        assertNull(lookup.get("[default]A"));

        gateway.fail = false;
        lookup.request(List.of("[default]A"), changes::incrementAndGet);
        assertEquals(1, gateway.reads.size(), "waits before trying again");

        now.addAndGet(TagSourceLookup.RETRY_MILLIS + 1);
        lookup.request(List.of("[default]A"), changes::incrementAndGet);
        assertEquals("[CP1]N7:0", lookup.get("[default]A").plcAddress());
    }

    @Test
    void aFailedRefreshKeepsWhatWasKnown() {
        gateway.opc("[default]A", "ns=1;s=[CP1]N7:0");
        lookup.request(List.of("[default]A"), changes::incrementAndGet);

        now.addAndGet(TagSourceLookup.FRESH_MILLIS + 1);
        gateway.fail = true;
        lookup.request(List.of("[default]A"), changes::incrementAndGet);

        assertEquals("[CP1]N7:0", lookup.get("[default]A").plcAddress());
    }

    @Test
    void unresolvedIndirectPatternsAreNotRead() {
        lookup.request(List.of("[default]Motors/{1}/Amps"), changes::incrementAndGet);

        assertTrue(gateway.reads.isEmpty());
        assertNull(lookup.get("[default]Motors/{1}/Amps"));
    }

    @Test
    void theDefaultProviderIsFilledIn() {
        gateway.opc("[default]CP300/FLOATS/23", "ns=1;s=[CP300]F8:23");

        lookup.request(List.of("[~]CP300/FLOATS/23"), changes::incrementAndGet);

        assertEquals(List.of("[default]CP300/FLOATS/23"), gateway.tagsRead());
        assertEquals("[CP300]F8:23", lookup.get("[~]CP300/FLOATS/23").plcAddress());
    }

    @Test
    void pathsWithNoProviderUseTheProjectDefault() {
        // How the plant's bindings are written: CP100/FLOATS/3, no [provider].
        gateway.opc("[plant]CP100/FLOATS/3", "ns=1;s=[CP100]Global.F8.F8[3]");
        TagSourceLookup plant = new TagSourceLookup(gateway, () -> "plant", now::get, Runnable::run);

        plant.request(List.of("CP100/FLOATS/3", "CP100/FLOATS/3.formatString"), changes::incrementAndGet);

        assertEquals(List.of("[plant]CP100/FLOATS/3"), gateway.tagsRead());
        assertEquals("[CP100]Global.F8.F8[3]", plant.get("CP100/FLOATS/3").plcAddress());
        assertEquals("[CP100]Global.F8.F8[3]", plant.get("CP100/FLOATS/3.formatString").plcAddress());
    }

    @Test
    void withNoDefaultProviderKnownItTriesDefault() {
        gateway.opc("[default]CP100/FLOATS/3", "ns=1;s=[CP100]Global.F8.F8[3]");
        TagSourceLookup unknown = new TagSourceLookup(gateway, () -> null, now::get, Runnable::run);

        unknown.request(List.of("CP100/FLOATS/3"), changes::incrementAndGet);

        assertEquals("[CP100]Global.F8.F8[3]", unknown.get("CP100/FLOATS/3").plcAddress());
    }

    @Test
    void propertyPathsReadTheirTagOnce() {
        gateway.opc("[default]Tank1Level", "ns=1;s=[CP300]F8:23");

        lookup.request(List.of("[default]Tank1Level.formatString", "[default]Tank1Level.engUnit", "[default]Tank1Level"),
            changes::incrementAndGet);

        assertEquals(List.of("[default]Tank1Level"), gateway.tagsRead());
        assertEquals("[CP300]F8:23", lookup.get("[default]Tank1Level.engUnit").plcAddress());
    }

    @Test
    void propertiesAreStrippedFromPaths() {
        assertEquals("[default]A/B", TagSourceLookup.withoutProperty("[default]A/B.engUnit"));
        assertEquals("[default]A/B", TagSourceLookup.withoutProperty("[default]A/B"));
        assertEquals("[my.provider]Tag", TagSourceLookup.withoutProperty("[my.provider]Tag"));
        assertEquals("[.]Sibling", TagSourceLookup.withoutProperty("[.]Sibling.Quality"));
        assertEquals("[.]../X", TagSourceLookup.withoutProperty("[.]../X"));
    }

    @Test
    void plcAddressDropsTheNamespace() {
        assertEquals("[CP300]F8:23", TagSourceLookup.plcAddress("ns=1;s=[CP300]F8:23"));
        assertEquals("[CP100]Global.F8.F8[17]", TagSourceLookup.plcAddress("ns=1;s=[CP100]Global.F8.F8[17]"));
        assertEquals("Channel1.Device1.Tag", TagSourceLookup.plcAddress("nsu=urn:kepware;s=Channel1.Device1.Tag"));
        assertEquals("ns=2;i=1001", TagSourceLookup.plcAddress("ns=2;i=1001"), "numeric ids stay as they are");
        assertNull(TagSourceLookup.plcAddress("  "));
    }
}
