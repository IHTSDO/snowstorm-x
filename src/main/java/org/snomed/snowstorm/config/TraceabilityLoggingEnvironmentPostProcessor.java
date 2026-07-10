package org.snomed.snowstorm.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

public class TraceabilityLoggingEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private static final String TRACEABILITY_ENABLED = "authoring.traceability.enabled";
	private static final String TRACEABILITY_LOGGING_CONFIG = "classpath:logback-spring-traceability.xml";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		if (Boolean.TRUE.equals(environment.getProperty(TRACEABILITY_ENABLED, Boolean.class, false))) {
			environment.getPropertySources().addFirst(new MapPropertySource("traceabilityLogging",
					Map.of("logging.config", TRACEABILITY_LOGGING_CONFIG)));
		}
	}

}
