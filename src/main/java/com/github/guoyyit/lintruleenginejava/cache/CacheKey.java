package com.github.guoyyit.lintruleenginejava.cache;

/**
 * Cache key covering every input that may influence a file's result:
 * file content hash, merged configuration fingerprint, rule-set version
 * and the engine/suppression format version.
 */
public record CacheKey(String fileHash, String configFingerprint, String rulesFingerprint,
                       String engineVersion) {

    /** Bumped when suppression/engine semantics change. */
    public static final String ENGINE_VERSION = "engine-1";

    public CacheKey {
        if (fileHash == null || configFingerprint == null || rulesFingerprint == null) {
            throw new IllegalArgumentException("cache key parts must not be null");
        }
        if (engineVersion == null || engineVersion.isBlank()) {
            engineVersion = ENGINE_VERSION;
        }
    }

    /** Canonical stable string form (used nowhere else hashes it). */
    public String stableString() {
        return engineVersion + "|" + rulesFingerprint + "|" + configFingerprint + "|" + fileHash;
    }
}
