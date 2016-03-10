package com.openshift.jenkins.plugins.pipeline;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IImageStream;

import javax.servlet.ServletException;

import java.io.IOException;
import java.util.Map;

public class OpenShiftImageTagger extends OpenShiftBaseStep {

	protected final static String DISPLAY_NAME = "Tag OpenShift Image";
	
    protected final String testTag;
    protected final String prodTag;
    protected final String testStream;
    protected final String prodStream;
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftImageTagger(String apiURL, String testTag, String prodTag, String namespace, String authToken, String verbose, String testStream, String prodStream) {
    	super(apiURL, namespace, authToken, verbose);
        this.testTag = testTag;
        this.prodTag = prodTag;
        this.prodStream = prodStream;
        this.testStream = testStream;
    }

	public String getTestTag() {
		return testTag;
	}

	public String getTestTag(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("testTag"))
			return overrides.get("testTag");
		return getTestTag();
	}
	
	public String getProdTag() {
		return prodTag;
	}
	
	public String getProdTag(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("prodTag"))
			return overrides.get("prodTag");
		return getProdTag();
	}
	
	public String getTestStream() {
		return testStream;
	}

	public String getTestStream(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("testStream"))
			return overrides.get("testStream");
		return getTestStream();
	}
	
	public String getProdStream() {
		return prodStream;
	}

	public String getProdStream(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("prodStream"))
			return overrides.get("prodStream");
		return getProdStream();
	}
	
	public boolean coreLogic(Launcher launcher, TaskListener listener,
			EnvVars env, Map<String,String> overrides) {
    	listener.getLogger().println(String.format(MessageConstants.START_TAG, DISPLAY_NAME, getTestStream(overrides), getTestTag(overrides), getProdStream(overrides), getProdTag(overrides), getNamespace(overrides)));
    	
    	// get oc client 
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    	
    	if (client != null) {
        	//tag image
			IImageStream is = client.get(ResourceKind.IMAGE_STREAM, getProdStream(overrides), getNamespace(overrides));
			is.setTag(getProdTag(overrides), getTestStream(overrides) + ":" + getTestTag(overrides));
			client.update(is);
			
			
	    	listener.getLogger().println(String.format(MessageConstants.EXIT_OK, DISPLAY_NAME));
			return true;
    	} else {
    		return false;
    	}

	}

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link OpenShiftImageTagger}. Used as a singleton.
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

        public FormValidation doCheckTestTag(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckTestTag(value);
        }

        public FormValidation doCheckProdTag(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckProdTag(value);
        }


        public FormValidation doCheckTestStream(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckTestStream(value);
        }

        public FormValidation doCheckProdStream(@QueryParameter String value)
                throws IOException, ServletException {
            return ParamVerify.doCheckProdStream(value);
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

