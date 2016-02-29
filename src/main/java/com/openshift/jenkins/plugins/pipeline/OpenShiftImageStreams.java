package com.openshift.jenkins.plugins.pipeline;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

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

import hudson.EnvVars;
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
	
	protected final static String DISPLAY_NAME = "OpenShift ImageStreams";
	
	private String imageStreamName;
	private String tag;
    private String apiURL;
    private String namespace;
    private String authToken;
    private String verbose;
    private String lastCommitId = null;
    private transient HashMap<String,String> overrides = new HashMap<String,String>();
    
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

    protected void pullDefaultsIfNeeded(EnvVars env, TaskListener listener) {
    	boolean chatty = Boolean.parseBoolean(verbose);
    	if (chatty)
    		listener.getLogger().println("apiURL before " + apiURL);
		if (apiURL == null || apiURL.length() == 0) {
			overrides.put("apiURL", apiURL);
			if (env != null)
				apiURL = env.get("KUBERNETES_SERVICE_HOST");
			if (apiURL == null || apiURL.length() == 0)
				apiURL = "https://openshift.default.svc.cluster.local";
		}
		if (apiURL != null && !apiURL.startsWith("https://"))
			apiURL = "https://" + apiURL;
		if (chatty)
			listener.getLogger().println(" apiURL after " + apiURL);

		if (chatty)
			listener.getLogger().println(" namespace before: " + namespace);
		if (namespace == null || namespace.length() == 0) {
			overrides.put("namespace", namespace);
			namespace = env.get("PROJECT_NAME");
		}
		if (chatty)
			listener.getLogger().println(" namespace after: " + namespace);
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
		

		if (chatty)
			listener.getLogger().println("\n\nOpenShiftImageStreams image ID used for Jenkins 'commitId' is " + commitId);
		return commitId;
    	
	}
	
	

	@Override
	public void checkout(Run<?, ?> build, Launcher launcher,
			FilePath workspace, TaskListener listener, File changelogFile,
			SCMRevisionState baseline) throws IOException, InterruptedException {
		boolean chatty = Boolean.parseBoolean(verbose);
		pullDefaultsIfNeeded(build.getEnvironment(listener), listener);

		String bldName = null;
		if (build != null)
			bldName = build.getDisplayName();
		if (chatty)
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
		pullDefaultsIfNeeded(build.getEnvironment(listener), listener);
    	listener.getLogger().println(String.format("\n\nPolling \"%s\":  the image stream \"%s\" and tag \"%s\" from the project \"%s\".", DISPLAY_NAME, imageStreamName, tag, namespace));
    	
    	String commitId = lastCommitId;
			
		ImageStreamRevisionState currIMSState = null;
		if (commitId != null) {
			currIMSState = new ImageStreamRevisionState(commitId);
			listener.getLogger().println(String.format("  Last revision:  [%s]", currIMSState.toString()));
		} else {
	    	listener.getLogger().println("  First time through, no revision state available.");
		}
		
		
		return currIMSState;
	}

	@Override
	protected PollingResult compareRemoteRevisionWith(
			AbstractProject<?, ?> project, Launcher launcher,
			FilePath workspace, TaskListener listener, SCMRevisionState baseline)
			throws IOException, InterruptedException {
    			
    	String commitId = this.getCommitId(listener);
		
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
			listener.getLogger().println("\n\n A revision change was found this polling cycle.");
		} else {
			listener.getLogger().println("\n\n No revision change found this polling cycle.");
		}
		
		if (chatty)
			listener.getLogger().println(" overrides " + overrides);
		
		if (overrides.containsKey("apiURL"))
			apiURL = overrides.get("apiURL");
		if (overrides.containsKey("namespace"))
			namespace = overrides.get("namespace");
		overrides.clear();
		
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
                return FormValidation.warning("Unless you specify a value here, one of the default API endpoints will be used; see this field's help or https://github.com/openshift/jenkins-plugin#common-aspects-across-the-rest-based-functions-build-steps-scm-post-build-actions for details");
            return FormValidation.ok();
        }

        public FormValidation doCheckDepCfg(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("You must set a DeploymentConfig name");
            return FormValidation.ok();
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("You must specify the name of the project where the image stream resides");
            return FormValidation.ok();
        }
        
        public FormValidation doCheckTag(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set the name of the image stream tag you want to poll");
            return FormValidation.ok();
        }
        
        public FormValidation doCheckImageStreamName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set the name of the image stream you want to poll");
            return FormValidation.ok();
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



}
