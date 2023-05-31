package com.thycotic.secrets.jenkins;

import hudson.console.ConsoleLogFilter;
import hudson.model.Run;

import java.io.OutputStream;
import java.io.Serializable;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.credentialsbinding.masking.SecretPatterns;

// borrowed from https://github.com/jenkinsci/azure-keyvault-plugin/blob/master/src/main/java/org/jenkinsci/plugins/azurekeyvaultplugin/MaskingConsoleLogFilter.java
public class ServerConsoleLogFilter extends ConsoleLogFilter implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String charsetName;
    private final List<String> valuesToMask;

    public ServerConsoleLogFilter(final String charsetName, final List<String> valuesToMask) {
        this.charsetName = charsetName;
        this.valuesToMask = valuesToMask;
    }

    @Override
    public OutputStream decorateLogger(Run run, final OutputStream logger) throws IOException, InterruptedException {
        return new SecretPatterns.MaskingOutputStream(logger, () -> {
                List<String> values = valuesToMask.stream().filter(Objects::nonNull).collect(Collectors.toList());
                if (!values.isEmpty()) {
                    return SecretPatterns.getAggregateSecretPattern(values);
                } else {
                    return null;
                }
            },charsetName);
    }
}
