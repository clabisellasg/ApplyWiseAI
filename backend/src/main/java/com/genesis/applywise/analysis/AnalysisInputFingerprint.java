package com.genesis.applywise.analysis;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class AnalysisInputFingerprint {

    public String generate(
            String resumeContent,
            String jobDescription,
            String provider,
            String model,
            String promptVersion
    ) {
        MessageDigest digest = sha256();
        updateLengthPrefixed(digest, resumeContent);
        updateLengthPrefixed(digest, jobDescription);
        updateLengthPrefixed(digest, provider);
        updateLengthPrefixed(digest, model);
        updateLengthPrefixed(digest, promptVersion);
        return HexFormat.of().formatHex(digest.digest());
    }

    private void updateLengthPrefixed(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
