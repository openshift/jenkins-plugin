package com.openshift.jenkins.plugins.pipeline.model;

import hudson.Launcher;
import hudson.model.TaskListener;

import java.util.Map;

import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;

public interface IOpenShiftDeploymentVerification extends IOpenShiftTimedPlugin {

	final static String DISPLAY_NAME = "Verify OpenShift Deployment";
	
	default String getDisplayName() {
		return DISPLAY_NAME;
	}
	
	String getDepCfg();

	String getReplicaCount();
		
	String getVerifyReplicaCount();
	
	default String getDepCfg(Map<String,String> overrides) {
		return getOverride(getDepCfg(), overrides);
	}
	
	default String getReplicaCount(Map<String,String> overrides) {
		return getOverride(getReplicaCount(), overrides);
	}
	
	default String getVerifyReplicaCount(Map<String,String> overrides) {
		return getOverride(getVerifyReplicaCount(), overrides);
	}
	
	default boolean coreLogic(Launcher launcher, TaskListener listener, Map<String,String> overrides) {
    	boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
    	boolean checkCount = Boolean.parseBoolean(getVerifyReplicaCount(overrides));
    	listener.getLogger().println(String.format(MessageConstants.START_DEPLOY_RELATED_PLUGINS, DISPLAY_NAME, getDepCfg(overrides), getNamespace(overrides)));
    	
    	// get oc client 
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    	
    	if (client != null) {
        	// explicitly set replica count, save that
        	int count = -1;
        	if (checkCount && getReplicaCount(overrides) != null && getReplicaCount(overrides).length() > 0)
        		count = Integer.parseInt(getReplicaCount(overrides));
        		

        	if (!checkCount)
        		listener.getLogger().println(String.format(MessageConstants.WAITING_ON_DEPLOY, getDepCfg(overrides)));
        	else
        		listener.getLogger().println(String.format(MessageConstants.WAITING_ON_DEPLOY_PLUS_REPLICAS, getDepCfg(overrides), getReplicaCount(overrides)));        	
			
			// confirm the deployment has kicked in from completed build;
        	// in testing with the jenkins-ci sample, the initial deploy after
        	// a build is kinda slow ... gotta wait more than one minute
			long currTime = System.currentTimeMillis();
			String state = null;
			String depId = null;
        	boolean scaledAppropriately = false;
			if (chatty)
				listener.getLogger().println("\nOpenShiftDeploymentVerifier wait " + convertUnitNotation(getWaitTime(overrides)));
			while (System.currentTimeMillis() < (currTime + convertUnitNotation(getWaitTime(overrides)))) {
				// refresh dc first
				IDeploymentConfig dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, getDepCfg(overrides), getNamespace(overrides));
				
				if (dc != null) {
					// if replicaCount not set, get it from config
					if (checkCount && count == -1)
						count = dc.getReplicas();
					
					if (chatty)
						listener.getLogger().println("\nOpenShiftDeploymentVerifier latest version:  " + dc.getLatestVersionNumber());
									
					IReplicationController rc = getLatestReplicationController(dc, getNamespace(overrides), client, chatty ? listener : null);
						
					if (rc != null) {
						if (chatty)
							listener.getLogger().println("\nOpenShiftDeploymentVerifier current rc " + rc);
						state = this.getReplicationControllerState(rc);
						if (this.isDeployFinished(state)) {
							depId = rc.getName();
							// first check state
							if (state.equalsIgnoreCase(STATE_FAILED)) {
								listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_BAD, DISPLAY_NAME, getDepCfg(overrides), state));
								return false;
							}
							if (chatty) listener.getLogger().println("\nOpenShiftDeploymentVerifier rc current count " + rc.getCurrentReplicaCount() + " rc desired count " + rc.getDesiredReplicaCount() + " step verification amount " + count + " current state " + state + " and check count " + checkCount);

							scaledAppropriately = this.isReplicationControllerScaledAppropriately(rc, checkCount, count);
							if (scaledAppropriately)
								break;
						} else {
							if (chatty)
								listener.getLogger().println("\nOpenShiftDeploymentVerifier current phase " + state);
						}
		        		
					} else {
						if (chatty)
							listener.getLogger().println("\nOpenShiftDeploymenVerifier no rc for latest version yet");
					}
				} else {
		    		listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_NO_CFG, DISPLAY_NAME, getDepCfg(overrides)));
	    			return false;
				}
													        										
        		try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}

			}
        			
        	if (scaledAppropriately) {
    	    	if (!checkCount)
    	    		listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_GOOD_REPLICAS_IGNORED, DISPLAY_NAME, depId));
    	    	else
    	    		listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_VERIFY_GOOD_REPLICAS_GOOD, DISPLAY_NAME, depId, count));
        		return true;
        	} else {
        		if (checkCount)
        			listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_VERIFY_BAD_REPLICAS_BAD, DISPLAY_NAME, depId, getReplicaCount(overrides)));
        		else
    		    	listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_BAD, DISPLAY_NAME, depId, state));
    	    	return false;
        	}        	
        		
        		
    	} else {
    		return false;
    	}

	}
	
}
