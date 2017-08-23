package com.openshift.jenkins.plugins.pipeline;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Functionless stub class to establish a path to common *.jelly files (e.g.
 * st:include page="cluster.jelly"
 * class="com.openshift.jenkins.plugins.pipeline.Common" )
 */
public class Common extends AbstractDescribableImpl<Common> {

    @DataBoundConstructor
    public Common() {
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Common> {
        public String getDisplayName() {
            return "Common";
        }
    }

}
