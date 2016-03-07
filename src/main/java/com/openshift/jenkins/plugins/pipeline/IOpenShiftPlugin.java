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

import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.model.IBuild;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;

public interface IOpenShiftPlugin {
	
	String getBaseClassName();
	
	String getAuthToken();
	
	String getVerbose();
	
	Auth getAuth();
	
	void setAuth(Auth auth);
	
	TokenAuthorizationStrategy getToken();
	
	void setToken(TokenAuthorizationStrategy token);
	
	public String getApiURL();
	
	public String getNamespace();
	
	void setApiURL(String apiURL);
	
	void setNamespace(String namespace);
	    	
	boolean coreLogic(Launcher launcher, TaskListener listener, EnvVars env);
	
	default IClient getClient(TaskListener listener, String displayName) {
    	IClient client = new ClientFactory().create(getApiURL(), getAuth());
    	if (client != null) {
        	client.setAuthorizationStrategy(getToken());    		
    	} else {
	    	listener.getLogger().println(String.format(MessageConstants.CANNOT_GET_CLIENT, displayName, getApiURL()));
    	}
    	return client;
	}
	
	default IReplicationController getLatestReplicationController(IDeploymentConfig dc, IClient client) {
		int latestVersion = dc.getLatestVersionNumber();
		String repId = dc.getName() + "-" + latestVersion;
		return client.get(ResourceKind.REPLICATION_CONTROLLER, repId, getNamespace());
	}
	
	//TODO move to openshift-restclient-java IReplicationController
	default String getReplicationControllerState(IReplicationController rc) {
		return rc.getAnnotation("openshift.io/deployment.phase");
	}
	
	//TODO move to openshift-restclient-java IReplicationController
	default boolean isReplicationControllerScaledAppropriately(IReplicationController rc, boolean checkCount, int count) {
		boolean scaledAppropriately = false;
		// check state, and if needed then check replica count
		if (getReplicationControllerState(rc).equalsIgnoreCase("Complete")) {
			if (!checkCount) {
				scaledAppropriately = true;
			} else if (rc.getCurrentReplicaCount() == count) {
    			scaledAppropriately = true;
    		}
		}
		return scaledAppropriately;
	}
	
	default boolean doItCore(TaskListener listener, EnvVars env, Run<?, ?> run, AbstractBuild<?, ?> build, Launcher launcher) {
		boolean chatty = Boolean.parseBoolean(getVerbose());
		if (run == null && build == null)
			throw new RuntimeException("Either the run or build parameter must be set");
    	if (chatty)
    		listener.getLogger().println("\n\nOpenShift Pipeline Plugin: env vars for this job:  " + env);
		HashMap<String,String> overrides = inspectBuildEnvAndOverrideFields(env, listener, chatty);
		try {
			pullDefaultsIfNeeded(env, overrides, listener);
			setAuth(Auth.createInstance(chatty ? listener : null, getApiURL(), env));
	    	setToken(new TokenAuthorizationStrategy(Auth.deriveBearerToken(build != null ? build : run, getAuthToken(), listener, chatty)));
			return coreLogic(launcher, listener, env);
		} finally {
			this.restoreOverridenFields(overrides, listener);			
		}
	}

	default void doIt(Run<?, ?> run, FilePath workspace, Launcher launcher,
			TaskListener listener) throws InterruptedException, IOException {
    	EnvVars env = run.getEnvironment(listener);
    	this.doItCore(listener, env, run, null, launcher);
	}

    default boolean doIt(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
    	EnvVars env = build.getEnvironment(listener);
    	return this.doItCore(listener, env, null, build, launcher);
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
	    	listener.getLogger().println(String.format(MessageConstants.EXIT_BUILD_BAD, displayName, bldId, bldState));
			return false;
		} else {
			if (checkDeps) {    						
				if (Deployment.didAllImagesChangeIfNeeded(bldCfg, listener, chatty, client, namespace, wait)) {
    		    	listener.getLogger().println(String.format(MessageConstants.EXIT_BUILD_GOOD_DEPLOY_GOOD, displayName, bldId));
					return true;
				} else {
    		    	listener.getLogger().println(String.format(MessageConstants.EXIT_BUILD_GOOD_DEPLOY_BAD, displayName, bldId));
					return false;
				}
			} else {
		    	listener.getLogger().println(String.format(MessageConstants.EXIT_BUILD_GOOD_DEPLOY_IGNORED, displayName, bldId));
				return true;
			}
		}
    }
    
}
