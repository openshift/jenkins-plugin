package com.openshift.jenkins.plugins.pipeline;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class NameValuePair extends AbstractDescribableImpl<NameValuePair> {

    protected final String name;
    protected final String value;

    @DataBoundConstructor
    public NameValuePair(String name, String value) {
        this.name = name != null ? name.trim() : null;
        this.value = value != null ? value.trim() : null;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<NameValuePair> {
        public String getDisplayName() {
            return "Name/Value Pair";
        }

        public FormValidation doCheckName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }
    }

}
