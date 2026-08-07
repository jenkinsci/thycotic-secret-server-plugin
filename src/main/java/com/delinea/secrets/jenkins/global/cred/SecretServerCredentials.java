package com.delinea.secrets.jenkins.global.cred;

import java.io.IOException;
import java.util.Collections;

import javax.annotation.Nullable;
import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.delinea.secrets.jenkins.global.cred.VaultClient.UsernamePassword;
import com.delinea.secrets.jenkins.wrapper.cred.UserCredentials;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Descriptor.FormException;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;

public class SecretServerCredentials extends UsernamePasswordCredentialsImpl implements StandardCredentials {
	private static final long serialVersionUID = 1L;
	private final String usernameSlug;
	private final String passwordSlugName;
	private final String vaultUrl;
	private final String credentialId;
	private final String secretId;
	private final String proxyHost;
	private final String proxyPort;
	private final String proxyUsername;
	private final Secret proxyPassword;
	private final String noProxyHosts;
	private final boolean useProxy;
	private final String autoComment;

	/**
	 * Constructor to initialize the SecretServerCredentials object.
	 *
	 * @param scope         - The scope of the credentials (GLOBAL, SYSTEM, etc.).
	 * @param id            - The unique ID for the credentials.
	 * @param description   - A description for the credentials.
	 * @param vaultUrl      - The URL of the Secret Server.
	 * @param credentialId- The ID of the credentials stored in Jenkins.
	 * @param secretId      - The ID of the secret stored in the Secret Server.
	 * @throws FormException 
	 */
	@DataBoundConstructor
	public SecretServerCredentials(final CredentialsScope scope, final String id, final String description,
			String vaultUrl, String credentialId, String secretId, String usernameSlug, String passwordSlugName,
			String proxyHost, String proxyPort, String proxyUsername, Secret proxyPassword, String noProxyHosts,
			boolean useProxy, String autoComment) throws FormException {
		super(scope, id, description, null, null);
		this.usernameSlug = usernameSlug;
		this.passwordSlugName = passwordSlugName;
		this.vaultUrl = vaultUrl;
		this.credentialId = credentialId;
		this.secretId = secretId;
		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;
		this.proxyUsername = proxyUsername;
		this.proxyPassword = proxyPassword;
		this.noProxyHosts = noProxyHosts;
		this.useProxy = useProxy;
		this.autoComment = autoComment;
	}

	public boolean isUseProxy() {
		return useProxy;
	}
	
	public String getProxyHost() {
		return proxyHost;
	}
	
	public String getProxyPort() {
		return proxyPort;
	}
	
	public String getProxyUsername() {
		return proxyUsername;
	}
	
	public Secret getProxyPassword() {
	    return proxyPassword;
	}
	
