package com.openshift.jenkins.plugins.pipeline;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.model.IImageStream;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCM;
import hudson.util.FormValidation;

public class OpenShiftImageStreams extends SCM {

	private String imageStreamName = "nodejs-010-centos7";
	private String tag = "latest";
    private String apiURL = "https://openshift.default.svc.cluster.local";
    private String namespace = "test";
    private String authToken = "";
    private String verbose = "false";
    private String lastCommitId = null;
    
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

	private String getCommitId(TaskListener listener) {
		boolean chatty = Boolean.parseBoolean(verbose);
		
		
    	// get oc client (sometime REST, sometimes Exec of oc command
    	Auth auth = Auth.createInstance(null);
    	TokenAuthorizationStrategy bearerToken = new TokenAuthorizationStrategy(Auth.deriveBearerToken(null, authToken, listener, chatty));		
    	IClient client = new ClientFactory().create(apiURL, auth);
    	client.setAuthorizationStrategy(bearerToken);
    	
    	
		IImageStream isImpl = client.get(ResourceKind.IMAGE_STREAM, imageStreamName, namespace);
		// we will treat the OpenShiftImageStream "imageID" as the Jenkins "commitId"
		String commitId = isImpl.getImageId(tag);

		// always print this
		listener.getLogger().println("\n\nOpenShiftImageStreams image ID used for Jenkins 'commitId' is " + commitId);
		return commitId;
    	
	}
	
	

	@Override
	public void checkout(Run<?, ?> build, Launcher launcher,
			FilePath workspace, TaskListener listener, File changelogFile,
			SCMRevisionState baseline) throws IOException, InterruptedException {
		String bldName = null;
		if (build != null)
			bldName = build.getDisplayName();
		listener.getLogger().println("\n\nOpenShiftImageStreams checkout called for " + bldName);
		// don't need to check out into jenkins workspace ... our bld/slave images/pods deal with that 
	}

	@Override
	public ChangeLogParser createChangeLogParser() {
		return null;
	}

	@Override
	public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> build,
			FilePath workspace, Launcher launcher, TaskListener listener)
			throws IOException, InterruptedException {
		String bldName = null;
		if (build != null)
			bldName = build.getDisplayName();
		listener.getLogger().println("\n\nOpenShiftImageStreams calcRevisionsFromBuild for " + bldName);
    	
    	String commitId = lastCommitId;
			
		ImageStreamRevisionState currIMSState = null;
		if (commitId != null)
			currIMSState = new ImageStreamRevisionState(commitId);
		
		listener.getLogger().println("\n\nOpenShiftImageStreams calcRevisionsFromBuild returning " + currIMSState);
		
		return currIMSState;
	}

	@Override
	protected PollingResult compareRemoteRevisionWith(
			AbstractProject<?, ?> project, Launcher launcher,
			FilePath workspace, TaskListener listener, SCMRevisionState baseline)
			throws IOException, InterruptedException {
		listener.getLogger().println("\n\nOpenShiftImageStreams compareRemoteRevisionWith");
    	
    	String commitId = this.getCommitId(listener);
		
		ImageStreamRevisionState currIMSState = null;
		if (commitId != null)
			currIMSState = new ImageStreamRevisionState(commitId);
		
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
		}
			
		return new PollingResult(baseline, currIMSState, changes ? Change.SIGNIFICANT : Change.NONE);
    				        		
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
    public static class DescriptorImpl extends SCMDescriptor {

        public DescriptorImpl() {
            super(OpenShiftImageStreams.class, null);
            load();
        }

        public FormValidation doCheckApiURL(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set apiURL");
            return FormValidation.ok();
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set namespace");
            return FormValidation.ok();
        }
        
        public FormValidation doCheckImageStreamName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set imageStreamName");
            return FormValidation.ok();
        }
        
        public FormValidation doCheckTag(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set tag");
            return FormValidation.ok();
        }
        
		@Override
		public String getDisplayName() {
			return "Monitor OpenShift ImageStreams";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws FormException {
			save();
			return super.configure(req, json);
		}
    }



}
