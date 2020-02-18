/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.proccleaner;

import hudson.Functions;
import hudson.Launcher;
import hudson.Proc;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.slaves.DumbSlave;
import hudson.util.ArgumentListBuilder;
import hudson.util.OneShotEvent;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import org.apache.tools.ant.util.JavaEnvUtils;
import org.jenkinsci.plugins.proccleaner.PsCleaner.PsCleanerDescriptor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

import static org.jenkinsci.plugins.proccleaner.Util.getLogAsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

public class PsCleanerTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    private PsCleaner preCleaner;
    private PsCleaner postCleaner;

    @Before public void setUp() {
        preCleaner = mock(PsCleaner.class, withSettings().serializable());
        postCleaner = mock(PsCleaner.class, withSettings().serializable());
    }

    @Test public void skipWhenGloballyTurnedOff() throws Exception {
        DumbSlave slave = j.createOnlineSlave();

        PsCleanerDescriptor descriptor = j.jenkins.getDescriptorByType(PsCleaner.PsCleanerDescriptor.class);
        descriptor.setSwitchedOff(true);
        LOGGER.info("Switched off " + descriptor.isSwitchedOff());

        FreeStyleProject job = j.createFreeStyleProject();
        job.setAssignedNode(slave);
        Util.setPreProcCleaner(job, new PsCleaner("org.jenkinsci.plugins.proccleaner.PsAllKiller"));

        FreeStyleBuild build = job.scheduleBuild2(0).get();
        String log = getLogAsString(build);
        assertTrue(log, log.contains("Process cleanup is globally turned off, contact your Jenkins administrator to turn it on."));

        job = j.createFreeStyleProject();
        job.setAssignedNode(slave);
        Util.setPostProcCleaner(job, new PsCleaner("org.jenkinsci.plugins.proccleaner.PsAllKiller"));

        build = job.scheduleBuild2(0).get();
        log = getLogAsString(build);
        assertTrue(log, log.contains("Process cleanup is globally turned off, contact your Jenkins administrator to turn it on."));
    }

    @Test public void runCleanup() throws Exception {
        FreeStyleProject job = j.createFreeStyleProject();
        setupKillers(job);
        job.scheduleBuild2(0).get();

        verify(preCleaner).clean(any(ProcCleaner.CleanRequest.class));
        verify(postCleaner).clean(any(ProcCleaner.CleanRequest.class));
    }

    @Test public void doNotRunCleanForMatrixParent() throws Exception {
        MatrixProject m = j.createProject(MatrixProject.class);
        m.setAxes(new AxisList(new TextAxis("axis", "a")));
        setupKillers(m);

        m.scheduleBuild2(0).get();

        verify(preCleaner).clean(any(ProcCleaner.CleanRequest.class));
        verify(postCleaner).clean(any(ProcCleaner.CleanRequest.class));
    }

    @Test public void doNotCleanOnSlaveWithOtherBuildRunning() throws Exception {
        FreeStyleProject running = j.createFreeStyleProject();

        final Proc[] p = new Proc[1];
        running.getBuildersList().add(addLongRunningStep(p, true));
        FreeStyleBuild running_build = running.scheduleBuild2(0).waitForStart();

        FreeStyleProject cleaned = j.createFreeStyleProject();
        setupKillers(cleaned);

        cleaned.scheduleBuild2(0).get();

        assertTrue("Long running process isn't running!", running_build.isBuilding());
        verify(preCleaner, never()).clean(any(ProcCleaner.CleanRequest.class));
        verify(postCleaner, never()).clean(any(ProcCleaner.CleanRequest.class));

        if(p[0] != null && p[0].isAlive()) {
            try {
                p[0].kill();
            } catch (Exception e) {
                e.toString();
                // Ignore any issue
            }
              assertFalse("Long running process is still alive!", p[0].isAlive());
        }
    }

    @Test public void runCleanupOnNonconcurrentJobs() throws Exception {
        FreeStyleProject job = j.createFreeStyleProject();
        setupKillers(job);
        job.scheduleBuild2(0).get();
        job.scheduleBuild2(0).get();

        verify(preCleaner, times(2)).clean(any(ProcCleaner.CleanRequest.class));
        verify(postCleaner, times(2)).clean(any(ProcCleaner.CleanRequest.class));
    }

    @Test public void saveJobWhileKillingIsInProgress() throws Exception {
        FreeStyleProject job = j.createFreeStyleProject();

        final BlockingCleaner preCleaner = new BlockingCleaner();
        final BlockingCleaner postCleaner = new BlockingCleaner();
        Util.setPreProcCleaner(job, preCleaner);
        Util.setPostProcCleaner(job, postCleaner);

        job.scheduleBuild2(0);

        preCleaner.started.block();
        job.save(); // Should not fail
        preCleaner.done.signal();

        postCleaner.started.block();
        job.save(); // Should not fail
        postCleaner.done.signal();
    }

    @Test public void performProcessCleanup() throws Exception {

        // Create and setup a job
        FreeStyleProject job = j.createFreeStyleProject();

        final Proc[] p = new Proc[1];
        job.getBuildersList().add(addLongRunningStep(p));

        // Configure plugin
        PsCleanerDescriptor descriptor = j.jenkins.getDescriptorByType(PsCleaner.PsCleanerDescriptor.class);

        // Attach PostBuildStep and configure it properly
        Util.setPostProcCleaner(job, new PsCleaner("org.jenkinsci.plugins.proccleaner.PsRecursiveKiller"));

        // Execute a job
        FreeStyleBuild build = null;
        try {
            build = job.scheduleBuild2(0).get();
        } catch (Exception e) {}

        assertNotNull("Long running process wasn't started!", p[0]);

        LOGGER.info("Job log: " + getLogAsString(build));

        boolean status;
        if (p[0] != null && (status = p[0].isAlive())) {
            try {
                p[0].kill();
            } catch (Exception e) {
                e.toString();
                // Ignore any issue
            }

            assertFalse("Long running process is still alive!", p[0].isAlive());
        }

        assertTrue("Killing of long running process wasn't performed by Process cleanup, plug-in doesn't have an effect on the target platform!",
                getLogAsString(build).matches("(?s).*Killing Process PID = .*, PPID = .*, ARGS = .*java.* -Xms00008m.*"));
    }

    private static final class BlockingCleaner extends PsCleaner {
        private static final long serialVersionUID = -2223282784314127148L;
        private final OneShotEvent started = new OneShotEvent();
        private final OneShotEvent done = new OneShotEvent();

        public BlockingCleaner() {
            super(null);
        }

        @Override
        public PsCleanerDescriptor getDescriptor() {
            return (PsCleanerDescriptor) Jenkins.getInstance().getDescriptor(PsCleaner.class);
        }

        @Override
        public void clean(CleanRequest request) throws IOException, InterruptedException {
            started.signal();
            done.block();
        }
    }

    private void setupKillers(AbstractProject<?, ?> project) throws Exception {
        Util.setPreProcCleaner(project, preCleaner);
        Util.setPostProcCleaner(project, postCleaner);
    }

    private TestBuilder addLongRunningStep(final Proc p[]) {
        return addLongRunningStep(p, false);
    }

    private TestBuilder addLongRunningStep(final Proc p[], final boolean wait) {
        return new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build,
                                   Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                ArgumentListBuilder args = new ArgumentListBuilder(
                        JavaEnvUtils.getJreExecutable("java"),
                        "-Xms00008m",
                        "-cp",
                        "target/test-classes/",
                        "org.jenkinsci.plugins.proccleaner.Sleeper",
                        "Hello"
                );
                if (Functions.isWindows()) {
                    args = args.toWindowsCommand();
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                final StreamTaskListener stlistener = new StreamTaskListener(out);
                p[0] = new Launcher.LocalLauncher(stlistener)
                        .launch()
                        .stderr(System.err)
                        .stdout(out)
                        .cmds(args)
                        .start();
                if (wait)
                    p[0].join();
                return true;
            }
        };
    }

    private static final Logger LOGGER = Logger.getLogger(PsCleanerTest.class.getName());

}
