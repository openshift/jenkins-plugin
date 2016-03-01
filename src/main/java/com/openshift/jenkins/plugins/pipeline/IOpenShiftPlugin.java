package com.openshift.jenkins.plugins.pipeline;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map.Entry;

import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.model.IBuild;

public interface IOpenShiftPlugin {
	
	String getBaseClassName();
	
	String getAuthToken();
	
	String getVerbose();
	
	void setAuth(Auth auth);
	
	void setToken(TokenAuthorizationStrategy token);
	
	public String getApiURL();
	
	public String getNamespace();
	
	void setApiURL(String apiURL);
	
	void setNamespace(String namespace);
	    	
	boolean coreLogic(Launcher launcher, TaskListener listener, EnvVars env);     

	default void doIt(Run<?, ?> run, FilePath workspace, Launcher launcher,
			TaskListener listener) throws InterruptedException, IOException {
		boolean chatty = Boolean.parseBoolean(getVerbose());
		setAuth(Auth.createInstance(chatty ? listener : null));
    	setToken(new TokenAuthorizationStrategy(Auth.deriveBearerToken(run, getAuthToken(), listener, chatty)));
    	EnvVars env = run.getEnvironment(listener);
    	if (chatty)
    		listener.getLogger().println("\n\nOpenShift Pipeline Plugin: env vars for this job:  " + env);
		HashMap<String,String> overrides = inspectBuildEnvAndOverrideFields(env, listener, chatty);
		try {
			pullDefaultsIfNeeded(env, overrides, listener);
			coreLogic(launcher, listener, run.getEnvironment(listener));
		} finally {
			this.restoreOverridenFields(overrides, listener);
		}
	}

    default boolean doIt(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
		boolean chatty = Boolean.parseBoolean(getVerbose());
		setAuth(Auth.createInstance(chatty ? listener : null));
    	setToken(new TokenAuthorizationStrategy(Auth.deriveBearerToken(build, getAuthToken(), listener, chatty)));
    	EnvVars env = build.getEnvironment(listener);
    	if (chatty)
    		listener.getLogger().println("\n\nOpenShift Pipeline Plugin: env vars for this job:  " + env);
		HashMap<String,String> overrides = inspectBuildEnvAndOverrideFields(env, listener, chatty);
		try {
			pullDefaultsIfNeeded(env, overrides, listener);
			return coreLogic(launcher, listener, env);
		} finally {
			this.restoreOverridenFields(overrides, listener);			
		}
    }
    
    default void pullDefaultsIfNeeded(EnvVars env, HashMap<String,String> overrides, TaskListener listener) {
    	boolean chatty = Boolean.parseBoolean(getVerbose());
    	if (chatty)
    		listener.getLogger().println(" before apiURL " + getApiURL() + " namespace " + getNamespace() + " env " + env);
		if (getApiURL() == null || getApiURL().length() == 0) {
			overrides.put("apiURL", getApiURL());
			if (env != null)
				setApiURL(env.get("KUBERNETES_SERVICE_HOST"));
			if (getApiURL() == null || getApiURL().length() == 0)
				setApiURL("https://openshift.default.svc.cluster.local");
		}
		if (getApiURL() != null && !getApiURL().startsWith("https://"))
			setApiURL("https://" + getApiURL());
		
		if (getNamespace() == null || getNamespace().length() == 0) {
			overrides.put("namespace", getNamespace());
			setNamespace(env.get("PROJECT_NAME"));
		}
    	if (chatty)
    		listener.getLogger().println(" after apiURL " + getApiURL() + " namespace " + getNamespace());
    }

	
    default HashMap<String,String> setFields(HashMap<String,String> overridenFields, Field[] fields, EnvVars env, boolean chatty, TaskListener listener) throws IllegalArgumentException, IllegalAccessException {
		for (Field f : fields) {
			String key = f.getName();
			// parameterized builds should only apply to instance variables, not static ones (and fyi, static fields in the classes extending this one will not be accessible by default)
			if (Modifier.isStatic(f.getModifiers()))
				continue;
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
    
	default HashMap<String,String> inspectBuildEnvAndOverrideFields(EnvVars env, TaskListener listener, boolean chatty) {
		String className = this.getClass().getName();
		if (chatty)
			listener.getLogger().println("inspectBuildEnvAndOverrideFields class name " + className);
		HashMap<String,String> overridenFields = new HashMap<String,String>();
		try {
			if (env == null)
				return overridenFields;
			Class<?> c = Class.forName(className);
			overridenFields = this.setFields(overridenFields, c.getDeclaredFields(), env, chatty, listener);
			
			c = Class.forName(getBaseClassName());
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
	
	default void restoreOverridenFields(HashMap<String,String> overrides, TaskListener listener) {
		String className = this.getClass().getName();
		try {
			Class<?> c = Class.forName(className);
			Class<?> bc = Class.forName(getBaseClassName());
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
	
    default boolean verifyBuild(long startTime, long wait, IClient client, String bldCfg, String bldId, String namespace, boolean chatty, TaskListener listener, String displayName, boolean checkDeps) {
		String bldState = null;
    	while (System.currentTimeMillis() < (startTime + wait)) {
			IBuild bld = client.get(ResourceKind.BUILD, bldId, namespace);
			bldState = bld.getStatus();
			if (chatty)
				listener.getLogger().println("\nOpenShiftBuilder post bld launch bld state:  " + bldState);
			if (!bldState.equals("Complete")) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			} else {
				break;
			}
		}
		if (bldState == null || !bldState.equals("Complete")) {
	    	listener.getLogger().println(String.format("\n\nExiting \"%s\" unsuccessfully; build \"%s\" has completed with status:  [%s].", displayName, bldId, bldState));
			return false;
		} else {
			if (checkDeps) {    						
				if (Deployment.didAllImagesChangeIfNeeded(bldCfg, listener, chatty, client, namespace, wait)) {
    		    	listener.getLogger().println(String.format("\n\nExiting \"%s\" successfully; build \"%s\" has completed with status:  [Complete].  All deployments with ImageChange triggers based on this build's output triggered off of the new image.", displayName, bldId));
					return true;
				} else {
    		    	listener.getLogger().println(String.format("\n\nExiting \"%s\" unsuccessfully; build \"%s\" has completed with status:  [Complete]. However, not all deployments with ImageChange triggers based on this build's output triggered off of the new image.", displayName, bldId));
					return false;
				}
			} else {
		    	listener.getLogger().println(String.format("\n\nExiting \"%s\" successfully; build \"%s\" has completed with status:  [Complete].", displayName, bldId));
				return true;
			}
		}
    }
    
}
