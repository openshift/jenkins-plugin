package com.openshift.jenkins.plugins.pipeline.dsl;

import com.openshift.jenkins.plugins.pipeline.Auth;
import com.openshift.jenkins.plugins.pipeline.ParamVerify;
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
        this.name = name;
    }

    @DataBoundSetter
    public void setTag(String tag) {
        this.tag = tag;
    }

    @DataBoundSetter
    public void setApiURL(String apiURL) {
        this.apiURL = apiURL;
    }

    @DataBoundSetter
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @DataBoundSetter
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    @DataBoundSetter
    public void setVerbose(String verbose) {
        this.verbose = verbose;
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
    public OpenShiftImageStreams (String name, String tag, String namespace) {
        this.name = name;
        this.tag = tag;
        this.namespace = namespace;
    }

    @Nonnull
    @Override
    protected SCM createSCM() {
        com.openshift.jenkins.plugins.pipeline.OpenShiftImageStreams scm = new com.openshift.jenkins.plugins.pipeline.OpenShiftImageStreams(
                name,
                tag,
                apiURL,
                namespace,
                authToken,
                verbose);
        scm.setAuth(auth);
        return scm;
    }

    @Extension(optional=true) public static final class DescriptorImpl extends SCMStepDescriptor {

        public DescriptorImpl() {
            // Fail now if dependency plugin not loaded. Descriptor.<init> will actually fail anyway, but this is just to be sure.
            com.openshift.jenkins.plugins.pipeline.OpenShiftImageStreams.class.hashCode();
        }

        @Override
        public String getFunctionName() {
            return "openshiftImageStream";
        }

        @Override
        public String getDisplayName() {
            return "OpenShift ImageStreams";
        }

        public FormValidation doCheckApiURL(@QueryParameter String value)
                throws IOException, ServletException {
            // with some of the paths into the Image Stream SCM, the env vars typically available for the build steps are not available,
            // but for now, we fall back on "https://openshift.default.svc.cluster.local" if they do not specify
            return ParamVerify.doCheckApiURL(value);
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
            // If a namespace is not specified, the default one is going to be used
            return ParamVerify.doCheckNamespace(value);
        }

        public FormValidation doCheckTag(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckTag(value);
        }

        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckImageStreamName(value);
        }

        public FormValidation doCheckAuthToken(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckToken(value);
        }
    }
}
