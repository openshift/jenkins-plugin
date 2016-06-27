package com.openshift.jenkins.plugins.pipeline.model;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jboss.dmr.ModelNode;

import com.openshift.internal.restclient.model.DeploymentConfig;
import com.openshift.jenkins.plugins.pipeline.Auth;
import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.restclient.ClientBuilder;
import com.openshift.restclient.IClient;
import com.openshift.restclient.OpenShiftException;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.model.IBuild;
import com.openshift.restclient.model.IBuildConfig;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;
import com.openshift.restclient.model.IResource;
import com.openshift.restclient.model.deploy.IDeploymentImageChangeTrigger;
import com.openshift.restclient.model.deploy.IDeploymentTrigger;
import com.openshift.restclient.model.route.IRoute;

public interface IOpenShiftPlugin {
	
	// arg1=resource type, arg2=object name
	public static final String ANNOTATION_FAILURE = "\nWARNING: Failed to annotate %s %s with job information.";
	// Jenkins env variable keys
	// This key contains the full url to the jenkins job instance that is currently running.
	public static final String BUILD_URL_ENV_KEY = "BUILD_URL";
	// this key contains the jenkins job name
	public static final String JOB_NAME = "JOB_NAME";
	// this key contains the jenkins job instance number
	public static final String BUILD_NUMBER = "BUILD_NUMBER";
	// Openshift annotation keys
	// This annotation will contain a the full url of the jenkins job instance that lead
	// to this object (e.g. build, deployment) being created.
	public static final String BUILD_URL_ANNOTATION = "openshift.io/jenkins-build-uri";
	
	public static final String NAMESPACE_FILE = "/run/secrets/kubernetes.io/serviceaccount/namespace";

	String getBaseClassName();
	
	String getAuthToken();
	
	default String getAuthToken(Map<String,String> overrides) {
		return getOverride(getAuthToken(), overrides);
	}
	
	String getVerbose();
	
	default String getVerbose(Map<String,String> overrides) {
		return getOverride(getVerbose(), overrides);
	}
	
	Auth getAuth();
	
	void setAuth(Auth auth);
	
	TokenAuthorizationStrategy getToken();
	
	void setToken(TokenAuthorizationStrategy token);
	
	public String getApiURL();
	
	default String getApiURL(Map<String,String> overrides) {
		String val = getOverride(getApiURL(), overrides);
		if ((val == null || val.length() == 0) && overrides != null && overrides.containsKey("KUBERNETES_SERVICE_HOST")) {
			val = overrides.get("KUBERNETES_SERVICE_HOST");
		}
		if (val != null && val.length() > 0 && !val.startsWith("https://"))
			val = "https://" + val;
		if (val == null || val.length() == 0)
			val = "https://openshift.default.svc.cluster.local";
		return val;
	}
	
	public String getNamespace();
	
	default String getNamespace(Map<String,String> overrides) {
		String val = getOverride(getNamespace(), overrides);
		if (val.length() == 0 && overrides != null && overrides.containsKey("PROJECT_NAME")) {
			val = overrides.get("PROJECT_NAME");
		} else {
			File f = new File(NAMESPACE_FILE);
			if (f.exists())
				val = Auth.pullTokenFromFile(f, null);
		}
		return val;
	}
	
	boolean coreLogic(Launcher launcher, TaskListener listener, Map<String,String> overrides);
	
