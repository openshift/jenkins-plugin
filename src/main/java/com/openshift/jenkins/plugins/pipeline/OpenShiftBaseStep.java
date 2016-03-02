package com.openshift.jenkins.plugins.pipeline;

import java.io.IOException;
import java.io.Serializable;

import com.openshift.restclient.authorization.TokenAuthorizationStrategy;

import jenkins.tasks.SimpleBuildStep;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;

public abstract class OpenShiftBaseStep extends Builder  implements SimpleBuildStep, Serializable, IOpenShiftPlugin {
	
    protected String apiURL;
    protected String namespace;
    protected String authToken;
    protected String verbose;
    // marked transient so don't serialize these next 2 in the workflow plugin flow; constructed on per request basis
    protected transient TokenAuthorizationStrategy bearerToken;
    protected transient Auth auth;

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
    
    @Override
	public void setAuth(Auth auth) {
		this.auth = auth;
	}

	@Override
	public Auth getAuth() {
		return auth;
	}

	@Override
	public TokenAuthorizationStrategy getToken() {
		return bearerToken;
	}

	@Override
	public void setToken(TokenAuthorizationStrategy token) {
		this.bearerToken = token;
	}

	@Override
	public void setApiURL(String apiURL) {
		this.apiURL = apiURL;
	}

	@Override
	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}
    
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
