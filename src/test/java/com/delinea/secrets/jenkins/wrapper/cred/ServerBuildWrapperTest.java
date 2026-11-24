package com.delinea.secrets.jenkins.wrapper.cred;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;

@WithJenkins
class ServerBuildWrapperTest {

    @Test
    void valuesToMaskArePerBuild(JenkinsRule j) throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        final FreeStyleBuild first = j.buildAndAssertSuccess(project);
        final FreeStyleBuild second = j.buildAndAssertSuccess(project);
        final ServerBuildWrapper wrapper = new ServerBuildWrapper(Collections.emptyList());

        final List<String> firstValues = wrapper.getValuesToMask(first);
        firstValues.add("first-secret");

        // setUp and createLoggerDecorator must see the same list for a build, whichever runs first
        assertSame(firstValues, wrapper.getValuesToMask(first));
        assertNotSame(firstValues, wrapper.getValuesToMask(second));
        assertTrue(wrapper.getValuesToMask(second).isEmpty());
    }

    @Test
    void addingValuesWhileMaskingDoesNotThrow(JenkinsRule j) throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        final FreeStyleBuild build = j.buildAndAssertSuccess(project);
        final FreeStyleBuild sibling = j.buildAndAssertSuccess(project);
        final ServerBuildWrapper wrapper = new ServerBuildWrapper(Collections.emptyList());

        final List<String> values = wrapper.getValuesToMask(build);
        values.add("s3cr3t");
        final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        final OutputStream out = wrapper.createLoggerDecorator(build).decorateLogger(build, sink);

        // Simulates setUp() of this build (pipeline) and of a concurrent build of the same job (freestyle)
        // adding values while this build's console is being masked.
        final Thread adder = new Thread(() -> {
            for (int i = 0; i < 500; i++) {
                values.add("value-" + i);
                wrapper.getValuesToMask(sibling).add("sibling-" + i);
                Thread.yield();
            }
        });
        adder.start();
        int lines = 0;
        while (adder.isAlive()) {
            out.write(("line " + lines++ + "\n").getBytes(StandardCharsets.UTF_8));
        }
        adder.join();
        assertTrue(lines > 0);

        sink.reset();
        out.write("token=s3cr3t\n".getBytes(StandardCharsets.UTF_8));
        out.flush();
        assertEquals("token=****\n", sink.toString(StandardCharsets.UTF_8));
    }
}
