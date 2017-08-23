package com.openshift.jenkins.plugins.pipeline.dsl;

import com.openshift.jenkins.plugins.pipeline.Auth;
import com.openshift.jenkins.plugins.pipeline.ParamVerify;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPluginDescriptor;
import hudson.Extension;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 * SCMStep for OpenShiftImageStreams
 */
public class OpenShiftImageStreams extends SCMStep {

    protected String name;
    protected String tag;
    protected String apiURL;
    protected String namespace;
    protected String authToken;
    protected String verbose;

    // marked transient so don't serialize; constructed on per request basis
    protected transient Auth auth;

    @DataBoundSetter
    public void setName(String name) {
        this.name = name != null ? name.trim() : null;
    }

    @DataBoundSetter
    public void setTag(String tag) {
        this.tag = tag != null ? tag.trim() : null;
    }

    @DataBoundSetter
    public void setApiURL(String apiURL) {
        this.apiURL = apiURL != null ? apiURL.trim() : null;
    }

    @DataBoundSetter
    public void setNamespace(String namespace) {
        this.namespace = namespace != null ? namespace.trim() : null;
    }

    @DataBoundSetter
    public void setAuthToken(String authToken) {
        this.authToken = authToken != null ? authToken.trim() : null;
    }

    @DataBoundSetter
    public void setVerbose(String verbose) {
        this.verbose = verbose != null ? verbose.trim() : null;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public String getApiURL() {
        return apiURL;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getAuthToken() {
        return authToken;
    }

    public String getVerbose() {
        return verbose;
    }

    public Auth getAuth() {
        return auth;
    }

    @DataBoundConstructor
    public OpenShiftImageStreams(String name, String tag, String namespace) {
        this.name = name != null ? name.trim() : null;
        this.tag = tag != null ? tag.trim() : null;
        this.namespace = namespace != null ? namespace.trim() : null;
    }

    @Nonnull
    @Override
    protected SCM createSCM() {
        com.openshift.jenkins.plugins.pipeline.OpenShiftImageStreams scm = new com.openshift.jenkins.plugins.pipeline.OpenShiftImageStreams(
                name, tag, apiURL, namespace, authToken, verbose);
        scm.setAuth(auth);
        return scm;
    }

    @Extension(optional = true)
    public static final class DescriptorImpl extends SCMStepDescriptor
            implements IOpenShiftPluginDescriptor {

        public DescriptorImpl() {
            // Fail now if dependency plugin not loaded. Descriptor.<init> will
            // actually fail anyway, but this is just to be sure.
            com.openshift.jenkins.plugins.pipeline.OpenShiftImageStreams.class
                    .hashCode();
        }

        @Override
        public String getFunctionName() {
            return "openshiftImageStream";
        }

        @Override
        public String getDisplayName() {
            return "OpenShift ImageStreams";
        }

        public FormValidation doCheckTag(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckTag(value);
        }

        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckImageStreamName(value);
        }
    }
}
