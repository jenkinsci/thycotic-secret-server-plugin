package com.delinea.secrets.jenkins.wrapper.cred;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

/**
 * A Delinea SecretServer Secret, identified by it's id, and a list of mappings
 * from the secret's data fields to environment variables.
 */
public class ServerSecret extends AbstractDescribableImpl<ServerSecret> {
    private final int id;
    private final List<Mapping> mappings;

    public int getId() {
        return id;
    }

    public List<Mapping> getMappings() {
        return mappings;
    }

    @DataBoundConstructor
    public ServerSecret(final int id, final List<Mapping> mappings) {
        this.id = id;
        this.mappings = mappings;
    }

    private String baseUrl, credentialId;

    public String getBaseUrl() {
        return baseUrl;
    }

    @DataBoundSetter
    public void setBaseUrl(final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCredentialId() {
        return credentialId;
    }

    @DataBoundSetter
    public void setCredentialId(final String credentialId) {
        this.credentialId = credentialId;
    }

    public static class Mapping extends AbstractDescribableImpl<Mapping> {
        private final String environmentVariable, field;

        public String getEnvironmentVariable() {
            return environmentVariable;
        }

        public String getField() {
            return field;
        }

        @DataBoundConstructor
        public Mapping(final String environmentVariable, final String field) {
            this.environmentVariable = environmentVariable;
            this.field = field;
        }

        @Extension
        public static final class DescriptorImpl extends Descriptor<Mapping> {
            private static final String NAME_PATTERN = "[a-zA-Z_-][a-zA-Z0-9 ]*";

            @Override
            public String getDisplayName() {
                return "Secret Field to Environment Variable Mapping";
            }

            private FormValidation checkPattern(final String value, final String name) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return FormValidation.error("You do not have permission to perform this action");
                }
                if (Pattern.matches(NAME_PATTERN, value))
                    return FormValidation.ok();
                return FormValidation.error(String.format("%s must match %s", name, NAME_PATTERN));

            }

            @POST
            public FormValidation doCheckEnvironmentVariable(@QueryParameter final String value)
                    throws IOException, ServletException {
                return checkPattern(value, "Environment Variable");
            }

            @POST
            public FormValidation doCheckField(@QueryParameter final String value)
                    throws IOException, ServletException {
                return checkPattern(value, "Field Name");
            }

        }
    }

    @Extension
    @Symbol("secretServerSecret")
    public static final class DescriptorImpl extends Descriptor<ServerSecret> {
        public String getDisplayName() {
            return "Secret Server Secret";
        }

        @POST
        public FormValidation doCheckCredentialId(@QueryParameter final String value)
                throws IOException, ServletException {
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                return FormValidation.error("You do not have permission to perform this action");
            }
            if (StringUtils.isBlank(value) && StringUtils.isBlank(ServerConfiguration.get().getCredentialId()))
                return FormValidation.error("Credentials are required");
            return FormValidation.ok();
        }

        @POST
        public ListBoxModel doFillCredentialIdItems(@AncestorInPath final Item item) {
            if (item == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER) ||
                    item != null && !item.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }
            return new StandardListBoxModel().includeAs(ACL.SYSTEM, item, UserCredentials.class).includeEmptyValue();
        }

        @POST
        public FormValidation doCheckId(@QueryParameter final String value) throws IOException, ServletException {
            try {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return FormValidation.error("You do not have permission to perform this action");
                }
                Integer.parseInt(value);
                return FormValidation.ok();
            } catch (final NumberFormatException e) {
                return FormValidation.error("Secret ID is an integer");
            }
        }

        @POST
        public FormValidation doCheckBaseUrl(@QueryParameter final String value) throws IOException, ServletException {
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                return FormValidation.error("You do not have permission to perform this action");
            }
            if (StringUtils.isBlank(value) && StringUtils.isNotBlank(ServerConfiguration.get().getBaseUrl()))
                return FormValidation.ok();
            return ServerConfiguration.checkBaseUrl(value);
        }
    } // TODO support for credential domains and permissions
}
