package com.delinea.secrets.jenkins.wrapper.cred;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.delinea.platform.service.AuthenticationService;
import com.delinea.secrets.jenkins.util.DelineaProxyUtil;
import com.delinea.server.spring.Secret;
import com.delinea.server.spring.SecretServer;
import com.delinea.server.spring.SecretServerFactoryBean;

import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.tasks.SimpleBuildWrapper;

public class ServerBuildWrapper extends SimpleBuildWrapper {
    private static final String USERNAME_PROPERTY = "server.username";
    private static final String PASSWORD_PROPERTY = "server.password";
    private static final String SERVER_URL_PROPERTY = "server.url";
    private static final String AUTO_COMMENT = "autoComment";

    private List<ServerSecret> secrets;
    private List<String> valuesToMask = new ArrayList<>();

    @DataBoundConstructor
    public ServerBuildWrapper(final List<ServerSecret> secrets) {
        this.secrets = secrets;
    }

    public List<ServerSecret> getSecrets() {
        return secrets;
    }

    @DataBoundSetter
    public void setSecrets(final List<ServerSecret> secrets) {
        this.secrets = secrets;
    }

    @Override
    public ConsoleLogFilter createLoggerDecorator(final Run<?,?> build) {
        return new ServerConsoleLogFilter(build.getCharset().name(), valuesToMask);
    }

    @Override
    public void setUp(final Context context,
                      final Run<?,?> build,
                      final FilePath workspace,
                      final Launcher launcher,
                      final TaskListener listener,
                      final EnvVars initialEnvironment)
            throws IOException, InterruptedException {

        final ServerConfiguration configuration = ExtensionList.lookupSingleton(ServerConfiguration.class);

        // Loop through each secret config
        for (ServerSecret serverSecret : secrets) {
            final Map<String, Object> properties = new HashMap<>();
            // Determine base URL (global vs override)
            final String overrideBaseURL = serverSecret.getBaseUrl();
            final String effectiveUrl = StringUtils.isNotBlank(overrideBaseURL)
                    ? overrideBaseURL : configuration.getBaseUrl();
            properties.put(SERVER_URL_PROPERTY, effectiveUrl);
            properties.put(AUTO_COMMENT, serverSecret.getAutoComment());
            final String overrideCredId = serverSecret.getCredentialId();
            final UserCredentials credential = StringUtils.isNotBlank(overrideCredId)
                    ? UserCredentials.get(overrideCredId, build.getParent())
                    : UserCredentials.get(configuration.getCredentialId(), build.getParent());
            if (credential == null) {
                throw new IOException("No credentials available to access Delinea Secret Server.");
            }
            properties.put(USERNAME_PROPERTY, credential.getUsername());
            properties.put(PASSWORD_PROPERTY, credential.getPassword().getPlainText());

            // Resolve proxy config (host/port/username/password) using shared utility
            Map<String, String> proxyConfig = DelineaProxyUtil.resolveProxy(
                 effectiveUrl,
                 configuration.isUseProxy() ? configuration.getProxyHost() : "",
                 configuration.isUseProxy() ? String.valueOf(configuration.getProxyPort()) : "",
                 configuration.isUseProxy() ? configuration.getProxyUsername() : "",
                 configuration.isUseProxy() && configuration.getProxyPassword() != null
                         ? configuration.getProxyPassword().getPlainText()
                         : "",
                 configuration.isUseProxy() ? configuration.getNoProxyHosts() : ""
         );

            // Add all proxy property keys found
            for (Map.Entry<String, String> entry : proxyConfig.entrySet()) {
                properties.put(entry.getKey(), entry.getValue());
            }

            listener.getLogger().println("[ServerBuildWrapper][DEBUG] Connecting to Secret Server URL: " + effectiveUrl);

            try (AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext()) {
                applicationContext.getEnvironment().getPropertySources()
                        .addLast(new MapPropertySource("properties", properties));
                applicationContext.registerBean(SecretServerFactoryBean.class);
                applicationContext.registerBean(AuthenticationService.class);
                applicationContext.refresh();

                SecretServer secretServer = applicationContext.getBean(SecretServer.class);
                Secret secret = secretServer.getSecret(serverSecret.getId());

                secret.getFields().forEach(field -> {
                    serverSecret.getMappings().forEach(mapping -> {
                        if (mapping.getField().equalsIgnoreCase(field.getFieldName())
                                || mapping.getField().equalsIgnoreCase(field.getSlug())) {
                            context.env(
                                StringUtils.trimToEmpty(configuration.getEnvironmentVariablePrefix())
                                + mapping.getEnvironmentVariable(),
                                field.getValue());
                            valuesToMask.add(field.getValue());
                        }
                    });
                });
            }catch (Exception ex) {
                String proxyHost = proxyConfig.getOrDefault("proxy.host", "(none)");
                String proxyPort = proxyConfig.getOrDefault("proxy.port", "(none)");
                String proxyUser = proxyConfig.getOrDefault("proxy.username", "(none)");
                String proxyPass = proxyConfig.getOrDefault("proxy.password", "(none)");

                // Mask username and password
                String maskedProxyUser = proxyUser.equals("(none)") ? "(none)" : proxyUser.replaceAll(".", "*");
                String maskedProxyPass = proxyPass.equals("(none)") ? "(none)" : proxyPass.replaceAll(".", "*");

                String maskedProxyInfo = String.format(
                    "Proxy Host=%s, Port=%s, Username=%s, Password=%s",
                    proxyHost, proxyPort, maskedProxyUser, maskedProxyPass
                );

                // Extract root cause
                Throwable root = ex;
                while (root.getCause() != null) {
                    root = root.getCause();
                }

                // Friendly error message
                String friendlyMessage;
                if (root instanceof java.net.UnknownHostException) {
                    friendlyMessage = "Host not found: " + root.getMessage();
                } else if (root instanceof org.springframework.web.client.HttpClientErrorException) {
                    int status = ((org.springframework.web.client.HttpClientErrorException) root).getStatusCode().value();
                    if (status == 407) {
                        friendlyMessage = "Proxy authentication failed (HTTP 407).";
                    } else if (status == 400) {
                        friendlyMessage = "Access denied / invalid credentials (HTTP 400).";
                    } else if (status == 403) {
                        friendlyMessage = "Access forbidden (HTTP 403).";
                    } else {
                        friendlyMessage = "HTTP error (status " + status + ").";
                    }
                } else if (root instanceof java.io.IOException) {
                    friendlyMessage = "Network I/O error: " + root.getMessage();
                } else {
                    friendlyMessage = "Unexpected error: " + root.getMessage();
                }

                // Log details
                listener.getLogger().println("[ServerBuildWrapper][ERROR] Failed to fetch secret.");
                listener.getLogger().println("    Secret ID   : " + serverSecret.getId());
                listener.getLogger().println("    Target URL  : " + effectiveUrl);
                listener.getLogger().println("    Proxy Info  : " + maskedProxyInfo);
                listener.getLogger().println("    Root Cause  : " + root.getClass().getSimpleName() + " - " + friendlyMessage);

                // Throw IOException with root cause for build failure
                throw new IOException(
                    String.format(
                        "Failed to fetch secret (id=%s) for host=%s. Proxy used: %s. See logs for details.",
                        serverSecret.getId(), effectiveUrl, maskedProxyInfo
                    ),
                    ex
                );
            }

        }
    }

    @Extension
    @Symbol("withSecretServer")
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public boolean isApplicable(final AbstractProject<?,?> item) {
            return true;
        }
        @Override
        public String getDisplayName() {
            return "Use Delinea Secret Server or Platform Secrets";
        }
    }
}
