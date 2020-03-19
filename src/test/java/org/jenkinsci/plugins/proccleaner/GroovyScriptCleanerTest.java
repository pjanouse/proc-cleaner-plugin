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

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class GroovyScriptCleanerTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test @Issue("SECURITY-489") @LocalData
    @SuppressWarnings("deprecation")
    public void killGroovySupport() throws Exception {
        final String EXPECTED_MESSAGE = Messages.GroovyScriptCleaner_DisplayName();

        FreeStyleProject a = j.jenkins.getItem("a", j.jenkins, FreeStyleProject.class);
        FreeStyleBuild build = a.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, build);
        j.assertLogContains(EXPECTED_MESSAGE, build);

        HtmlPage page = j.createWebClient().getPage(a, "configure");
        String resp = page.getWebResponse().getContentAsString();
        assertThat(resp, containsString(EXPECTED_MESSAGE));
        assertThat(resp, containsString("println 'precleaner'; return 'precleaned!'"));
        assertThat(resp, containsString("println 'postcleaner'; return 'postcleaned!'"));

        j.configRoundtrip(a);

        page = j.createWebClient().getPage(a, "configure");
        assertThat(page.getWebResponse().getContentAsString(), containsString(EXPECTED_MESSAGE));

        assertEquals(
                "println 'precleaner'; return 'precleaned!'",
                ((GroovyScriptCleaner) a.getBuildWrappersList().get(PreBuildCleanup.class).getCleaner()).getScript()
        );

        assertEquals(
                "println 'postcleaner'; return 'postcleaned!'",
                ((GroovyScriptCleaner) a.getPublishersList().get(PostBuildCleanup.class).getCleaner()).getScript()
        );
    }
}
