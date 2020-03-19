/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Author: pjanouse
 * Date: 2015/07/03
 */
public class PsBasedHPUXProcessTree extends PsBasedProcessTree {

    @Override
    public PsBasedProcessTree createProcessTreeFor(String user) throws InterruptedException, IOException {
        String[] cmd = {"ps","-u",user, "-f"};
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        final StringWriter writer = new StringWriter();

        try {
            IOUtils.copy(proc.getInputStream(), writer);
        } catch (IOException e) {
            LOGGER.warning("'ps' command invocation IOException occurred!");
        }

        BufferedReader reader = new BufferedReader(new StringReader(writer.toString()));
        if (proc.waitFor() != 0) {
            LOGGER.warning("'ps' command invocation " + ArrayUtils.toString(cmd) + " failed! Return code: "
                    + proc.exitValue());
            LOGGER.fine("Received output from 'ps' command invocation follows:");

            if(getLog() != null) {
                getLog().println("WARNING: 'ps' command invocation " + ArrayUtils.toString(cmd)
                        + " failed! Return code: " + proc.exitValue());
                getLog().println("DEBUG: Received output from 'ps' command invocation follows:");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                LOGGER.fine(line);
                if(getLog() != null)
                    getLog().println("DEBUG: '" + line + "'");
            }
            return null;
        }

        String line = reader.readLine(); // first line should be "UID PID PPID C STIME TTY TIME COMMAND" - skip it
        if (StringUtils.isEmpty(line)) {
            LOGGER.fine("Unrecognized output from 'ps' command invocation! Output is empty!");
            if (getLog() != null) {
                getLog().println("DEBUG: Unrecognized output from 'ps' command invocation! Output is empty!");
            }
            return null;

        }
        if (!line.matches("^\\s*UID\\s*PID\\s*PPID\\s*C\\s*STIME\\s*TTY\\s*TIME\\s*COMMAND\\s*$")) {
            LOGGER.fine("Unrecognized first output line from 'ps' command invocation! Was: '" + line + "'");
            if (getLog() != null) {
                getLog().println("DEBUG: Unrecognized first output line from 'ps' command invocation! Was: '"
                        + line + "'");
            }
            return null;
        }

        PsBasedProcessTree ptree = new PsBasedHPUXProcessTree();
        while ((line = reader.readLine()) != null) {
            String[] ps = line.trim().split(" +", 8);
            if (ps.length < 8) {
                LOGGER.fine("Unrecognized output line from 'ps' command invocation! Was: '" + line + "'");
                if(getLog() != null) {
                    getLog().println("DEBUG: Unrecognized output line from 'ps' command invocation! Was '"
                            + line + "'");
                }

                return null;
            }
            if(getLog() != null)
                getLog().println("DEBUG: '" + line + "'");
            ptree.addProcess(ps[1] + ' ' + ps[2] + ' ' + ps[7]);
        }

        if (!isSystemProcessesFilterOff()) {
            LOGGER.fine("Filter system processes");
            if (getLog() != null)
                getLog().println("DEBUG: 'Filter system processes'");

            List<PsProcess> toRemoveProcesses = new ArrayList<PsProcess>();
            for (PsProcess ps: ptree.getProcessList()) {
                if (blacklisted(ps)) {
                    toRemoveProcesses.add(ps);
                }
            }
            ptree.getProcessList().removeAll(toRemoveProcesses);
        } // systemProcessesFilterOff is On

        ptree.setSystemProcessesFilterOff(isSystemProcessesFilterOff());
        ptree.setLog(getLog());
        return ptree;
    }

    // On HP-UX don't filter anything yet
    private boolean blacklisted(PsProcess p){
        // Filtered stuff should be placed here
        // if (p.getArgs().contains("systemd --user")) {
        //     return true;
        // }

        return false;
    }

    private static final Logger LOGGER = Logger.getLogger(PsBasedHPUXProcessTree.class.getName());
}
