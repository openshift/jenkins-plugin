package com.openshift.jenkins.plugins.pipeline.model;

import hudson.Launcher;
import hudson.model.TaskListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.jenkins.plugins.pipeline.OpenShiftApiObjHandler;
import com.openshift.restclient.IClient;

public interface IOpenShiftDeleterLabels extends IOpenShiftApiObjHandler {

	final static String DISPLAY_NAME = "Delete OpenShift Resource(s) using Labels";
	
	default String getDisplayName() {
		return DISPLAY_NAME;
	}
	
	String getTypes();
		
	String getKeys();
		
	String getValues();
	
	default String getTypes(Map<String,String> overrides) {
		return getOverride(getTypes(), overrides);
	}
	
	default String getKeys(Map<String,String> overrides) {
		return getOverride(getKeys(), overrides);
	}
	
	default String getValues(Map<String,String> overrides) {
		return getOverride(getValues(), overrides);
	}
	
	default boolean coreLogic(Launcher launcher, TaskListener listener, Map<String, String> overrides) {
		boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
    	listener.getLogger().println(String.format(MessageConstants.START_DELETE_OBJS, DISPLAY_NAME, getNamespace(overrides)));

    	updateApiTypes(chatty, listener, overrides);
    	
    	// get oc client 
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    	
    	if (client != null) {
    		// verify valid type is specified
    		Set<String> types = OpenShiftApiObjHandler.apiMap.keySet();
    		String resourceKind = null;
	    	// rc[0] will be successful deletes, rc[1] will be failed deletes, not no failed deletes in the labels scenario
    		int[] rc = new int[2];
    		String[] inputTypes = getTypes(overrides).split(",");
    		String[] inputKeys = getKeys(overrides).split(",");
    		String[] inputValues = getValues(overrides).split(",");
    		
    		if (inputKeys.length != inputValues.length) {
    			listener.getLogger().println(String.format(MessageConstants.EXIT_DELETE_KEY_TYPE_MISMATCH, inputValues.length, inputKeys.length));
    			return false;
    		}
    		
    		Map<String, String> labels = new HashMap<String, String>();
    		for (int j=0; j < inputKeys.length; j++) {
    			labels.put(inputKeys[j], inputValues[j]);
    		}
    		
    		for (int i =0; i < inputTypes.length; i++) {
        		for (String type : types) {
        			
        			if (type.equalsIgnoreCase(inputTypes[i])) {
        				resourceKind = type;
        				break;
        			}
        		}
    			
        		if (resourceKind == null) {
        			listener.getLogger().println(String.format(MessageConstants.TYPE_NOT_SUPPORTED, inputTypes[i]));
        			continue;
        		}
        		
        		rc = deleteAPIObjs(client, listener, getNamespace(overrides), resourceKind, null, labels);
    		}
    		
    		listener.getLogger().println(String.format(MessageConstants.EXIT_DELETE_GOOD, DISPLAY_NAME, rc[0]));
    		return true;
     	}
		return false;
	}
}
