package com.thycotic.secrets.jenkins;

import hudson.console.ConsoleLogFilter;
import hudson.model.Run;

import java.io.OutputStream;
import java.io.Serializable;
import java.io.IOException;
import java.util.regex.Pattern;

import org.jenkinsci.plugins.credentialsbinding.masking.SecretPatterns;

// borrowed from https://github.com/jenkinsci/azure-keyvault-plugin/blob/master/src/main/java/org/jenkinsci/plugins/azurekeyvaultplugin/MaskingConsoleLogFilter.java
public class ServerConsoleLogFilter extends ConsoleLogFilter implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String charsetName;
    private final Pattern valuesToMask;

    public ServerConsoleLogFilter(final String charsetName, final Pattern valuesToMask) {
        this.charsetName = charsetName;
        this.valuesToMask = valuesToMask;
    }

    @Override
    public OutputStream decorateLogger(Run run, final OutputStream logger) throws IOException, InterruptedException {
        return new SecretPatterns.MaskingOutputStream(logger, () -> valuesToMask, charsetName);
    }
}
