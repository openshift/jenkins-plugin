package com.openshift.jenkins.plugins.pipeline;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

public class OpenShiftImageStreams extends SCM implements IOpenShiftPlugin {
	
	protected final static String DISPLAY_NAME = "OpenShift ImageStreams";
	
	protected final String imageStreamName;
	protected final String tag;
    protected final String apiURL;
    protected final String namespace;
    protected final String authToken;
    protected final String verbose;
    protected String lastCommitId = null;
    // marked transient so don't serialize these next 3 in the workflow plugin flow; constructed on per request basis
    protected transient TokenAuthorizationStrategy bearerToken;
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

	protected String getCommitId(TaskListener listener, EnvVars env, HashMap<String,String> overrides) {
		boolean chatty = Boolean.parseBoolean(verbose);
		
    	// get oc client (sometime REST, sometimes Exec of oc command
    	setAuth(Auth.createInstance(null, getApiURL(overrides), env));
    	setToken(new TokenAuthorizationStrategy(Auth.deriveBearerToken(null, authToken, listener, chatty)));		
    	// get oc client 
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    	
    	
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
		HashMap<String,String> overrides = inspectBuildEnvAndOverrideFields(build.getEnvironment(listener), listener, Boolean.parseBoolean(getVerbose(null)));
		pullDefaultsIfNeeded(build.getEnvironment(listener), overrides, listener);
    	listener.getLogger().println(String.format(MessageConstants.SCM_CALC, DISPLAY_NAME, imageStreamName, tag, namespace));
    	
    	String commitId = lastCommitId;
			
		ImageStreamRevisionState currIMSState = null;
		if (commitId != null) {
			currIMSState = new ImageStreamRevisionState(commitId);
			listener.getLogger().println(String.format(MessageConstants.SCM_LAST_REV, currIMSState.toString()));
		} else {
	    	listener.getLogger().println(MessageConstants.SCM_NO_REV);
		}
		
		
		return currIMSState;
	}

	@Override
	protected PollingResult compareRemoteRevisionWith(
			AbstractProject<?, ?> project, Launcher launcher,
			FilePath workspace, TaskListener listener, SCMRevisionState baseline)
			throws IOException, InterruptedException {
    	listener.getLogger().println(String.format(MessageConstants.SCM_COMP, DISPLAY_NAME, imageStreamName, tag, namespace));
		HashMap<String,String> overrides = inspectBuildEnvAndOverrideFields(project.getEnvironment(null,listener), listener, Boolean.parseBoolean(getVerbose(null)));
		pullDefaultsIfNeeded(project.getEnvironment(null,listener), overrides, listener);
    	String commitId = this.getCommitId(listener, project.getEnvironment(null, listener), overrides);
		
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
		
		if (chatty)
			listener.getLogger().println(" overrides " + overrides);
		
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
        	// with some of the paths into the Image Stream SCM, the env vars typically available for the build steps are not available,
        	// but for now, we fall back on "https://openshift.default.svc.cluster.local" if they do not specify
            return ParamVerify.doCheckApiURL(value);
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
        	// with some of the paths into the Image Stream SCM, the env vars typically available for the build steps are not available,
        	// we we have to force the user to specify the project/namespace
        	FormValidation fv = ParamVerify.doCheckNamespace(value);
        	if (fv.kind == FormValidation.Kind.OK)
        		return fv;
        	return FormValidation.error("Please specify the name of the project");
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
	public void setToken(TokenAuthorizationStrategy token) {
		this.bearerToken = token;
	}

	@Override
	public boolean coreLogic(Launcher launcher, TaskListener listener,
			EnvVars env, Map<String,String> overrides) {
		return false;
	}

	@Override
	public Auth getAuth() {
		return auth;
	}

	@Override
	public TokenAuthorizationStrategy getToken() {
		return bearerToken;
	}



}
