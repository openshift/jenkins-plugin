package com.openshift.jenkins.plugins.pipeline.model;

import hudson.Launcher;
import hudson.model.TaskListener;

import java.util.List;
import java.util.Map;

import org.jboss.dmr.ModelNode;


import com.openshift.internal.restclient.model.KubernetesResource;
import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.jenkins.plugins.pipeline.OpenShiftApiObjHandler;
import com.openshift.restclient.IClient;

public interface IOpenShiftCreator extends IOpenShiftApiObjHandler {
	final static String DISPLAY_NAME = "Create OpenShift Resource(s)";
	final static String UNDEFINED = "undefined";
	
	default String getDisplayName() {
		return DISPLAY_NAME;
	}
	
	String getJsonyaml();
	
    default String getJsonyaml(Map<String,String> overrides) {
		return getOverride(getJsonyaml(), overrides);
    }
    
    default boolean makeRESTCall(boolean chatty, TaskListener listener, String path, ModelNode resource, Map<String,String> overrides) {
    	//TODO openshift-restclient-java's IApiTypeMapper is not really accessible from our perspective,
    	//without accessing/recreating the underlying OkHttpClient, so until that changes, we use our own
    	// type mapping
		if (OpenShiftApiObjHandler.apiMap.get(path) == null) {
			listener.getLogger().println(String.format(MessageConstants.TYPE_NOT_SUPPORTED, path));
			return false;
		}
		
    	// if they have specified a namespace in the json/yaml, honor that vs. the one specified in the build step,
    	// though the user then needs to insure that the service account for the one specified in the build step 
    	// has access to that namespace
    	String namespace = resource.get("metadata").get("namespace").asString();
    	boolean setOnResource = false;
    	if (UNDEFINED.equals(namespace))
    		namespace = getNamespace(overrides);
    	else
    		setOnResource = true;
    	    	
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    
    	if (client == null) {
    		return false;
    	}
    	
    	KubernetesResource kr = new KubernetesResource(resource, client, null);
    	if (setOnResource)
    		kr.setNamespace(namespace);
    	if (chatty)
    		listener.getLogger().println("\nOpenShiftCreator calling create on for type " + OpenShiftApiObjHandler.apiMap.get(path)[1] + " and resource " + kr.toJson(false));
    	client.execute("POST", OpenShiftApiObjHandler.apiMap.get(path)[1], namespace, null, null, kr);
		
		return true;
    }
    
	default boolean coreLogic(Launcher launcher, TaskListener listener, Map<String,String> overrides) {
		boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
    	listener.getLogger().println(String.format(MessageConstants.START_CREATE_OBJS, getNamespace(overrides)));
    	updateApiTypes(chatty, listener, overrides);
    	
    	ModelNode resources = this.hydrateJsonYaml(getJsonyaml(overrides), chatty ? listener : null);
    	if (resources == null) {
    		return false;
    	}
    	    	
    	//cycle through json and POST to appropriate resource
    	String kind = resources.get("kind").asString();
    	int created = 0;
    	int failed = 0;
    	if (kind.equalsIgnoreCase("List")) {
    		List<ModelNode> list = resources.get("items").asList();
    		for (ModelNode node : list) {
    			String path = node.get("kind").asString();
    			
    			boolean success = this.makeRESTCall(chatty, listener, path, node, overrides);
    			if (!success) {
    				listener.getLogger().println(String.format(MessageConstants.FAILED_OBJ, path));
    				failed++;
    			} else {
    				listener.getLogger().println(String.format(MessageConstants.CREATED_OBJ, path));
    				created++;
    			}
    		}
    	} else {
    		String path = kind;
			
    		boolean success = this.makeRESTCall(chatty, listener, path, resources, overrides);
    		if (success) {
				listener.getLogger().println(String.format(MessageConstants.CREATED_OBJ, path));
    			created = 1;
    		} else {
				listener.getLogger().println(String.format(MessageConstants.FAILED_OBJ, path));
    			failed = 1;
    		}
    	}

    	if (failed > 0) {
    		listener.getLogger().println(String.format(MessageConstants.EXIT_CREATE_BAD, created, failed));
			return false;
    	} else {
    		listener.getLogger().println(String.format(MessageConstants.EXIT_CREATE_GOOD, created));
    		return true;
    	}
	}
    
}
