package com.openshift.jenkins.plugins.pipeline;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftDeleterLabels;


public class OpenShiftDeleterLabels extends OpenShiftBaseStep implements IOpenShiftDeleterLabels {

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
		return types;
	}
	
	public String getKeys() {
		return keys;
	}
	
	public String getValues() {
		return values;
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

        public FormValidation doCheckAuthToken(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckToken(value);
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
