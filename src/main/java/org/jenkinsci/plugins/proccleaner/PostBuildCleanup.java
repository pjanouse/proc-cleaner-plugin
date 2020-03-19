/*
 * The MIT License
 *
 * Copyright (c) 2012 Red Hat, Inc.
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

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import java.io.IOException;
import java.util.Collection;

import org.kohsuke.stapler.DataBoundConstructor;

public class PostBuildCleanup extends Notifier {

    private final ProcCleaner cleaner;

    @DataBoundConstructor
    public PostBuildCleanup(ProcCleaner cleaner) {
        this.cleaner = cleaner;
    }

    public ProcCleaner getCleaner() {
        return cleaner;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        cleaner.performCleanup(build, launcher, listener);
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension(ordinal = -999)
    public static final class DescriptorImpl extends
            BuildStepDescriptor<Publisher> {

        @Override
        public String getDisplayName() {
            return Messages.PostBuildCleanup_DisplayName();
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean isApplicable(Class clazz) {
            return true;
        }

        public static Collection<Descriptor<ProcCleaner>> getCleanerDescriptors(ProcCleaner current) {
            return PreBuildCleanup.DescriptorImpl.getCleanerDescriptors(current);
        }
    }
}
