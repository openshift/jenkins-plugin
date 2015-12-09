package com.openshift.jenkins.plugins.pipeline;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map.Entry;

import com.openshift.restclient.authorization.TokenAuthorizationStrategy;

import jenkins.tasks.SimpleBuildStep;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;

public abstract class OpenShiftBasePostAction extends Recorder implements SimpleBuildStep, Serializable {

    protected String apiURL = "https://openshift.default.svc.cluster.local";
    protected String namespace = "test";
    protected String authToken = "";
    protected String verbose = "false";
    protected transient TokenAuthorizationStrategy bearerToken;
    protected transient Auth auth;

    public OpenShiftBasePostAction() {
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
    
	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean needsToRunAfterFinalized() {
		return true;
	}
	
    protected HashMap<String,String> setFields(HashMap<String,String> overridenFields, Field[] fields, EnvVars env, boolean chatty, TaskListener listener) throws IllegalArgumentException, IllegalAccessException {
		for (Field f : fields) {
			String key = f.getName();
			Object val = f.get(this);
			if (chatty)
				listener.getLogger().println("inspectBuildEnvAndOverrideFields found field " + key + " with current value " + val);
			if (val == null)
				continue;
			if (!(val instanceof String))
				continue;
			String envval = env.get(val);
			if (chatty)
				listener.getLogger().println("inspectBuildEnvAndOverrideFields for field " + key + " got val from build env " + envval);
			if (envval != null && envval.length() > 0) {
				f.set(this, envval);
				overridenFields.put(f.getName(), (String)val);
			}
		}
    	return overridenFields;
    }
    
	protected HashMap<String,String> inspectBuildEnvAndOverrideFields(EnvVars env, TaskListener listener, boolean chatty) {
		String className = this.getClass().getName();
		if (chatty)
			listener.getLogger().println("inspectBuildEnvAndOverrideFields class name " + className);
		HashMap<String,String> overridenFields = new HashMap<String,String>();
		try {
			if (env == null)
				return overridenFields;
			Class<?> c = Class.forName(className);
			overridenFields = this.setFields(overridenFields, c.getDeclaredFields(), env, chatty, listener);
			
			c = Class.forName(OpenShiftBasePostAction.class.getName());
			overridenFields = this.setFields(overridenFields, c.getDeclaredFields(), env, chatty, listener);
		} catch (ClassNotFoundException e1) {
			e1.printStackTrace(listener.getLogger());
		} catch (IllegalArgumentException e) {
			e.printStackTrace(listener.getLogger());
		} catch (IllegalAccessException e) {
			e.printStackTrace(listener.getLogger());
		}
		return overridenFields;
	}
	
	protected void restoreOverridenFields(HashMap<String,String> overrides, TaskListener listener) {
		String className = this.getClass().getName();
		try {
			Class<?> c = Class.forName(className);
			Class<?> bc = Class.forName(OpenShiftBasePostAction.class.getName());
			for (Entry<String, String> entry : overrides.entrySet()) {
				Field f = null;
				try {
					f = c.getDeclaredField(entry.getKey());
				} catch (NoSuchFieldException e) {
					f = bc.getDeclaredField(entry.getKey());
				}
				f.set(this, entry.getValue());
				
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace(listener.getLogger());
		} catch (SecurityException e) {
			e.printStackTrace(listener.getLogger());
		} catch (NoSuchFieldException e) {
			e.printStackTrace(listener.getLogger());
		} catch (IllegalArgumentException e) {
			e.printStackTrace(listener.getLogger());
		} catch (IllegalAccessException e) {
			e.printStackTrace(listener.getLogger());
		}
	}
	
    protected abstract boolean coreLogic(Launcher launcher, TaskListener listener, EnvVars env, Result result);
    
	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher,
			TaskListener listener) throws InterruptedException, IOException {
		boolean chatty = Boolean.parseBoolean(verbose);
		auth = Auth.createInstance(chatty ? listener : null);
    	bearerToken = new TokenAuthorizationStrategy(Auth.deriveBearerToken(run, authToken, listener, chatty));
    	EnvVars env = run.getEnvironment(listener);
		HashMap<String,String> overrides = inspectBuildEnvAndOverrideFields(env, listener, chatty);
		try {
			coreLogic(launcher, listener, run.getEnvironment(listener), null);
		} finally {
			this.restoreOverridenFields(overrides, listener);
		}
	}

	@Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
		boolean chatty = Boolean.parseBoolean(verbose);
		auth = Auth.createInstance(chatty ? listener : null);
    	bearerToken = new TokenAuthorizationStrategy(Auth.deriveBearerToken(build, authToken, listener, Boolean.parseBoolean(verbose)));
    	EnvVars env = build.getEnvironment(listener);
		HashMap<String,String> overrides = inspectBuildEnvAndOverrideFields(env, listener, chatty);
		try {
			return coreLogic(launcher, listener, env, build.getResult());
		} finally {
			this.restoreOverridenFields(overrides, listener);			
		}
    }


}