	public String getNoProxyHosts() {
		return noProxyHosts;
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
	
	public String getUsernameSlug() {
		return usernameSlug;
	}
	
	public String getPasswordSlugName() {
		return passwordSlugName;
	}

	public String getAutoComment() {
		return autoComment;
	}

	/**
	 * Fetches the username from the Secret Server.
	 *
	 * @return The username fetched from the Secret Server.
	 */
	@Override
	public String getUsername() {
		return getVaultCredential(getContextItem()).getUsername();
	}

	/**
	 * Fetches the password from the Secret Server.
	 *
	 * @return The password fetched from the Secret Server, wrapped in a Secret
	 *         object.
	 */
	@Override
	public Secret getPassword() {
		return Secret.fromString(getVaultCredential(getContextItem()).getPassword());
	}

	@Nullable
    private Item getContextItem() {
        // Retrieve the nearest item in the current request context
        if (Stapler.getCurrentRequest() != null) {
            Item contextItem = Stapler.getCurrentRequest().findAncestorObject(Item.class);
            if (contextItem != null) {
                return contextItem;
            }
        }
        return null;
    }

	/**
	 * Fetches the credentials (username and password) from the Secret Server only
	 * once and caches it.
	 *
	 * @return The UsernamePassword object containing the fetched credentials.
	 * @throws RuntimeException if the credentials cannot be fetched from the Secret
	 *                          Server.
	 */
	private UsernamePassword getVaultCredential(@Nullable Item contextItem) {
	    try {
	        UserCredentials credential = UserCredentials.get(credentialId, contextItem);
	        if (credential == null) {
	            throw new RuntimeException("UserCredentials not found for credentialId: " + credentialId);
	        }
	        String ph = useProxy ? proxyHost : null;
	        String pp = useProxy ? proxyPort : null;
	        String pu = useProxy ? proxyUsername : null;
	        String pw = (useProxy && proxyPassword != null) ? proxyPassword.getPlainText() : null;
	        String nph = useProxy ? noProxyHosts : null;

			return new VaultClient().fetchCredentials(vaultUrl, secretId, credential.getUsername(),
					credential.getPassword().getPlainText(), usernameSlug, passwordSlugName, ph, pp, pu, pw, nph,
					autoComment);

		 } catch (Exception e) {
	        throw new RuntimeException("Failed to fetch credentials from vault. " + e.getMessage(), e);
	    }
	}

	@Extension
	public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

		@Override
		public String getDisplayName() {
			return "Delinea Secret Server or Platform Vault Credentials";
		}

		/**
		 * Populates the list of available Credential IDs for the dropdown in the
		 * Jenkins UI.
		 *
		 * @param owner - The Jenkins item context.
		 * @return A ListBoxModel containing the available Credential IDs.
		 */
		@POST
		public ListBoxModel doFillCredentialIdItems(@AncestorInPath final Item owner) {
		    if ((owner == null && !Jenkins.get().hasPermission(CredentialsProvider.CREATE))
		            || (owner != null && !owner.hasPermission(CredentialsProvider.CREATE))) {
		        return new StandardListBoxModel();
		    }
		    return new StandardListBoxModel()
		            .includeEmptyValue() 
		            .includeAs(ACL.SYSTEM, owner, UserCredentials.class); 
		}

		/**
		 * Validates the Credential ID input by the user.
		 */
		@POST
		public FormValidation doCheckCredentialId(@AncestorInPath Item item, @QueryParameter final String value)
				throws IOException, ServletException {
			if ((item == null && !Jenkins.get().hasPermission(CredentialsProvider.CREATE))
		            || (item != null && !item.hasPermission(CredentialsProvider.CREATE))) {
		        return FormValidation.error("You do not have permission to perform this action.");
		    }
			if (StringUtils.isBlank(value)) {
				return FormValidation.error("Credential ID is required.");
			}
			// Check if the Credential ID exists within the specified item context
			if (CredentialsProvider.lookupCredentials(UserCredentials.class, item, ACL.SYSTEM, Collections.emptyList())
					.stream().noneMatch(cred -> cred.getId().equals(value))) {
				return FormValidation.error("Credential ID not found. Please provide a valid ID.");
			}
			return FormValidation.ok();
		}

		/**
		 * Validates the Secret ID input by the user.
		 */
		@POST
		public FormValidation doCheckSecretId(@AncestorInPath final Item item, @QueryParameter final String value)
				throws IOException, ServletException {
			if ((item == null && !Jenkins.get().hasPermission(CredentialsProvider.CREATE))
		            || (item != null && !item.hasPermission(CredentialsProvider.CREATE))) {
		        return FormValidation.error("You do not have permission to perform this action.");
		    }
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

		@POST
		public FormValidation doCheckUsernameSlug(@AncestorInPath final Item item, @QueryParameter final String value)
				throws IOException, ServletException {
			if ((item == null && !Jenkins.get().hasPermission(CredentialsProvider.CREATE))
		            || (item != null && !item.hasPermission(CredentialsProvider.CREATE))) {
		        return FormValidation.error("You do not have permission to perform this action.");
		    }
			if (StringUtils.isBlank(value)) {
				return FormValidation.error("Slug name is required.");
			}
			return FormValidation.ok();
		}
		
		@POST
		public FormValidation doCheckPasswordSlugName(@AncestorInPath final Item item, @QueryParameter final String value)
				throws IOException, ServletException {
			if ((item == null && !Jenkins.get().hasPermission(CredentialsProvider.CREATE))
		            || (item != null && !item.hasPermission(CredentialsProvider.CREATE))) {
		        return FormValidation.error("You do not have permission to perform this action.");
		    }
			if (StringUtils.isBlank(value)) {
				return FormValidation.error("Slug name is required.");
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
				@QueryParameter("usernameSlug") final String usernameSlug,
				@QueryParameter("passwordSlugName") final String passwordSlugName,
				@QueryParameter("vaultUrl") final String vaultUrl,
				@QueryParameter("credentialId") final String credentialId,
				@QueryParameter("secretId") final String secretId,
				@QueryParameter("proxyHost") final String proxyHost,
				@QueryParameter("proxyPort") final String proxyPort,
				@QueryParameter("proxyUsername") final String proxyUsername,
				@QueryParameter("proxyPassword") final Secret proxyPassword, 
				@QueryParameter("noProxyHosts") final String noProxyHosts,
				@QueryParameter("useProxy") final boolean useProxy,
				@QueryParameter("autoComment") final String autoComment) {
			if ((owner == null && !Jenkins.get().hasPermission(CredentialsProvider.CREATE))
		            || (owner != null && !owner.hasPermission(CredentialsProvider.CREATE))) {
		        return FormValidation.error("You do not have permission to perform this action.");
		    }

			if (StringUtils.isBlank(credentialId)) {
				return FormValidation.error("Credential ID is required to test the connection.");
			}

			if (StringUtils.isBlank(vaultUrl)) {
				return FormValidation.error("Vault URL cannot be blank.");
			}
			
			if (StringUtils.isBlank(usernameSlug)) {
				return FormValidation.error("Slug name cannot be blank.");
			}
			
			if (StringUtils.isBlank(passwordSlugName)) {
				return FormValidation.error("Slug name cannot be blank.");
			}
			
			try {
				UserCredentials credential = UserCredentials.get(credentialId, owner);
				String ph = useProxy ? proxyHost : null;
				String pp = useProxy ? proxyPort : null;
				String pu = useProxy ? proxyUsername : null;
				String pw = (useProxy && proxyPassword != null) ? proxyPassword.getPlainText() : null;
				String nph = useProxy ? noProxyHosts : null;
	                
				new VaultClient().fetchCredentials(vaultUrl, secretId, credential.getUsername(),
						credential.getPassword().getPlainText(), usernameSlug, passwordSlugName, ph, pp, pu, pw, nph,
						autoComment);
				return FormValidation.ok("Connection successful.");
			}  catch (Exception e) {
		        Throwable root = e;
		        while (root.getCause() != null) {
		            root = root.getCause();
		        }

		        String message;
		        if (root instanceof java.net.UnknownHostException) {
		            message = "Host not found: " + root.getMessage();
		        } else if (root instanceof org.springframework.web.client.HttpClientErrorException) {
		            int status = ((org.springframework.web.client.HttpClientErrorException) root)
		                    .getStatusCode().value();
		            if (status == 407) {
		                message = "Proxy authentication failed (HTTP 407).";
		            } else if (status == 400) {
		                message = "Access denied or invalid client credentials (HTTP 400).";
		            } else if (status == 403) {
		                message = "Access forbidden (HTTP 403).";
		            } else {
		                message = "HTTP error (status " + status + ").";
		            }
		        } else if (root instanceof java.io.IOException) {
		            message = "Network I/O error: " + root.getMessage();
		        } else {
		            message = "Unexpected error: " + root.getMessage();
		        }

		        return FormValidation.error("Failed to establish connection: " + message);
		    }
		}
	}
}
