package com.workpulse.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalTime;

/**
 * Maps LocalTime directly to/from SQL TIME strings ("HH:mm:ss") without undergoing
 * JVM default timezone or JDBC Calendar offset adjustments.
 */
@Converter
public class LocalTimeConverter implements AttributeConverter<LocalTime, String> {

    @Override
    public String convertToDatabaseColumn(LocalTime attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.toString();
    }

    @Override
    public LocalTime convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        // In case H2 or MySQL returns "09:00:00" or "09:00"
        return LocalTime.parse(dbData.trim());
    }
}
