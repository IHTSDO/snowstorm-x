package org.snomed.snowstorm.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class TraceabilityLoggingEnvironmentPostProcessorTest {

	private final TraceabilityLoggingEnvironmentPostProcessor processor = new TraceabilityLoggingEnvironmentPostProcessor();

	@Test
	void doesNotSetLoggingConfigWhenTraceabilityDisabled() {
		MockEnvironment environment = new MockEnvironment();
		processor.postProcessEnvironment(environment, new SpringApplication());
		assertNull(environment.getProperty("logging.config"));
	}

	@Test
	void setsTraceabilityLoggingConfigWhenTraceabilityEnabled() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("authoring.traceability.enabled", "true");
		processor.postProcessEnvironment(environment, new SpringApplication());
		assertEquals("classpath:logback-spring-traceability.xml", environment.getProperty("logging.config"));
		assertTrue(environment.getPropertySources().contains("traceabilityLogging"));
		assertInstanceOf(MapPropertySource.class, environment.getPropertySources().get("traceabilityLogging"));
	}

}
