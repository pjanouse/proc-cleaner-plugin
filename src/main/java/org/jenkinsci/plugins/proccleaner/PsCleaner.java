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

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class PsCleaner extends ProcCleaner {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(PsCleaner.class.getName());

    private final String killerType;
    private final PsKiller killer;
    private boolean switchedOff;
    private boolean systemProcessesFilterOff;

    @DataBoundConstructor
    public PsCleaner(String killerType) {
        this.killerType = killerType;
        this.killer = PsKiller.all().getDynamic(killerType);
    }

    public String getKillerType() {
        return killerType;
    }

    @Override
    public void setup() {
        switchedOff = getDescriptor().isSwitchedOff();
        systemProcessesFilterOff = getDescriptor().isSystemProcessesFilterOff();
    }

    @Override
    public void clean(CleanRequest request) throws IOException, InterruptedException {
        if(switchedOff) {
            request.getListener().getLogger().println("Process cleanup is globally turned off, contact your Jenkins administrator to turn it on.");
            return;
        }

        try {
            String osName = System.getProperty( "os.name" ).toLowerCase();
            String className = null;
            String methodName = "getUsername";
            String user = null;

            // https://issues.jenkins-ci.org/browse/JENKINS-53739
            if (osName.contains("windows")) {
                className = "com.sun.security.auth.module.NTSystem";
                methodName = "getName";
            } else if (osName.contains("linux")) {
                className = "com.sun.security.auth.module.UnixSystem";
            } else if (osName.contains("solaris") || osName.contains("sunos")) {
                className = "com.sun.security.auth.module.SolarisSystem";
            }
            if (className != null) {
                try {
                    Class<?> c = Class.forName(className);
                    Method method = c.getDeclaredMethod(methodName);
                    Object o = c.newInstance();
                    user = (String) method.invoke(o);
                } catch (Throwable e) {
                    LOGGER.log(Level.WARNING, "Unexpected exception occurred: ", e);
                }
            }
            if (user == null) {
                user = System.getProperty("user.name", "");
            }
            killer.kill(user, request.getListener().getLogger(), systemProcessesFilterOff);
        } catch(IOException e) {
            e.printStackTrace();
            throw e;
        } catch(InterruptedException e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    public PsCleanerDescriptor getDescriptor() {
        Jenkins j = Jenkins.getInstance();
        assert j != null;

        return (PsCleanerDescriptor) j.getDescriptor(getClass());
    }

    @Extension
    public static class PsCleanerDescriptor extends ProcCleanerDescriptor {

        private boolean switchedOff;

        private boolean systemProcessesFilterOff;

        public PsCleanerDescriptor() {
            load();
        }

        public boolean isSwitchedOff() {
            return switchedOff;
        }

        public boolean isSystemProcessesFilterOff() {
            return systemProcessesFilterOff;
        }

        @Override
        public String getDisplayName() {
            return Messages.PsCleaner_DisplayName();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) {
            switchedOff = json.getBoolean("switchedOff");
            systemProcessesFilterOff = json.getBoolean("systemProcessesFilterOff");
            save();
            return true;
        }

        /*package*/ void setSwitchedOff(boolean value) {
            switchedOff = value;
        }

        /*package*/ void setSystemProcessesFilterOff(boolean value) {
            systemProcessesFilterOff = value;
        }
    }
}