package com.openshift.jenkins.plugins.pipeline.model;

import hudson.Launcher;
import hudson.model.TaskListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IBuild;

public interface IOpenShiftBuildVerifier extends IOpenShiftPlugin {

	public final static String DISPLAY_NAME = "Verify OpenShift Build";

	default String getDisplayName() {
		return DISPLAY_NAME;
	}
	
	String getBldCfg();
		
	String getCheckForTriggeredDeployments();
		
	String getWaitTime();
	
	String getWaitTime(Map<String, String> overrides);
	
	default String getBldCfg(Map<String,String> overrides) {
		return getOverride(getBldCfg(), overrides);
	}

	default String getCheckForTriggeredDeployments(Map<String,String> overrides) {
		return getOverride(getCheckForTriggeredDeployments(), overrides);
	}

	default List<String> getBuildIDs(IClient client, Map<String,String> overrides) {
		List<IBuild> blds = client.list(ResourceKind.BUILD, getNamespace(overrides));
		List<String> ids = new ArrayList<String>();
		for (IBuild bld : blds) {
			if (bld.getName().startsWith(getBldCfg(overrides))) {
				ids.add(bld.getName());
			}
		}
		return ids;
	}
	
	default String getLatestBuildID(List<String> ids) {
		String bldId = null;
		if (ids.size() > 0) {
			Collections.sort(ids);
			bldId = ids.get(ids.size() - 1);
		}
		return bldId;
	}
	
	default boolean coreLogic(Launcher launcher, TaskListener listener, Map<String,String> overrides) {
		boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
		boolean checkDeps = Boolean.parseBoolean(getCheckForTriggeredDeployments(overrides));
    	listener.getLogger().println(String.format(MessageConstants.START_BUILD_RELATED_PLUGINS, DISPLAY_NAME, getBldCfg(overrides), getNamespace(overrides)));
    	
    	// get oc client 
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    	
    	if (client != null) {
			if (chatty)
				listener.getLogger().println("\nOpenShiftBuildVerifier wait " + getWaitTime(overrides));
			List<String> ids = getBuildIDs(client, overrides);
			
			String bldId = getLatestBuildID(ids);
			
			if (!checkDeps)
				listener.getLogger().println(String.format(MessageConstants.WAITING_ON_BUILD_STARTED_ELSEWHERE, bldId));
			else
				listener.getLogger().println(String.format(MessageConstants.WAITING_ON_BUILD_STARTED_ELSEWHERE_PLUS_DEPLOY, bldId));
				
			return this.verifyBuild(System.currentTimeMillis(), Long.parseLong(getWaitTime(overrides)), client, getBldCfg(overrides), bldId, getNamespace(overrides), chatty, listener, DISPLAY_NAME, checkDeps, false, overrides);
    				        		
    	} else {
    		return false;
    	}
    	
	}
	
}
