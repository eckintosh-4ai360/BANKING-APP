package com.company.banking.common.config;

import io.swagger.v3.oas.models.media.StringSchema;
import java.math.BigDecimal;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Decimals (money, rates, percentages) travel as JSON strings such as {@code "1250.00"} (decision D4). JavaScript
 * and Dart parse JSON numbers into binary floating point, which would silently lose cents on large amounts.
 * Incoming decimals are accepted as strings (preferred) or as numbers; Jackson reads either straight into
 * {@link BigDecimal} without going through a float.
 */
@Configuration
public class JsonConfig {

    static {
        // Keep the published API contract in line with what is actually sent.
        SpringDocUtils.getConfig().replaceWithSchema(BigDecimal.class,
                new StringSchema().format("decimal").pattern("^-?\\d+(\\.\\d+)?$").example("1250.00"));
    }

    @Bean
    public JacksonModule decimalAsStringModule() {
        SimpleModule module = new SimpleModule("decimal-as-string");
        module.addSerializer(BigDecimal.class, new DecimalAsStringSerializer());
        return module;
    }

    static final class DecimalAsStringSerializer extends StdSerializer<BigDecimal> {

        DecimalAsStringSerializer() {
            super(BigDecimal.class);
        }

        @Override
        public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context) {
            // Plain notation: never "1E+3".
            generator.writeString(value.toPlainString());
        }
    }
}
