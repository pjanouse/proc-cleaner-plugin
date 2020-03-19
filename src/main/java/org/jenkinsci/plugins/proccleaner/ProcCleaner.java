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

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.Queue.Executable;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;

import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

public abstract class ProcCleaner implements Describable<ProcCleaner>, ExtensionPoint, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     *
     * @return pid of the ProcCleaner process
     */
    public static int getpid(){
        String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        int index = jvmName.indexOf('@');
        return Integer.parseInt(jvmName.substring(0,index));
    }

    /**
     * Prepare cleaner for action.
     *
     * This method is called on master (before deserialization).
     */
    public void setup() {}

    /**
     * Clean the processes.
     *
     * This method is called on slaves (after deserialization).
     */
    public abstract void clean(CleanRequest request) throws IOException, InterruptedException;

    public final void performCleanup(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        // Do not run for matrix parent
        if (build instanceof MatrixBuild) return;

        if (areThereOtherBuilds(build)) {
            listener.getLogger().println(
                    Messages.ProcCleaner_SlaveNotUsedExclusively()
            );
            return;
        }

        listener.getLogger().println(Messages.ProcCleaner_Running());

        setup();
        VirtualChannel c = launcher.getChannel();
        try {
            c.call(new CleanRequest(this, listener));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Callable to invoke particular cleaner holding all data it can need.
     *
     * @author ogondza
     */
    public static final class CleanRequest extends MasterToSlaveCallable<Void,Exception> {
        private static final long serialVersionUID = -3747367960274628624L;
        private final ProcCleaner cleaner;
        private final BuildListener listener;

        private CleanRequest(ProcCleaner procCleaner, BuildListener listener) {
            this.cleaner = procCleaner;
            this.listener = listener;
        }

        public Void call() throws Exception {
            cleaner.clean(this);
            return null;
        }

        public BuildListener getListener() {
            return listener;
        }
    }

    private boolean areThereOtherBuilds(AbstractBuild<?, ?> build) {
        Computer computer = Computer.currentComputer();
        for (Executor e: computer.getExecutors()) {
            if (runsDifferentBuilds(e, build)) return true;
        }

        for (Executor e: computer.getOneOffExecutors()) {
            if (runsDifferentBuilds(e, build)) return true;
        }

        return false;
    }

    /**
     * @return true if executor runs build different than current or its matrix parent.
     */
    private boolean runsDifferentBuilds(Executor executor, AbstractBuild<?, ?> current) {
        Executable candidate = executor.getCurrentExecutable();
        if (candidate == null) return false; // idle

        if (current.equals(candidate)) return false; // runs current

        if (current instanceof MatrixRun) {
            MatrixBuild parent = ((MatrixRun) current).getParentBuild();
            if (parent.equals(candidate)) return false; // runs parent
        }

        return true;
    }

    public ProcCleanerDescriptor getDescriptor() {
        Jenkins j = Jenkins.getInstance();
        assert j != null;

        return (ProcCleanerDescriptor) j.getDescriptor(getClass());
    }

    public static ExtensionList<ProcCleaner> all() {
        Jenkins j = Jenkins.getInstance();
        assert j != null;

        return j.getExtensionList(ProcCleaner.class);
    }

    public static class ProcCleanerDescriptor extends Descriptor<ProcCleaner> {

        protected ProcCleanerDescriptor(Class<? extends ProcCleaner> clazz) {
            super(clazz);
        }

        protected ProcCleanerDescriptor() {
        }

        @Override
        public String getDisplayName() {
            return clazz.getSimpleName();
        }
    }

}
