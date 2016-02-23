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

import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IService;

import javax.servlet.ServletException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class OpenShiftServiceVerifier extends OpenShiftBaseStep {

    protected String svcName = "frontend";
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftServiceVerifier(String apiURL, String svcName, String namespace, String authToken, String verbose) {
        this.apiURL = apiURL;
        this.svcName = svcName;
        this.namespace = namespace;
        this.authToken = authToken;
        this.verbose = verbose;
    }

	public String getSvcName() {
		return svcName;
	}

    protected boolean coreLogic(Launcher launcher, TaskListener listener, EnvVars env) {
		boolean chatty = Boolean.parseBoolean(verbose);
    	listener.getLogger().println("\n\nBUILD STEP:  OpenShiftServiceVerifier in perform on namespace " + namespace);
    	
    	// get oc client (sometime REST, sometimes Exec of oc command
    	IClient client = new ClientFactory().create(apiURL, auth);
    	
    	if (client != null) {
    		// seed the auth
        	client.setAuthorizationStrategy(bearerToken);
        	
        	// get Service
        	IService svc = client.get(ResourceKind.SERVICE, svcName, namespace);
        	String ip = svc.getPortalIP();
        	int port = svc.getPort();
        	String spec = "http://" + ip + ":" + port;
        	URL url = null;
        	try {
				url = new URL(spec);
			} catch (MalformedURLException e) {
				e.printStackTrace(listener.getLogger());
				return false;
			}
        	int tryCount = 0;
        	if (chatty)
        		listener.getLogger().println("\nOpenShiftServiceVerifier retry " + getDescriptor().getRetry());
    		URLConnection conn = null;
        	while (tryCount < getDescriptor().getRetry()) {
        		tryCount++;
        		if (chatty) listener.getLogger().println("\nOpenShiftServiceVerifier attempt connect to " + spec + " attempt " + tryCount);
        		try {
					conn = url.openConnection();
				} catch (IOException e) {
					if (chatty) e.printStackTrace(listener.getLogger());
				}
        		if (conn != null)
        			break;
        	}
        	
        	if (conn != null) {
        		listener.getLogger().println("\n\nBUILD STEP EXIT: OpenShiftServiceVerifier successful connection made");
        		return true;
        	}
        	
    	} else {
    		listener.getLogger().println("\n\nOpenShiftServiceVerifier could not get oc client");
    		return false;
    	}

    	if (!chatty)
    		listener.getLogger().println("\n\nBUILD STEP EXIT: OpenShiftServiceVerifier successful connection could not be made, re-run with verbose logging to assist with diagnostics");
    	else
    		listener.getLogger().println("\n\nBUILD STEP EXIT: OpenShiftServiceVerifier successful connection made, review verbose logging above to help determine the problem");

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
     * Descriptor for {@link OpenShiftServiceVerifier}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    	private int retry = 100;
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
            if (value.length() == 0)
                return FormValidation.warning("Unless you specify a value here, one of the default API endpoints will be used; see this field's help or https://github.com/openshift/jenkins-plugin#common-aspects-across-the-rest-based-functions-build-steps-scm-post-build-actions for details");
            return FormValidation.ok();
        }

        public FormValidation doCheckSvcName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set the name of the Service to validate");
            return FormValidation.ok();
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.warning("Unless you specify a value here, the default namespace will be used; see this field's help or https://github.com/openshift/jenkins-plugin#common-aspects-across-the-rest-based-functions-build-steps-scm-post-build-actions for details");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Verify a service is up in OpenShift";
        }
        
        public int getRetry() {
        	return retry;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // pull info from formData, set appropriate instance field (which should have a getter), and call save().
        	retry = formData.getInt("retry");
            save();
            return super.configure(req,formData);
        }

    }

}