	default IClient getClient(TaskListener listener, String displayName, Map<String,String> overrides) {
		IClient client = new ClientBuilder(getApiURL(overrides)).sslCertificateCallback(getAuth()).resourceFactory(getToken()).sslCertificate(getApiURL(overrides), getAuth().getCert()).build();
    	if (client == null) {
	    	listener.getLogger().println(String.format(MessageConstants.CANNOT_GET_CLIENT, displayName, getApiURL(overrides)));
    	}
    	return client;
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
	
	default Map<String, String> consolidateEnvVars(TaskListener listener, EnvVars env, Run<?, ?> run, AbstractBuild<?, ?> build, Launcher launcher, boolean chatty) {
		// EnvVars extends TreeMap
		TreeMap<String,String> overrides = new TreeMap<String, String>();
		// merge from all potential sources
    	if (run != null) {
    		try {
				EnvVars runEnv = run.getEnvironment(listener);
				if (chatty)
					listener.getLogger().println("run env vars:  " + runEnv);
				overrides.putAll(runEnv);
			} catch (IOException | InterruptedException e) {
				if (chatty)
					e.printStackTrace(listener.getLogger());
			}
    	}
    	
    	if (build != null) {
    		try {
				EnvVars buildEnv = build.getEnvironment(listener);
				if (chatty)
					listener.getLogger().println("build env vars:  " + buildEnv);
				overrides.putAll(buildEnv);
			} catch (IOException | InterruptedException e) {
				if (chatty)
					e.printStackTrace(listener.getLogger());
			}
    	}
    	
		try {
			EnvVars computerEnv = null;
			Computer computer = Computer.currentComputer();
			if (computer != null) {
				computerEnv = computer.getEnvironment();
			} else {
				computer = launcher.getComputer();
				if (computer != null) {
					computerEnv = computer.getEnvironment();
				}
			}
			if (chatty)
				listener.getLogger().println("computer env vars:  " + computerEnv);
			if (computerEnv != null)
				overrides.putAll(computerEnv);
		} catch (IOException | InterruptedException e2) {
			if (chatty) 
				e2.printStackTrace(listener.getLogger());
		}
		
		if (env != null) {
			if (chatty)
				listener.getLogger().println("DSL injected env vars: " + env);
			overrides.putAll(env);
		}
		
		return overrides;
	}
	
	default Map<String, String> constructBuildUrl(TaskListener listener, Map<String, String> overrides, boolean chatty) {
		// for our pipeline strategy / build annotaion logic, we will construct the BUILD_URL env var 
		// if it is not set (which can occur with freestyle jobs when the openshift-sync plugin is not 
		// leveraged
		String jobName = overrides.get(JOB_NAME);
		String buildNum = overrides.get(BUILD_NUMBER);
		// we check for null but these should always be there from a jenkins api guarantee perspective
		if (jobName != null && buildNum != null) {
			IClient client = getClient(listener, getDisplayName(), overrides);
			List<IRoute> routes = client.list(ResourceKind.ROUTE, getNamespace(overrides));
			for (IRoute route : routes) {
				if (route.getServiceName().equals("jenkins")) {
					overrides.put(BUILD_URL_ENV_KEY, route.getURL() + "/job/" + jobName + "/" + buildNum + "/");
					break;
				}
			}
		} else {
			if (chatty)
				listener.getLogger().printf("\n missing jenkins job/build info: job %s build %s \n", jobName, buildNum);
		}
		
		return overrides;
	}
	
	//TODO leaving EnvVars env param for now ... in DSL scenario, curious of the injected contents vs. the run/build/computer contents
	default boolean doItCore(TaskListener listener, EnvVars env, Run<?, ?> run, AbstractBuild<?, ?> build, Launcher launcher) {
		boolean chatty = Boolean.parseBoolean(getVerbose());
		
		if (run == null && build == null)
			throw new RuntimeException("Either the run or build parameter must be set");
		
		Map<String, String> overrides = consolidateEnvVars(listener, env, run, build, launcher, chatty);
		
		setAuth(Auth.createInstance(chatty ? listener : null, getApiURL(overrides), overrides));
    	setToken(new TokenAuthorizationStrategy(Auth.deriveBearerToken(build != null ? build : run, getAuthToken(overrides), listener, chatty)));
    	
    	// this needs to follow the auth/token set up since a rest client instance is set up if we need
    	// to set up the BUILD_URL
    	if (!overrides.containsKey(BUILD_URL_ENV_KEY)) {
    		overrides = constructBuildUrl(listener, overrides, chatty);
    	}
    	
    	if (chatty)
    		listener.getLogger().println("\n\nOpenShift Pipeline Plugin: env vars for this job:  " + overrides);
    	
		return coreLogic(launcher, listener, overrides);
	}
	
	public String getDisplayName();

	default void doIt(Run<?, ?> run, FilePath workspace, Launcher launcher,
			TaskListener listener) throws InterruptedException, IOException {
    	boolean successful = this.doItCore(listener, null, run, null, launcher);
    	if (!successful)
    		throw new AbortException("\"" + getDisplayName() + "\" failed");
	}

    default boolean doIt(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
    	boolean successful = this.doItCore(listener, null, null, build, launcher);
    	if (!successful)
    		throw new AbortException("\"" + getDisplayName() + "\" failed");
    	return successful;
    }
    
    default String pruneKey(String key) {
    	if (key == null)
    		key = "";
    	if (key.startsWith("$"))
    		return key.substring(1, key.length()).trim();
    	return key.trim();
    }
    
    default String getOverride(String key, Map<String, String> overrides) {
		String val = pruneKey(key);
		if (overrides != null && overrides.containsKey(val)) {
			val = overrides.get(val);
		}
		return val;
    }
    
    default boolean verifyBuild(long startTime, long wait, IClient client, String bldCfg, String bldId, String namespace, boolean chatty, TaskListener listener, String displayName, boolean checkDeps, boolean annotateRC, Map<String, String> env) {
		String bldState = null;
    	while (System.currentTimeMillis() < (startTime + wait)) {
			IBuild bld = client.get(ResourceKind.BUILD, bldId, namespace);
			bldState = bld.getStatus();
			if (chatty)
				listener.getLogger().println("\nOpenShiftBuilder post bld launch bld state:  " + bldState);
			if (!bldState.equals("Complete") && !bldState.equals("Failed") && !bldState.equals("Error") && !bldState.equals("Cancelled")) {
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
			// calling this will annotate any RC that was created as a result of this build, with the jenkins job info.
			boolean triggerSuccess= didAllImagesChangeIfNeeded(bldCfg, listener, chatty, client, namespace, wait, annotateRC, env);
			if (checkDeps) {    						
				if (triggerSuccess) {
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

	default boolean doesDCTriggerOnImageTag(IClient client, IDeploymentConfig dc, String imageTag, boolean chatty, TaskListener listener) {
		if (dc == null || imageTag == null)
			throw new RuntimeException("needed param null for doesDCTriggerOnImageTag");
		if (listener == null)
			chatty = false;
		
		Collection<IDeploymentTrigger> triggers = dc.getTriggers();
		for (IDeploymentTrigger trigger : triggers) {
			if (trigger instanceof IDeploymentImageChangeTrigger) {
				IDeploymentImageChangeTrigger ict = (IDeploymentImageChangeTrigger)trigger;
				if (chatty)
					listener.getLogger().println("\n\n found ict " + ict.toString() + " with from " + ict.getFrom().getNameAndTag());
				if (ict.getFrom().getNameAndTag().contains(imageTag)) {
					if (chatty)
						listener.getLogger().println("\n\n ict  triggers off of " + imageTag);
					return true;
				}
			}
		}
		
		return false;
		
	}
	
	default boolean didICTCauseDeployment(IClient client, IDeploymentConfig dc, String imageTag, boolean chatty, TaskListener listener, long wait) {
		long currTime = System.currentTimeMillis();
		while (System.currentTimeMillis() - (wait / 3) <= currTime) {
			dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, dc.getName(), dc.getNamespace());
			if (!dc.didImageTrigger(imageTag)) {
				if (chatty)
					listener.getLogger().println("\n ICT did not fire");
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
				}
			} else {
				if (chatty)
					listener.getLogger().println("\n ICT fired for deployment " + dc.toJson(false));
				return true;
			}
		}
		
		if (chatty) {
			listener.getLogger().println("\n done checking dc " + dc.toJson(false) );
		}
		
		return false;
		
	}

	default boolean didImageChangeFromPreviousVersion(IClient client, int latestVersion, boolean chatty, TaskListener listener, 
			String depCfg, String namespace, String latestImageHexID, String imageTag) {
		// now get previous RC, fetch image Hex ID, and compare
		int previousVersion = latestVersion -1;
		if (previousVersion < 1) {
			if (chatty) listener.getLogger().println("\n first version skip image compare");
			return true;
		}
		IReplicationController prevRC = null;
		try {
			prevRC = client.get(ResourceKind.REPLICATION_CONTROLLER, depCfg + "-" + previousVersion, namespace);
		} catch (Throwable t) {
			if (chatty)
				t.printStackTrace(listener.getLogger());
		}
		
		if (prevRC == null) {
			listener.getLogger().println("\n\n could not obtain previous replication controller");
			return false;
		}
		
		// get the dc again from the rc vs. passing the dc in aIClient client, IDeploymentConfig dc, String imageTag, boolean chatty, TaskListener listener, long wait)s a form of cross reference verification
		String dcJson = prevRC.getAnnotation("openshift.io/encoded-deployment-config");
		if (dcJson == null || dcJson.length() == 0) {
			listener.getLogger().println("\n\n associated DeploymentConfig for previous ReplicationController missing");
			return false;
		}
		ModelNode dcNode = ModelNode.fromJSONString(dcJson);
		IDeploymentConfig dc = new DeploymentConfig(dcNode, client, null);
		String previousImageHexID = dc.getImageHexIDForImageNameAndTag(imageTag);
		
		if (previousImageHexID == null || previousImageHexID.length() == 0) {
			// don't count ill obtained prev image id as successful image id change
			listener.getLogger().println("\n\n could not obtain hex image ID for previous deployment");
			return false;
		}
		
		if (latestImageHexID.equals(previousImageHexID)) {
			if (chatty) listener.getLogger().println("\n images still the same " + latestImageHexID);
			return false;
		} else {
			if (chatty) listener.getLogger().println("\n image did change, new image " + latestImageHexID + " old image " + previousImageHexID);
			return true;
		}
	}

	default boolean didAllImagesChangeIfNeeded(String buildConfig, TaskListener listener, boolean chatty, IClient client, String namespace, long wait, boolean annotateRC, Map<String, String> env) {
		if (chatty)
			listener.getLogger().println("\n checking if the build config " + buildConfig + " got the image changes it needed");
		IBuildConfig bc = client.get(ResourceKind.BUILD_CONFIG, buildConfig, namespace);
		if (bc == null) {
			if (chatty)
				listener.getLogger().println("\n bc null for " +buildConfig);
		}
	
		String imageTag = null;
		try {
			imageTag = bc.getOutputRepositoryName();
			if (chatty) listener.getLogger().println("\n\n build config output image tag " + imageTag);
		} catch (Throwable t) {
		}
		
		if (imageTag == null) {
			if (chatty)
				listener.getLogger().println("\n\n build config " + bc.getName() + " does not output an image");
			return true;
		} else {
			if (chatty)
				listener.getLogger().println("\n\n build config " + bc.getName() + " generates image " + imageTag);
		}
		
		// find deployment configs with image change triggers 
		List<IDeploymentConfig> allDC = client.list(ResourceKind.DEPLOYMENT_CONFIG, namespace);
		if (allDC == null || allDC.size() == 0) {
			if (chatty)
				listener.getLogger().println("\n\n no deployment configs present");
			return true;
		}
		List<IDeploymentConfig> dcsToCheck = new ArrayList<IDeploymentConfig>();
		for (IDeploymentConfig dc : allDC) {
			if (chatty) listener.getLogger().println("\n checking triggers on dc " + dc.getName());
			if (doesDCTriggerOnImageTag(client, dc, imageTag, chatty, listener)) {
				if (chatty) listener.getLogger().println("\n adding dc to check " + dc.getName());
				dcsToCheck.add(dc);
			}
		}
		
		// cycle through the DCs triggering, comparing latest and previous RC, see if image changed
		for (IDeploymentConfig dc : dcsToCheck) {
			if (chatty) {
				listener.getLogger().println("\n looking at image ids for " + dc.getName() + " with json " + dc.toJson(false));
			}
			
			// if a DC has both a config change trigger and image change trigger, with the first deployment, it is 
			// a race condition as to whether the cause gets marked as the CCT or ICT; but in any event, as long as
			// the first deployment fires, we don't care why, so we bypass the looping for ICT change / image change
			// verification 
			if (dc.getLatestVersionNumber() == 1) {
				if(annotateRC) {
					IReplicationController rc = getLatestReplicationController(dc, namespace, client, chatty ? listener : null);
					annotateJobInfoToResource(client, listener, chatty, env, rc); 
				}
				return true;
			}
			
			if (this.didICTCauseDeployment(client, dc, imageTag, chatty, listener, wait)) {
				String latestImageHexID = dc.getImageHexIDForImageNameAndTag(imageTag);
				
				if (latestImageHexID == null) {
					if (chatty)
						listener.getLogger().println("\n dc " + dc.getName() + " did not have a reference to " + imageTag);
					continue;
				}
				
				if (didImageChangeFromPreviousVersion(client, dc.getLatestVersionNumber(), 
						chatty, listener, dc.getName(), namespace, latestImageHexID, imageTag)) {
		
					if(annotateRC) {
						IReplicationController rc = getLatestReplicationController(dc, namespace, client, chatty ? listener : null);
						annotateJobInfoToResource(client, listener, chatty, env, rc); 
					}
					if (chatty)
						listener.getLogger().println("\n dc " + dc.getName() + " did trigger based on image change as expected");
				} else {
					if (chatty)
						listener.getLogger().println("\n dc " + dc.getName() + " did not trigger based on image change as expected");
					return false;
				}
			} else {
				return false;
			}
			
		}
		
		return true;
	}

	default IReplicationController getLatestReplicationController(IDeploymentConfig dc, String namespace, IClient client, TaskListener listener) {
		int latestVersion = dc.getLatestVersionNumber();
		if (latestVersion == 0)
			return null;
		String repId = dc.getName() + "-" + latestVersion;
		IReplicationController rc = null;
		try {
			rc = client.get(ResourceKind.REPLICATION_CONTROLLER, repId, namespace);
		} catch (Throwable t) {
			if (listener != null)
				t.printStackTrace(listener.getLogger());
		}
		return rc;
	}

	// Adds the jenkins job information annotation to the resource object.
	// Will retry up to 3 times, 3 seconds apart, in case of update conflicts.
	default boolean annotateJobInfoToResource(IClient client, TaskListener listener, boolean chatty, Map<String, String> env, IResource resource) {
		boolean annotated = false;
		String buildURL = env.get(IOpenShiftPlugin.BUILD_URL_ENV_KEY);
		for(int i=0;i<3;i++) {
			// anticipate update conflicts so get the latest object and retry the update 3 times.
			IResource annotatedResource = client.get(resource.getKind(),resource.getName(),resource.getNamespace());
			annotatedResource.setAnnotation(IOpenShiftPlugin.BUILD_URL_ANNOTATION,buildURL);
			try {
				client.update(annotatedResource);
				annotated = true;
				break;
			} catch(OpenShiftException e) {
				if (chatty)
					e.printStackTrace(listener.getLogger());
			}
			try { Thread.sleep(3000); } catch(InterruptedException e){}
		}
		if(!annotated) {
			listener.getLogger().println(String.format(ANNOTATION_FAILURE, resource.getKind(), resource.getName()));
		}
		return annotated;
	}
    
}
