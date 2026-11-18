package dev.flixw.sdk.bridge;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Checks the version data file and everything derived from it. */
public final class VersionDataTest {
    private static int passed;

    private VersionDataTest() { }

    public static void main(String[] args) {
        VersionData data = VersionData.load();
        require(data.verified().equals(List.of("0.60.0", "0.61.0", "0.65.0", "0.66.1", "0.67.1", "0.67.2",
                "0.68.0", "0.75.2", "0.75.3", "0.76.0", "0.76.1", "0.77.0", "0.78.0", "0.79.0", "0.80.0")),
            "the data file lists the fifteen verified releases, oldest first");
        require(data.verified().size() == 15, "fifteen verified releases");
        require(!data.verified().contains("0.59.0") && !data.verified().contains("0.66.0"),
            "the rejected releases are not verified");
        require(data.release("0.59.0").orElseThrow().rejection().equals("Symbol$EffSym")
                && data.release("0.66.0").orElseThrow().rejection().equals("dev.flix.runtime.Global"),
            "a rejected release names the reference its jar lacks");
        require(data.families().stream().map(VersionData.Family::id).toList().equals(
                List.of("flix0800", "flix0761", "flix0753", "flix0680", "flix0672", "flix0661", "flix0610", "flix0600")),
            "eight families, newest first");
        require(data.releases().stream().map(VersionData.Release::sha256).distinct().count() == 17
                && data.releases().stream().allMatch(r -> r.sha256().matches("[0-9a-f]{64}")),
            "every release has its own SHA-256 digest");
        require(data.family("flix0761").orElseThrow().adapter().equals("dev.flixw.sdk.bridge.adapter.Flix0761Adapter")
                && data.family("flix0761").orElseThrow().capabilities().equals(List.of("metrics")),
            "a family names its adapter class and capabilities");
        require(data.release("0.79.0").orElseThrow().family().equals("flix0761")
                && data.release("0.80.0").orElseThrow().family().equals("flix0800"),
            "a verified release names its family; 0.79.0 stays in flix0761");
        require(data.family("flix0800").orElseThrow().adapter().equals("dev.flixw.sdk.bridge.adapter.Flix0800Adapter")
                && data.family("flix0800").orElseThrow().floor().equals("0.80.0")
                && data.family("flix0800").orElseThrow().testVersion().equals("0.80.0"),
            "flix0800 starts at 0.80.0 and is tested against it");
        require(data.pin().equals(Optional.of("0.77.0"))
                && !data.pin().equals(Optional.of(data.families().get(0).testVersion())),
            "the pin is its own field: 0.77.0, not the test release of the first family");
        require(data.release(data.pin().orElseThrow()).orElseThrow().status().equals("verified"),
            "the pin is a verified release");
        require(data.releasesOf("flix0661").stream().map(VersionData.Release::version).toList()
                .equals(List.of("0.66.1", "0.67.1")), "releases of a family are listed oldest first");

        require(data.warning("metrics", Path.of("/cache/compilers",
                "flix-0.79.0-8fad2aa09567a7edabcc43f4101fa6199131df2c22929e001c87659d764f6dcf.jar")).isEmpty(),
            "a verified release in flixw's cache naming gets no notice");
        require(data.warning("metrics", Path.of("/cache/compilers/flix-0.77.0.jar")).isEmpty()
                && data.warning("metrics", Path.of("/cache/compilers/flix-0.80.0.jar")).isEmpty(),
            "a verified release by plain name gets no notice");
        String unknown = data.warning("metrics", Path.of("/cache/compilers/flix-0.81.0-abcdef0123.jar")).orElseThrow();
        require(unknown.startsWith("metrics: warning: ") && unknown.indexOf('\n') < 0,
            "the notice is a single warning line led by the plugin name");
        require(unknown.contains("0.81.0") && unknown.contains("newest verified: 0.80.0")
                && unknown.contains("flix-bridge/docs/compatibility"),
            "the notice names the release, the newest verified one and the compatibility records");
        require(data.warning("metrics", Path.of("/cache/compilers/flix-0.81.0.jar")).orElseThrow()
                .equals(unknown.replace("-abcdef0123", "")), "the cache suffix does not change the notice");
        require(VersionData.newest(List.of("0.9.0", "0.10.0", "0.2.5")).equals("0.10.0"),
            "the newest release is chosen numerically, not as text");
        require(VersionData.newest(List.of("0.79.0", "0.100.0", "0.80.0")).equals("0.100.0"),
            "a three-digit minor version is newer than a two-digit one");
        require(VersionData.newest(List.of()).equals("none"), "no verified release is named none");
        require(data.warning("metrics", Path.of("flix-0.100.0.jar")).orElseThrow().contains("0.100.0 has no"),
            "a release with a longer minor version is judged as itself");
        require(data.warning("metrics", Path.of("flix-0.59.0.jar")).orElseThrow().contains("0.59.0"),
            "a rejected release is noticed as well");
        for (String name : List.of("xflix-0.79.0.jar", "flix-0.79.0-rc1.jar", "flix-0.79.0-ABCDEF.jar",
                "flix-00.79.0.jar", "flix-0.79.0.jar.bak", "flix-0.79.00.jar", "flix-0.7.0.jar")) {
            require(data.warning("metrics", Path.of("/c", name)).isPresent(),
                name + " is not a verified release name");
        }
        require(data.warning("metrics", Path.of("/c/flix-0.79.0-ABCDEF.jar")).orElseThrow().contains("cannot read"),
            "an upper-case digest suffix is not a cache name");
        String unreadable = data.warning("metrics", Path.of("/c/flix.jar")).orElseThrow();
        require(unreadable.contains("cannot read the Flix version") && unreadable.indexOf('\n') < 0,
            "a jar name without a version gets a single notice that says so");

        require(VersionData.flixVersion(Map.of("FLIXW_COMPILER_VERSION", "0.79.0"), Path.of("/c/flix.jar"))
                .equals(Optional.of("0.79.0")), "FLIXW_COMPILER_VERSION names the Flix version");
        require(VersionData.flixVersion(Map.of("FLIXW_COMPILER_VERSION", "0.79.0"), Path.of("/c/flix-0.60.0.jar"))
                .equals(Optional.of("0.79.0")), "the variable wins over the jar name");
        require(VersionData.flixVersion(Map.of(), Path.of("/c/flix-0.60.0-abc.jar"))
                .equals(Optional.of("0.60.0")), "without the variable the jar name is the fallback");
        require(VersionData.flixVersion(Map.of("FLIXW_COMPILER_VERSION", " "), Path.of("/c/flix.jar")).isEmpty(),
            "a blank variable and an unreadable name give no version");
        require(data.warningFor("metrics", "0.80.0").isEmpty() && data.warningFor("metrics", "0.81.0").isPresent(),
            "a notice can be derived from the version alone");
        require(data.cacheFields("flix0761").equals(Map.of("sdkVersion", "1", "adapterFamily", "flix0761"))
                && data.cacheFields("flix0800").equals(Map.of("sdkVersion", "1", "adapterFamily", "flix0800")),
            "SDK version and adapter family are explicit cache fields");

        require(CompilerModel.CAPABILITY == Capability.METRICS && Capability.of("metrics").orElseThrow() == Capability.METRICS
                && Capability.of("solverHook").orElseThrow() == Capability.SOLVER_HOOK
                && Capability.of("nothing").isEmpty(), "the capabilities are named, and Metrics is CompilerModel");
        require(data.families().stream().allMatch(f -> f.capabilities().equals(List.of("metrics"))),
            "every family provides the metrics capability");
        rejects(minimal("\"status\": \"verified\", \"family\": \"f\"").replace("[\"metrics\"]", "[\"magic\"]"),
            "unknown capability");
        rejects("{", "unterminated object");
        rejects("{\"a\": 1, \"a\": 2}", "duplicate key");
        rejects("{} x", "trailing text");
        rejects(minimal("\"status\": \"verified\", \"family\": \"nope\""), "unknown family");
        rejects(minimal("\"status\": \"verified\""), "verified release without a family");
        rejects(minimal("\"status\": \"maybe\", \"family\": \"f\""), "unknown status");
        rejects(minimal("\"status\": \"rejected\""), "rejected release without a rejection");
        rejects(minimal("\"status\": \"verified\", \"family\": \"f\"").replace("0.60.0", "0.6"), "bad version");
        rejects(minimal("\"status\": \"verified\", \"family\": \"f\"").replace(hex(), "abc"), "bad digest");
        rejects(minimal("\"status\": \"verified\", \"family\": \"f\"").replace("\"schema\": 1", "\"schema\": 2"),
            "unsupported schema");
        rejects(minimal("\"status\": \"verified\", \"family\": \"f\"").replace("\"floor\": \"0.60.0\"",
            "\"floor\": \"0.59.9\""), "floor is not a release");
        String verified = "\"status\": \"verified\", \"family\": \"f\"";
        String release = "{\"version\": \"0.60.0\", \"sha256\": \"" + hex() + "\", " + verified + "}";
        rejects(minimal(verified).replace("}]}", "}, " + release + "]}"), "duplicate release");
        String family = "{\"id\": \"f\", \"adapter\": \"a.B\", \"floor\": \"0.60.0\","
            + " \"testVersion\": \"0.60.0\", \"capabilities\": [\"metrics\"]}";
        rejects(minimal(verified).replace("}], \"releases\"", "}, " + family + "], \"releases\""),
            "duplicate family");
        require(VersionData.parse(minimal(verified)).verified()
            .equals(List.of("0.60.0")), "the minimal document is accepted");
        require(VersionData.parse(minimal(verified)).pin().isEmpty(), "a document without a pin is read and has none");
        require(VersionData.parse(withPin(verified, "\"0.60.0\"")).pin().equals(Optional.of("0.60.0")),
            "a pin that names a verified release is read");
        rejectsWith(withPin(verified, "\"0.61.0\""), "pin 0.61.0 is not a verified release (not listed)",
            "pin names a release that is not listed");
        rejectsWith(withPin(verified, "\"0.6\""), "bad version 0.6", "pin is not a version");
        rejectsWith(withPin(verified, "0"), "missing pin", "pin is not a string");
        rejectsWith(withPin(verified, "\"\""), "missing pin", "pin is empty");
        String rejected = "{\"version\": \"0.59.0\", \"sha256\": \"" + "1".repeat(64)
            + "\", \"status\": \"rejected\", \"rejection\": \"X\"}";
        rejects(withPin(verified, "\"0.59.0\"").replace("\"releases\": [", "\"releases\": [" + rejected + ", "),
            "pin names a rejected release");
        rejectsWith(withPin(verified, "\"0.59.0\"").replace("\"releases\": [", "\"releases\": [" + rejected + ", "),
            "pin 0.59.0 is not a verified release (rejected)", "the message says that the pinned release is rejected");
        String newer = "{\"id\": \"g\", \"adapter\": \"a.C\", \"floor\": \"0.61.0\","
            + " \"testVersion\": \"0.61.0\", \"capabilities\": [\"metrics\"]}";
        String later = "{\"version\": \"0.61.0\", \"sha256\": \"" + "2".repeat(64)
            + "\", \"status\": \"verified\", \"family\": \"g\"}";
        String two = withPin(verified, "\"0.60.0\"").replace("\"families\": [", "\"families\": [" + newer + ", ")
            .replace("}]}", "}, " + later + "]}");
        require(VersionData.parse(two).families().get(0).id().equals("g")
                && VersionData.parse(two).pin().equals(Optional.of("0.60.0")),
            "the pin does not follow the order of the families");
        System.out.println("VersionDataTest: " + passed + " passed, 0 failed");
    }

    private static String hex() {
        return "0".repeat(64);
    }

    private static String minimal(String release) {
        return "{\"schema\": 1, \"families\": [{\"id\": \"f\", \"adapter\": \"a.B\", \"floor\": \"0.60.0\","
            + " \"testVersion\": \"0.60.0\", \"capabilities\": [\"metrics\"]}], \"releases\": [{\"version\": \"0.60.0\","
            + " \"sha256\": \"" + hex() + "\", " + release + "}]}";
    }

    private static String withPin(String release, String pin) {
        return minimal(release).replace("{\"schema\": 1, ", "{\"schema\": 1, \"pin\": " + pin + ", ");
    }

    private static void rejectsWith(String document, String message, String reason) {
        try {
            VersionData.parse(document);
        } catch (IllegalArgumentException e) {
            if (!e.getMessage().contains(message))
                throw new AssertionError(reason + ": message was " + e.getMessage());
            passed++;
            return;
        }
        throw new AssertionError("accepted: " + reason);
    }

    private static void rejects(String document, String reason) {
        try {
            VersionData.parse(document);
        } catch (IllegalArgumentException e) {
            passed++;
            return;
        }
        throw new AssertionError("accepted: " + reason);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
    }
}
