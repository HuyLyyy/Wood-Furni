package com.woodfurni.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Jackson deserializer for {@link Instant} that accepts:
 * <ol>
 *   <li>Any valid ISO-8601 string — "2026-10-01T08:49:00.000Z",
 *       "2026-10-01T08:49:00Z", "2026-10-01T08:49:00+07:00", etc.
 *       (via {@link Instant#parse}).
 *   <li>HTML datetime-local format "yyyy-MM-dd'T'HH:mm" — treated as
 *       Asia/Ho_Chi_Minh wall-clock and converted to UTC Instant.
 * </ol>
 *
 * <p>Usage:
 * <pre>
 * &#64;JsonDeserialize(using = FlexibleInstantDeserializer.class)
 * private Instant startDate;
 * </pre>
 */
public class FlexibleInstantDeserializer extends JsonDeserializer<Instant> {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Matches HTML &lt;input type="datetime-local"&gt; output: "2026-10-01T08:49". */
    private static final DateTimeFormatter DATETIME_LOCAL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(VN);

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        value = value.trim();

        // 1. Instant.parse handles every ISO-8601 variant including
        //    "2026-10-01T08:49:00.000Z", "2026-10-01T08:49:00Z",
        //    "2026-10-01T08:49:00+07:00", etc.
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // not ISO-8601 with zone; try datetime-local next
        }

        // 2. datetime-local without timezone: interpret as VN wall-clock.
        try {
            return Instant.from(DATETIME_LOCAL.parse(value));
        } catch (DateTimeParseException ignored) {
            // fall through to error
        }

        throw new IOException(
                "Cannot parse \"" + value + "\" as Instant. " +
                "Expected: ISO-8601 (e.g. 2026-10-01T08:49:00.000Z) " +
                "or datetime-local (yyyy-MM-ddTHH:mm).");
    }
}
