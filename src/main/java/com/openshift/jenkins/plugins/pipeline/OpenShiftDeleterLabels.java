package com.openshift.jenkins.plugins.pipeline;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.model.Descriptor.FormException;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.openshift.jenkins.plugins.pipeline.OpenShiftCreator.DescriptorImpl;
import com.openshift.restclient.IClient;
import com.openshift.restclient.model.IResource;

public class OpenShiftDeleterLabels extends OpenShiftApiObjHandler {

	protected final static String DISPLAY_NAME = "Delete OpenShift Resource(s) using Labels";
	protected final String types;
	protected final String keys;
	protected final String values;
	
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
	public OpenShiftDeleterLabels(String apiURL, String namespace, String authToken,
			String verbose, String types, String keys, String values) {
		super(apiURL, namespace, authToken, verbose);
		this.types = types;
		this.keys = keys;
		this.values = values;
	}

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the intial incarnations of the plugin)
    // of insuring nulls are not returned for field getters
	public String getTypes() {
		if (types == null)
			return "";
		return types;
	}
	
	public String getTypes(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("types"))
			return overrides.get("types");
		return getTypes();
	}
	
	public String getKeys() {
		if (keys == null)
			return "";
		return keys;
	}
	
	public String getKeys(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("keys"))
			return overrides.get("keys");
		return getKeys();
	}
	
	public String getValues() {
		if (values == null)
			return "";
		return values;
	}
	
	public String getValues(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("values"))
			return overrides.get("values");
		return getValues();
	}
	
	@Override
	public boolean coreLogic(Launcher launcher, TaskListener listener,
			EnvVars env, Map<String, String> overrides) {
		boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
    	listener.getLogger().println(String.format(MessageConstants.START_DELETE_OBJS, DISPLAY_NAME, getNamespace(overrides)));

    	updateApiTypes(chatty, listener, overrides);
    	
    	// get oc client 
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    	
    	if (client != null) {
    		// verify valid type is specified
    		Set<String> types = apiMap.keySet();
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

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link OpenShiftCreator}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the various fields.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckApiURL(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckApiURL(value);
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckNamespace(value);
        }

        public FormValidation doCheckType(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckType(value);
        }

        public FormValidation doCheckKey(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckKey(value);
        }
        
        public ListBoxModel doFillInputTypeItems(@AncestorInPath Item item, @QueryParameter String key, @QueryParameter String type) {
        	ListBoxModel ret = new ListBoxModel();
        	ret.add("Json contents", "json");
        	ret.add("API Object type : API Object key", "kv");
        	ret.add("Label", "label");
        	return ret;
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // pull info from formData, set appropriate instance field (which should have a getter), and call save().
            save();
            return super.configure(req,formData);
        }

    }
}
