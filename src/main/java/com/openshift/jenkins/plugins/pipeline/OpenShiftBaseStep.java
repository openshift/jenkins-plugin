package com.openshift.jenkins.plugins.pipeline;

import java.io.IOException;
import java.io.Serializable;

import com.openshift.restclient.authorization.TokenAuthorizationStrategy;

import jenkins.tasks.SimpleBuildStep;
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

	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher,
			TaskListener listener) throws InterruptedException, IOException {
		this.doIt(run, workspace, launcher, listener);
	}

	@Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
		return this.doIt(build, launcher, listener);
    }


}
