package com.delinea.secrets.jenkins.global.cred;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.thycotic.secrets.server.spring.Secret;
import com.thycotic.secrets.server.spring.SecretServer;
import com.thycotic.secrets.server.spring.SecretServerFactoryBean;


public class VaultClient {
	private static final String USERNAME_PROPERTY = "secret_server.oauth2.username";
	private static final String PASSWORD_PROPERTY = "secret_server.oauth2.password";
	private static final String API_ROOT_URL_PROPERTY = "secret_server.api_root_url";
	private static final String OAUTH2_TOKEN_URL_PROPERTY = "secret_server.oauth2.token_url";

	public VaultClient() {
	}

	/**
	 * Fetches credentials from the Secret Server using the provided Vault URL,
	 * secret ID, username, and password.
	 *
	 * @param vaultUrl The base URL of the Secret server.
	 * @param secretId The ID of the secret to fetch.
	 * @param username The username for authenticating with the Vault.
	 * @param password The password for authenticating with the Vault.
	 * @param usernameSlug 
	 * @param passwordSlugName 
	 * @return A UsernamePassword object containing the fetched credentials, or null
	 *         if not found.
	 * @throws Exception if there is an error during the fetching process.
	 */
	public UsernamePassword fetchCredentials(String vaultUrl, String secretId, String username, String password, String usernameSlug, String passwordSlugName)
			throws Exception {
		// Create a map to hold properties for the Secret Server connection
		Map<String, Object> properties = new HashMap<>();

		// Remove trailing slash from the Vault URL if present
		String ssurl = StringUtils.removeEnd(vaultUrl, "/");
		if (StringUtils.isNotBlank(ssurl)) {
			properties.put(API_ROOT_URL_PROPERTY, ssurl + "/api/v1");
			properties.put(OAUTH2_TOKEN_URL_PROPERTY, ssurl + "/oauth2/token");
		}

		properties.put(USERNAME_PROPERTY, username);
		properties.put(PASSWORD_PROPERTY, password);

		// Create and configure the application context with the Secret Server
		try (AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext()) {
			applicationContext.getEnvironment().getPropertySources()
					.addLast(new MapPropertySource("properties", properties));
			applicationContext.registerBean(SecretServerFactoryBean.class);
			applicationContext.refresh();

			// Fetch the secret using the provided secret ID
			Secret secret = applicationContext.getBean(SecretServer.class).getSecret(Integer.parseInt(secretId));
			// Extract the username and password fields from the secret
			Optional<String> fetchUsername = secret.getFields().stream()
					.filter(field -> usernameSlug.equalsIgnoreCase(field.getFieldName())|| usernameSlug.equalsIgnoreCase(field.getSlug())).map(Secret.Field::getValue)
					.findFirst();

			Optional<String> fetchPassword = secret.getFields().stream()
					.filter(field -> passwordSlugName.equalsIgnoreCase(field.getFieldName()) || passwordSlugName.equalsIgnoreCase(field.getSlug())).map(Secret.Field::getValue)
					.findFirst();
			
			// Return the fetched credentials if both username and password are present
			if (fetchUsername.isPresent() && fetchPassword.isPresent()) {
				UsernamePassword usernamePassword = new UsernamePassword(fetchUsername.get(), fetchPassword.get());
				return usernamePassword;
			} else {
				return null;
			}
		}
	}

	public static class UsernamePassword {
		private final String username;
		private final hudson.util.Secret password;

		public UsernamePassword(String username, String password) {
			this.username = username;
			this.password = hudson.util.Secret.fromString(password);
		}

		public String getPassword() {
			return password.getPlainText();
		}
		
		public String getUsername() {
			return username;
		}
	}

}
