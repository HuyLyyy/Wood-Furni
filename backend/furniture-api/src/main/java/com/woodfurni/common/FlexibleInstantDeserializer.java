package com.woodfurni.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Jackson deserializer for {@link Instant} that accepts multiple common
 * input formats sent by the admin frontend and other clients.
 *
 * <p>Tries each pattern in order until one parses successfully:
 * <ol>
 *   <li>{@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'} — ISO-8601 UTC with millis
 *       (e.g. "2026-10-01T08:49:00.000Z") — browser {@code JSON.stringify(Date)}
 *   <li>{@code yyyy-MM-dd'T'HH:mm:ss'Z'} — ISO-8601 UTC without millis
 *   <li>{@code yyyy-MM-dd'T'HH:mm:ss.SSSXXX} — ISO-8601 with explicit offset
 *   <li>{@code yyyy-MM-dd'T'HH:mm:ssXXX} — ISO-8601 without millis + offset
 *   <li>{@code yyyy-MM-dd'T'HH:mm} — HTML datetime-local (VN timezone)
 *       Note: parsed as Asia/Ho_Chi_Minh wall-clock, not UTC.
 * </ol>
 *
 * <p>If all patterns fail, throws {@link IOException} with the unparseable value.
 *
 * <p>Usage: annotate the field on the DTO:
 * <pre>
 * &#64;JsonDeserialize(using = FlexibleInstantDeserializer.class)
 * private Instant startDate;
 * </pre>
 */
public class FlexibleInstantDeserializer extends JsonDeserializer<Instant> {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            // ISO-8601 UTC (with & without millis)
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC")),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC")),
            // ISO-8601 with explicit +07:00 / -05:00 offset
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,  // handles all XXX offsets
            // datetime-local without seconds (HTML <input type="datetime-local">)
            // Interpreted as Asia/Ho_Chi_Minh wall-clock → converted to Instant
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(VN)
    );

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        value = value.trim();

        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                TemporalAccessor parsed = formatter.parse(value);
                return Instant.from(parsed);
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }

        // Fallback: raw Instant.parse (handles fully-qualified ISO-8601)
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IOException(
                    "Cannot parse \"" + value + "\" as Instant. " +
                    "Expected formats: ISO-8601 (with Z or offset), " +
                    "or datetime-local (yyyy-MM-ddTHH:mm, Asia/Ho_Chi_Minh).", e);
        }
    }
}
