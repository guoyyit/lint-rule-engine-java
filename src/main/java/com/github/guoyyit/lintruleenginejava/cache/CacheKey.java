package com.github.guoyyit.lintruleenginejava.cache;

/**
 * Cache key covering every input that may influence a file's result:
 * file content hash, merged configuration fingerprint, rule-set version
 * and the engine/suppression format version.
 */
public record CacheKey(String fileHash, String configFingerprint, String rulesFingerprint, String engineVersion) {

    public String stableString() {
        // skeleton
        return null;
    }
}
