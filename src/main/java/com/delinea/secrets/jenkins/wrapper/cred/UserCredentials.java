package com.delinea.secrets.jenkins.wrapper.cred;

import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Item;
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
        if(Jenkins.get().hasPermission(CredentialsProvider.VIEW))
        {
            return CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(UserCredentials.class, item, ACL.SYSTEM, Collections.emptyList()),
                    new IdMatcher(credentialId));
        }
        else
        {
            return null;
        }
    }

    @DataBoundConstructor
    public UserCredentials(final CredentialsScope scope, final String id, final String description,
            final String username, final String password) {
        super(scope, id, description, username, password);
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        @Override
        public String getDisplayName() {
            return "SecretServer User Credentials";
        }
    }
}
