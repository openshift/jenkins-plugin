package com.openshift.jenkins.plugins.pipeline.dsl;

import java.io.IOException;
import java.io.Serializable;

import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundSetter;

import com.openshift.jenkins.plugins.pipeline.Auth;
import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftPlugin;
//import com.openshift.restclient.authorization.TokenAuthorizationStrategy;

import jenkins.tasks.SimpleBuildStep;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;

public abstract class OpenShiftBaseStep extends AbstractStepImpl  implements SimpleBuildStep, Serializable, IOpenShiftPlugin {
	
    protected String apiURL;
    protected String namespace;
    protected String authToken;
    protected String verbose;
    // marked transient so don't serialize these next 2 in the workflow plugin flow; constructed on per request basis
    //protected transient TokenAuthorizationStrategy bearerToken;
    protected transient Auth auth;
    
    protected OpenShiftBaseStep() {
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the initial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

    public String getApiURL() {
		return apiURL;
	}
    
    @DataBoundSetter public void setApiURL(String apiURL) {
    	this.apiURL = apiURL != null ? apiURL.trim() : null;
    }

	public String getNamespace() {
		return namespace;
	}
	
	@DataBoundSetter public void setNamespace(String namespace) {
		this.namespace = namespace != null ? namespace.trim() : null;
	}
	
	public String getAuthToken() {
		return authToken;
	}
	
	@DataBoundSetter public void setAuthToken(String authToken) {
		this.authToken = authToken != null ? authToken.trim() : null;
	}
	
    public String getVerbose() {
		return verbose;
	}
    
    @DataBoundSetter public void setVerbose(String verbose) {
    	this.verbose = verbose != null ? verbose.trim() : null;
    }
    
    @Override
	public void setAuth(Auth auth) {
		this.auth = auth;
	}

	@Override
	public Auth getAuth() {
		return auth;
	}

/*	@Override
	public TokenAuthorizationStrategy getToken() {
		return bearerToken;
	}

	@Override
	public void setToken(TokenAuthorizationStrategy token) {
		this.bearerToken = token;
	}
*/
	@Override
	public String getBaseClassName() {
		return OpenShiftBaseStep.class.getName();
	}

	// this is the workflow plugin path
	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher,
			TaskListener listener) throws InterruptedException, IOException {
		this.doIt(run, workspace, launcher, listener);
	}

	// this is the classic jenkins build step path
	@Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
		return this.doIt(build, launcher, listener);
    }


}
