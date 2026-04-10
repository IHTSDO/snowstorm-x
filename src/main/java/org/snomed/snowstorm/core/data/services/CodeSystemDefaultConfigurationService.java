package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.services.pojo.CodeSystemDefaultConfiguration;

import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeSystemDefaultConfigurationService {

	private static final Pattern SNOMEDCT_MODULE_SHORT_NAME = Pattern.compile("^SNOMEDCT-(\\d+)$", Pattern.CASE_INSENSITIVE);

	private final Map<String, String> config = new HashMap<>();

	private Set<CodeSystemDefaultConfiguration> configurations;

	public Map<String, String> getConfig() {
		return config;
	}

	public String getDefaultModuleId(String codeSystemShortName) {
		for (CodeSystemDefaultConfiguration codeSystemConfiguration : configurations) {
			if (codeSystemConfiguration.shortName().equalsIgnoreCase(codeSystemShortName)) {
				return codeSystemConfiguration.module();
			}
		}
		if (codeSystemShortName != null) {
			Matcher m = SNOMEDCT_MODULE_SHORT_NAME.matcher(codeSystemShortName);
			if (m.matches()) {
				return m.group(1);
			}
		}
		return null;
	}

	@PostConstruct
	private void init() {
		configurations = new HashSet<>();
		for (String key : config.keySet()) {
			String codeSystemShortName = key.substring(key.lastIndexOf(".") + 1);
			String configString = config.get(key);
			String[] split = configString.split("\\|");
			String name = split[0];
			String moduleId = split[1];
			String countryCode = split.length > 2 ? split[2] : null;
			String owner = split.length > 3 ? split[3] : null;
			configurations.add(new CodeSystemDefaultConfiguration(name, codeSystemShortName, moduleId, countryCode, owner));
		}
	}

	public Set<CodeSystemDefaultConfiguration> getConfigurations() {
		return configurations;
	}
}
