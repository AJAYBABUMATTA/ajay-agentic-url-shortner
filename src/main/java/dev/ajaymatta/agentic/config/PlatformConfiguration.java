package dev.ajaymatta.agentic.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Clock;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class PlatformConfiguration {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictRequests() {
        return builder -> builder.featuresToEnable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature(), SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .postConfigurer(mapper -> {
                    var text = mapper.coercionConfigFor(LogicalType.Textual);
                    text.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                    text.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                    text.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                });
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI platformApi() {
        return new OpenAPI().info(new Info().title("Agentic Engineering Platform")
                .version("0.1.0")
                .description("Automatic requirement interpretation, ambiguity handling, isolated repository analysis "
                        + "and dynamic planning. Engineering source generation and build execution remain gated."));
    }
}
