package com.woodfurni.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Jackson serializer for {@link Instant} that always outputs the date-time
 * in Asia/Ho_Chi_Minh (UTC+7) timezone using the HTML datetime-local format
 * "yyyy-MM-dd'T'HH:mm:ss".
 *
 * <p>Why this matters:
 * <ul>
 *   <li>The admin frontend uses {@code <input type="datetime-local">} which
 *       expects "yyyy-MM-dd'T'HH:mm" (no Z, no offset).
 *   <li>{@code new Date(isoString)} in the browser interprets a bare
 *       "yyyy-MM-dd'T'HH:mm" string as local time — so an Instant serialised
 *       as "2026-10-01T08:00" (VN) lands in the datetime-local input
 *       exactly as the user sees it, regardless of the admin's browser TZ.
 * </ul>
 *
 * <p>Usage on a DTO field:
 * <pre>
 * &#64;JsonSerialize(using = VietnamInstantSerializer.class)
 * &#64;JsonDeserialize(using = FlexibleInstantDeserializer.class)
 * private Instant startDate;
 * </pre>
 */
public class VietnamInstantSerializer extends JsonSerializer<Instant> {

    /** Output format: "yyyy-MM-dd'T'HH:mm:ss" in VN time (no Z). */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                    .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Override
    public void serialize(Instant value, JsonGenerator gen,
                          SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(FORMATTER.format(value));
    }
}
