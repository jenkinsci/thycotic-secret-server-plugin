package com.thycotic.secrets.jenkins;

import hudson.console.ConsoleLogFilter;
import hudson.model.Run;

import java.io.OutputStream;
import java.io.Serializable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import org.jenkinsci.plugins.credentialsbinding.masking.SecretPatterns;
import java.util.regex.PatternSyntaxException;

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
                return ServerConsoleLogFilter.getAggregateSecretPattern(values);
            } else {
                return null;
            }
        },charsetName);
    }

    public static Pattern getAggregateSecretPattern(List<String> patterns) {
        List<String> escapedPatterns = new ArrayList<>();
        for (String pattern : patterns) {
            escapedPatterns.add(ServerConsoleLogFilter.escapeSpecialCharacters(pattern));
        }
        String aggregatedPattern = String.join("|", escapedPatterns);
        try {
            return Pattern.compile(aggregatedPattern);
        } catch (PatternSyntaxException e) {
            System.err.println("Error compiling pattern: " + e.getMessage());
            return null;
        }
    }

    private static String escapeSpecialCharacters(String input) {
        String[] specialChars = {"\\", "^", "$", ".", "|", "?", "*", "+", "(", ")", "[", "]", "{", "}", "~", "@", "#", "%", "&", "_", "-", "=", "!", "/"};
        for (String specialChar : specialChars) {
            input = input.replace(specialChar, "\\" + specialChar);
        }
        return input;
    }
}
