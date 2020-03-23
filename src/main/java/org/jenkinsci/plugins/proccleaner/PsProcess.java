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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public abstract class PsProcess {

    private final int pid;
    private final int ppid;
    private final String args;
    private PsBasedProcessTree ptree;


    public PsProcess(int pid, int ppid, String args, PsBasedProcessTree ptree) {
        this.pid = pid;
        this.ppid = ppid;
        this.args = args;
        this.ptree = ptree;
    }

    public int getPid() {
        return pid;
    }

    public int getPpid() {
        return ppid;
    }

    public String getArgs() {
        return args;
    }

    public PsProcess getParent() {
        return ptree.getByPid(ppid);
    }

    public List<PsProcess> getChildren() {
        List<PsProcess> children = new ArrayList<PsProcess>();
        for(PsProcess p : ptree.getProcessList())
            if(this == p.getParent())
                children.add(p);

        return children;
    }

    public void killRecursively() {
        for(PsProcess p : getChildren())
            p.killRecursively();
        kill();
    }

    public void kill() {
        LOGGER.fine("Killing " + pid + ", args: " + args);
        if(ptree.getLog() != null)
            ptree.getLog().println("Killing " + this);
        killHard();
    }

    public void killHard() {
        kill(9);
    }

    public abstract void kill(int signum);

    public void killAllExceptMe() {
        Map<Integer, PsProcess> ph = getParentHierarchy(this);
        for(PsProcess p : ptree.getProcessList())
            if(this != p && !ph.containsKey(p.pid)) // don't kill myself (and parent) //TODO should contain whole possible hierarchy
                p.kill();
    }

    public Map<Integer, PsProcess> getParentHierarchy(PsProcess p) {
        Map<Integer, PsProcess> ph = new HashMap<Integer, PsProcess>();
        while((p = p.getParent()) != null) {
            //p = p.getParent();
            ph.put(p.pid, p);
        }
        return ph;
    }

    @Override
    public String toString() {
        return "Process PID = " + pid + ", PPID = " + ppid + ", ARGS = " + args;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof PsProcess)
            if(pid == ((PsProcess)o).pid)
                return true;
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid);
    }

    // private static final long serialVersionUID = 1L;
    
    private static final Logger LOGGER = Logger.getLogger(PsProcess.class.getName());
}
