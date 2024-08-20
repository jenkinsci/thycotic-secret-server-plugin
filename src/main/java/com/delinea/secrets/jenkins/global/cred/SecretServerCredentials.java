package com.delinea.secrets.jenkins.global.cred;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.delinea.secrets.jenkins.global.cred.VaultClient.UsernamePassword;
import com.delinea.secrets.jenkins.wrapper.cred.UserCredentials;

import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;

public class SecretServerCredentials extends UsernamePasswordCredentialsImpl implements StandardCredentials {

	private static final long serialVersionUID = 1L;
	private final String vaultUrl;
	private final String credentialId;
	private final String secretId;
	private final UsernamePassword vaultCredential; // Caches the credentials fetched from Secret Server

	/**
	 * Constructor to initialize the SecretServerCredentials object.
	 *
	 * @param scope         - The scope of the credentials (GLOBAL, SYSTEM, etc.).
	 * @param id            - The unique ID for the credentials.
	 * @param description   - A description for the credentials.
	 * @param vaultUrl      - The URL of the Secret Server.
	 * @param credentialId- The ID of the credentials stored in Jenkins.
	 * @param secretId      - The ID of the secret stored in the Secret Server.
	 */
	@DataBoundConstructor
	public SecretServerCredentials(CredentialsScope scope, String id, String description, String vaultUrl,
			String credentialId, String secretId) {
		super(scope, id, description, null, null);
		this.vaultUrl = vaultUrl;
		this.credentialId = credentialId;
		this.secretId = secretId;
		this.vaultCredential = null;
	}

	public String getVaultUrl() {
		return vaultUrl;
	}

	public String getCredentialId() {
		return credentialId;
	}

	public String getSecretId() {
		return secretId;
	}

	/**
	 * Fetches the username from the Secret Server.
	 *
	 * @return The username fetched from the Secret Server.
	 */
	@Override
	public String getUsername() {
		return getVaultCredential().username;
	}

	/**
	 * Fetches the password from the Secret Server.
	 *
	 * @return The password fetched from the Secret Server, wrapped in a Secret
	 *         object.
	 */
	@Override
	public Secret getPassword() {
		return Secret.fromString(getVaultCredential().password);
	}

	/**
	 * Fetches the credentials (username and password) from the Secret Server only
	 * once and caches it.
	 *
	 * @return The UsernamePassword object containing the fetched credentials.
	 * @throws RuntimeException if the credentials cannot be fetched from the Secret
	 *                          Server.
	 */
	private UsernamePassword getVaultCredential() {
		if (vaultCredential == null) { // Fetch only if not already cached
			try {
				UserCredentials credential = UserCredentials.get(credentialId, null);
				vaultCredential = new VaultClient().fetchCredentials(vaultUrl, secretId, credential.getUsername(),
						credential.getPassword().getPlainText());
			} catch (Exception e) {
				throw new RuntimeException("Failed to fetch credentials from vault. " + e.getMessage());
			}
		}
		return vaultCredential;
	}

	@Extension
	public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

		@Override
		public String getDisplayName() {
			return "Secret Server Vault Credentials";
		}

		/**
		 * Populates the list of available Credential IDs for the dropdown in the
		 * Jenkins UI.
		 *
		 * @param item - The Jenkins item context.
		 * @return A ListBoxModel containing the available Credential IDs.
		 */
		public ListBoxModel doFillCredentialIdItems(@AncestorInPath final Item item) {
			return new StandardListBoxModel().includeAs(ACL.SYSTEM, item, UserCredentials.class).includeEmptyValue();
		}

		/**
		 * Validates the Credential ID input by the user.
		 */
		public FormValidation doCheckCredentialId(@QueryParameter final String value)
				throws IOException, ServletException {
			if (StringUtils.isBlank(value)) {
				return FormValidation.error("Credential ID is required.");
			}
			return FormValidation.ok();
		}

		/**
		 * Validates the Secret ID input by the user.
		 */
		public FormValidation doCheckSecretId(@QueryParameter final String value) throws IOException, ServletException {
			if (StringUtils.isBlank(value)) {
				return FormValidation.error("Secret ID is required.");
			}
			try {
				Integer.parseInt(value);
			} catch (NumberFormatException e) {
				return FormValidation.error("ID must be an integer.");
			}
			return FormValidation.ok();
		}

		/**
		 * Tests the connection to the Secret Server using the provided parameters.
		 *
		 * @param owner        - The Jenkins item context.
		 * @param vaultUrl     - The URL of the Secret Server.
		 * @param credentialId - The ID of the credentials stored in Jenkins.
		 * @param secretId     - The ID of the secret stored in theSecret Server.
		 * @return FormValidation indicating whether the connection was successful or
		 *         not.
		 */
		@POST
		public FormValidation doTestConnection(@AncestorInPath Item owner,
				@QueryParameter("vaultUrl") final String vaultUrl,
				@QueryParameter("credentialId") final String credentialId,
				@QueryParameter("secretId") final String secretId) {

			// Check for necessary permissions
			if (owner == null) {
				Jenkins.get().checkPermission(Jenkins.ADMINISTER);
			} else {
				owner.checkPermission(Item.CONFIGURE);
			}

			// Validate inputs
			if (StringUtils.isBlank(credentialId)) {
				return FormValidation.error("Credential ID is required to test the connection.");
			}

			if (StringUtils.isBlank(vaultUrl)) {
				return FormValidation.error("Vault URL cannot be blank.");
			}

			try {
				// Attempt to fetch credentials from Secret Server
				UserCredentials credential = UserCredentials.get(credentialId, null);
				new VaultClient().fetchCredentials(vaultUrl, secretId, credential.getUsername(),
						credential.getPassword().getPlainText());
				return FormValidation.ok("Connection successful.");
			} catch (Exception e) {
				return FormValidation.error("Failed to establish connection: " + e.getMessage());
			}
		}
	}
}
