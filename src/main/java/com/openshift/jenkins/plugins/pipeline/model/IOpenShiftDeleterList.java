package com.openshift.jenkins.plugins.pipeline.model;

import hudson.Launcher;
import hudson.model.TaskListener;

import java.util.Map;
import java.util.Set;

import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.jenkins.plugins.pipeline.OpenShiftApiObjHandler;
import com.openshift.restclient.IClient;

public interface IOpenShiftDeleterList extends IOpenShiftApiObjHandler {

	final static String DISPLAY_NAME = "Delete OpenShift Resource(s) by Key";
	
	default String getDisplayName() {
		return DISPLAY_NAME;
	}
	
	String getKeys();
		
	String getTypes();
		
	default String getTypes(Map<String,String> overrides) {
		return getOverride(getTypes(), overrides);
	}
	
	default String getKeys(Map<String,String> overrides) {
		return getOverride(getKeys(), overrides);
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
	    	// rc[0] will be successful deletes, rc[1] will be failed deletes
    		int[] rc = new int[2];
    		int badType = 0;
    		String[] inputTypes = getTypes(overrides).split(",");
    		String[] inputKeys = getKeys(overrides).split(",");
    		
    		if (inputTypes.length != inputKeys.length) {
    			listener.getLogger().println(String.format(MessageConstants.EXIT_DELETE_KEY_TYPE_MISMATCH, inputTypes.length, inputKeys.length));
    			return false;
    		}
    		
    		for (int i =0; i < inputTypes.length; i++) {
    			if (OpenShiftApiObjHandler.typeShortcut.containsKey(inputTypes[i])) {
    				resourceKind = OpenShiftApiObjHandler.typeShortcut.get(inputTypes[i]);
    			} else {
            		for (String type : types) {
            			
            			if (type.equalsIgnoreCase(inputTypes[i])) {
            				resourceKind = type;
            				break;
            			}
            			
            		}
    			}
    			
        		if (resourceKind == null) {
        			listener.getLogger().println(String.format(MessageConstants.TYPE_NOT_SUPPORTED, inputTypes[i]));
        			badType++;
        			continue;
        		}
        		
        		rc = deleteAPIObjs(client, listener, getNamespace(overrides), resourceKind, inputKeys[i], null);
    		}
    		
    		if (rc[1] == 0 && badType == 0) {
    			listener.getLogger().println(String.format(MessageConstants.EXIT_DELETE_GOOD, DISPLAY_NAME, rc[0]));
    			return true;
    		} else {
    			listener.getLogger().println(String.format(MessageConstants.EXIT_DELETE_BAD, DISPLAY_NAME, rc[0], rc[1] + badType));
    		}
    	}
		return false;
	}
}
