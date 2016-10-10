package com.openshift.jenkins.plugins.pipeline;

import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPlugin;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPluginDescriptor;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IImageStream;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.*;
import hudson.scm.PollingResult.Change;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.Map;

//import com.openshift.restclient.authorization.TokenAuthorizationStrategy;

public class OpenShiftImageStreams extends SCM implements IOpenShiftPlugin {

    protected final static String DISPLAY_NAME = "OpenShift ImageStreams";

    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    protected final String imageStreamName;
    protected final String tag;
    protected final String apiURL;
    protected final String namespace;
    protected final String authToken;
    protected final String verbose;
    protected String lastCommitId = null;
    // marked transient so don't serialize these next 3 in the workflow plugin flow; constructed on per request basis
    //protected transient TokenAuthorizationStrategy bearerToken;
    protected transient Auth auth;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftImageStreams(String imageStreamName, String tag, String apiURL, String namespace, String authToken, String verbose) {
        this.imageStreamName = imageStreamName;
        this.tag = tag;
        this.apiURL = apiURL;
        this.namespace = namespace;
        this.authToken = authToken;
        this.verbose = verbose;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the initial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

    public String getApiURL() {
        return apiURL;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getAuthToken() {
        return authToken;
    }

    public String getImageStreamName() {
        return imageStreamName;
    }

    public String getTag() {
        return tag;
    }

    public String getVerbose() {
        return verbose;
    }

    public String getImageStreamName(Map<String, String> overrides) {
        return getOverride(getImageStreamName(), overrides);
    }

    public String getTag(Map<String, String> overrides) {
        return getOverride(getTag(), overrides);
    }

    protected String getCommitId(TaskListener listener, Map<String, String> overrides) {
        boolean chatty = Boolean.parseBoolean(verbose);

        // get oc client (sometime REST, sometimes Exec of oc command
        setAuth(Auth.createInstance(null, getApiURL(overrides), overrides));
        //setToken(new TokenAuthorizationStrategy(Auth.deriveBearerToken(null, authToken, listener, chatty)));
        // get oc client
        IClient client = this.getClient(listener, DISPLAY_NAME, overrides);


        IImageStream isImpl = client.get(ResourceKind.IMAGE_STREAM, getImageStreamName(overrides), getNamespace(overrides));
        // we will treat the OpenShiftImageStream "imageID" as the Jenkins "commitId"
        String commitId = isImpl.getImageId(tag);


        if (chatty)
            listener.getLogger().println("\n\nOpenShiftImageStreams image ID used for Jenkins 'commitId' is " + commitId);
        return commitId;

    }

    @Override
    public void checkout(Run<?, ?> build, Launcher launcher,
                         FilePath workspace, TaskListener listener, File changelogFile,
                         SCMRevisionState baseline) throws IOException, InterruptedException {
        boolean chatty = Boolean.parseBoolean(verbose);

        String bldName = null;
        if (build != null)
            bldName = build.getDisplayName();
        if (chatty)
            listener.getLogger().println("\n\nOpenShiftImageStreams checkout called for " + bldName);
        // nothing to actually check out in the classic SCM sense into the jenkins workspace
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return null;
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> build,
                                                   FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        EnvVars env = build.getEnvironment(listener);
        listener.getLogger().println(String.format(MessageConstants.SCM_CALC, DISPLAY_NAME, getImageStreamName(env), tag, getNamespace(env)));

        String commitId = lastCommitId;

        ImageStreamRevisionState currIMSState = null;
        if (commitId == null) {
            commitId = this.getCommitId(listener, build.getEnvironment(listener));
        }

        if (commitId != null) {
            currIMSState = new ImageStreamRevisionState(commitId);
            listener.getLogger().println(String.format(MessageConstants.SCM_LAST_REV, currIMSState.toString()));
        } else {
            listener.getLogger().println(MessageConstants.SCM_NO_REV);
        }


        return currIMSState;
    }

    protected PollingResult compareRemoteRevisionInternal(EnvVars env, Launcher launcher, TaskListener listener, SCMRevisionState baseline) {
        listener.getLogger().println(String.format(MessageConstants.SCM_COMP, DISPLAY_NAME, getImageStreamName(env), tag, getNamespace(env)));
        String commitId = this.getCommitId(listener, env);

        ImageStreamRevisionState currIMSState = null;
        if (commitId != null)
            currIMSState = new ImageStreamRevisionState(commitId);
        boolean chatty = Boolean.parseBoolean(verbose);
        if (chatty)
            listener.getLogger().println("\n\nOpenShiftImageStreams compareRemoteRevisionWith comparing baseline " + baseline +
                    " with lastest " + currIMSState);
        boolean changes = false;
        if (baseline != null && baseline instanceof ImageStreamRevisionState && currIMSState != null)
            changes = !currIMSState.equals(baseline);

        if (baseline == null && currIMSState != null) {
            changes = true;
        }

        if (changes) {
            lastCommitId = commitId;
            listener.getLogger().println(MessageConstants.SCM_CHANGE);
        } else {
            listener.getLogger().println(MessageConstants.SCM_NO_CHANGE);
        }

        return new PollingResult(baseline, currIMSState, changes ? Change.SIGNIFICANT : Change.NONE);
    }

    @Override
    public PollingResult compareRemoteRevisionWith(@Nonnull Job<?, ?> project,
                                                   @Nullable Launcher launcher,
                                                   @Nullable FilePath workspace,
                                                   @Nonnull TaskListener listener,
                                                   @Nonnull SCMRevisionState baseline)
            throws IOException, InterruptedException {
        return compareRemoteRevisionInternal(project.getEnvironment(null, listener), launcher, listener, baseline);
    }

    @Override
    protected PollingResult compareRemoteRevisionWith(
            AbstractProject<?, ?> project, Launcher launcher,
            FilePath workspace, TaskListener listener, SCMRevisionState baseline)
            throws IOException, InterruptedException {
        return compareRemoteRevisionInternal(project.getEnvironment(null, listener), launcher, listener, baseline);
    }

    @Override
    public SCMDescriptor<?> getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(
                getClass());
    }


    @Override
    public boolean requiresWorkspaceForPolling() {
        // our openshift itself and the cloud/master/slave plugin handles ramping up pods for builds ... those are our
        // "workspace"
        return false;
    }


    @Extension
    public static class DescriptorImpl extends SCMDescriptor implements IOpenShiftPluginDescriptor {

        public DescriptorImpl() {
            super(OpenShiftImageStreams.class, null);
            load();
        }


        public FormValidation doCheckTag(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckTag(value);
        }

        public FormValidation doCheckImageStreamName(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckImageStreamName(value);
        }

        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json)
                throws FormException {
            save();
            return super.configure(req, json);
        }
    }


    @Override
    public String getBaseClassName() {
        return OpenShiftImageStreams.class.getName();
    }

    @Override
    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    @Override
    public boolean coreLogic(Launcher launcher, TaskListener listener, Map<String, String> overrides) {
        return false;
    }

    @Override
    public Auth getAuth() {
        return auth;
    }

}
