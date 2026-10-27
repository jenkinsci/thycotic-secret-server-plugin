package com.delinea.secrets.jenkins.wrapper.cred;

import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Descriptor.FormException;
import hudson.security.ACL;
import jenkins.model.Jenkins;

public class UserCredentials extends UsernamePasswordCredentialsImpl implements StandardCredentials {
    private static final long serialVersionUID = 1L;

    /**
     * The credentials of this type with this credentialId that apply to this item
     *
     * @param credentialId  the id of the credential
     * @param item         the optional item (context)
     * @return the credentials or {@code null} if no matching credentials exist
     */
	public static UserCredentials get(@Nonnull final String credentialId, @Nullable final Item item) {
		if (item != null) {
			// If we're inside a folder (item is non-null), check for the read permission at
			// the folder level.
			if (item.hasPermission(Item.READ)) {
				return CredentialsProvider
						.lookupCredentials(UserCredentials.class, item, ACL.SYSTEM, Collections.emptyList()).stream()
						.filter(cred -> cred.getId().equals(credentialId)).findFirst().orElse(null);
			}
		} else {
			// If there's no item (global context), check for global permission to view
			// credentials.
			if (Jenkins.get().hasPermission(CredentialsProvider.VIEW)) {
				return CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(UserCredentials.class,
						(ItemGroup<?>) null, ACL.SYSTEM, Collections.emptyList()), new IdMatcher(credentialId));
			}
		}

		return null;
	}

	/**
	 * Resolves the credentials of this type with this credentialId for internal
	 * use, without checking the permissions of the calling thread.
	 * <p>
	 * Use this when Jenkins itself needs the credential (e.g. an agent launcher
	 * resolving a {@code SecretServerCredentials}), where the current thread may
	 * run as anonymous. Callers handling user requests must use
	 * {@link #get(String, Item)} instead.
	 * <p>
	 * With a {@code null} item the lookup is done at the Jenkins root, which
	 * includes {@code SYSTEM}-scoped credentials. With an item, scoping rules apply
	 * and {@code SYSTEM}-scoped credentials are not visible.
	 *
	 * @param credentialId the id of the credential
	 * @param item         the optional item (context)
	 * @return the credentials or {@code null} if no matching credentials exist
	 */
	public static UserCredentials lookup(@Nonnull final String credentialId, @Nullable final Item item) {
		if (item != null) {
			return CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(UserCredentials.class, item,
					ACL.SYSTEM, Collections.emptyList()), new IdMatcher(credentialId));
		}
		return CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(UserCredentials.class,
				Jenkins.get(), ACL.SYSTEM, Collections.emptyList()), new IdMatcher(credentialId));
	}

    @DataBoundConstructor
    public UserCredentials(final CredentialsScope scope, final String id, final String description,
            final String username, final String password) throws FormException {
        super(scope, id, description, username, password);
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        @Override
        public String getDisplayName() {
            return "Delinea Secret Server Application or Platform Service Account Credentials";
        }
    }
}
