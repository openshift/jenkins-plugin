package com.openshift.jenkins.plugins.pipeline;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.jenkins.plugins.pipeline.model.IOpenShiftImageTagger;
//import com.openshift.restclient.authorization.TokenAuthorizationStrategy;

import javax.servlet.ServletException;

import java.io.IOException;
//import java.util.Map;

public class OpenShiftImageTagger extends OpenShiftBaseStep implements IOpenShiftImageTagger {

	
    protected final String testTag;
    protected final String prodTag;
    protected final String testStream;
    protected final String prodStream;
    protected final String destinationNamespace;
    protected final String destinationAuthToken;
    protected final String alias;
    // marked transient so don't serialize these next 2 in the workflow plugin flow; constructed on per request basis
//    protected transient TokenAuthorizationStrategy destinationBearerToken;
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftImageTagger(String apiURL, String testTag, String prodTag, String namespace, String authToken, String verbose, String testStream, String prodStream, String destinationNamespace, String destinationAuthToken, String alias) {
    	super(apiURL, namespace, authToken, verbose);
        this.testTag = testTag;
        this.prodTag = prodTag;
        this.prodStream = prodStream;
        this.testStream = testStream;
        this.destinationAuthToken = destinationAuthToken;
        this.destinationNamespace = destinationNamespace;
        this.alias = alias;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the intial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

	public String getAlias() {
		return alias;
	}

	public String getTestTag() {
		return testTag;
	}

	public String getProdTag() {
		return prodTag;
	}
	
	public String getTestStream() {
		return testStream;
	}

	public String getProdStream() {
		return prodStream;
	}

	public String getDestinationNamespace() {
		return this.destinationNamespace;
	}
	
	public String getDestinationAuthToken() {
		return this.destinationAuthToken;
	}
	
/*	public TokenAuthorizationStrategy getDestinationToken() {
		return destinationBearerToken;
	}
	
	public void setDestinationToken(TokenAuthorizationStrategy token) {
		destinationBearerToken = token;
	}
*/	
	
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
        
        public FormValidation doCheckDestinationNamespace(@QueryParameter String value)
                throws IOException, ServletException {
        	// change reuse doCheckNamespace for destination namespace
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

