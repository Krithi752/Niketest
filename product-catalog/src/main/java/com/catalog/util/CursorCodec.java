package com.catalog.util;

import com.catalog.exception.BadRequestException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Encodes and decodes opaque keyset pagination cursors representing (price, id).
 */
public final class CursorCodec {

    private CursorCodec() {}

    public record KeysetCursor(BigDecimal price, UUID id) {}

    public static String encode(BigDecimal price, UUID id) {
        String raw = price.toPlainString() + ":" + id.toString();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static KeysetCursor decode(String cursor) {
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(cursor);
            String raw = new String(decodedBytes, StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 2);
            if (parts.length != 2) {
                throw new BadRequestException("Malformed pagination cursor");
            }
            return new KeysetCursor(new BigDecimal(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid pagination cursor");
        }
    }
}
