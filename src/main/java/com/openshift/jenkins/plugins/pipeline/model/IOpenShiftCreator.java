package com.openshift.jenkins.plugins.pipeline.model;

import hudson.Launcher;
import hudson.model.TaskListener;

import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.jboss.dmr.ModelNode;

import com.openshift.internal.restclient.http.HttpClientException;
import com.openshift.internal.restclient.model.KubernetesResource;
import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.jenkins.plugins.pipeline.OpenShiftApiObjHandler;
import com.openshift.restclient.IClient;

public interface IOpenShiftCreator extends IOpenShiftApiObjHandler {
	final static String DISPLAY_NAME = "Create OpenShift Resource(s)";
	
	String getJsonyaml();
	
    default String getJsonyaml(Map<String,String> overrides) {
		return getOverride(getJsonyaml(), overrides);
    }
    
    default boolean makeRESTCall(boolean chatty, TaskListener listener, String path, ModelNode resource, Map<String,String> overrides) {
		String response = null;
		URL url = null;
		if (OpenShiftApiObjHandler.apiMap.get(path) == null) {
			listener.getLogger().println(String.format(MessageConstants.TYPE_NOT_SUPPORTED, path));
			return false;
		}
		
    	try {
    		if (chatty) listener.getLogger().println("\nOpenShiftCreator POST URI " + OpenShiftApiObjHandler.apiMap.get(path)[0] + "/" + resource.get("apiVersion").asString() + "/namespaces/" +getNamespace(overrides) + "/" + OpenShiftApiObjHandler.apiMap.get(path)[1]);
			url = new URL(getApiURL(overrides) + OpenShiftApiObjHandler.apiMap.get(path)[0] + "/" + resource.get("apiVersion").asString() + "/namespaces/" + getNamespace(overrides) + "/" + OpenShiftApiObjHandler.apiMap.get(path)[1]);
		} catch (MalformedURLException e1) {
			e1.printStackTrace(listener.getLogger());
			return false;
		}
    	
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    	if (client == null) {
    		return false;
    	}
		try {
	    	KubernetesResource kr = new KubernetesResource(resource, client, null);
			response = createHttpClient().post(url, 10 * 1000, kr);
			if (chatty) listener.getLogger().println("\nOpenShiftCreator REST POST response " + response);
		} catch (SocketTimeoutException e1) {
			if (chatty) e1.printStackTrace(listener.getLogger());
	    	listener.getLogger().println(String.format(MessageConstants.SOCKET_TIMEOUT, DISPLAY_NAME, getApiURL(overrides)));
			return false;
		} catch (HttpClientException e1) {
			if (chatty) e1.printStackTrace(listener.getLogger());
	    	listener.getLogger().println(String.format(MessageConstants.HTTP_ERR, e1.getMessage(), DISPLAY_NAME, getApiURL(overrides)));
			return false;
		}
		
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
