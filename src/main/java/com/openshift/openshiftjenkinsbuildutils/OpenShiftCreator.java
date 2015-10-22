package com.openshift.openshiftjenkinsbuildutils;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;

import org.jboss.dmr.ModelNode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.internal.restclient.http.HttpClientException;
import com.openshift.internal.restclient.http.UrlConnectionHttpClient;
import com.openshift.internal.restclient.model.KubernetesResource;
import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.capability.ICapability;

import javax.servlet.ServletException;

import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.List;

import jenkins.tasks.SimpleBuildStep;

/**
 * OpenShift {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link OpenShiftCreator} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Gabe Montero
 */
public class OpenShiftCreator extends Builder implements SimpleBuildStep, Serializable {

    private String apiURL = "https://openshift.default.svc.cluster.local";
    private String namespace = "test";
    private String authToken = "";
    private String verbose = "false";
    private String jsonyaml = "";
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftCreator(String apiURL, String namespace, String authToken, String verbose, String jsonyaml) {
        this.apiURL = apiURL;
        this.namespace = namespace;
        this.authToken = authToken;
        this.verbose = verbose;
        this.jsonyaml = jsonyaml;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getApiURL() {
		return apiURL;
	}

	public String getNamespace() {
		return namespace;
	}
	
	public String getAuthToken() {
		return authToken;
	}

    public String getVerbose() {
		return verbose;
	}
    
    public String getJsonyaml() {
    	return jsonyaml;
    }
    
    private boolean makeRESTCall(boolean chatty, TaskListener listener, String path, Auth auth, UrlConnectionHttpClient urlClient, ModelNode resource) {
		String response = null;
		URL url = null;
    	try {
    		if (chatty) listener.getLogger().println("\nOpenShiftCreator PUT URI " + "/oapi/v1/namespaces/"+namespace+"/"+path+"s");
			url = new URL(apiURL + "/oapi/v1/namespaces/" + namespace + "/" + path + "s");
		} catch (MalformedURLException e1) {
			e1.printStackTrace(listener.getLogger());
			return false;
		}
    	
    	IClient client = new ClientFactory().create(apiURL, auth);
    	if (client == null) {
    		listener.getLogger().println("\n\n OpenShiftCreator BUILD STEP EXIT:  could not create client");
    		return false;
    	}
		try {
	    	KubernetesResource kr = new KubernetesResource(resource, client, null);
			response = urlClient.post(url, 10 * 1000, kr);
			if (chatty) listener.getLogger().println("\nOpenShiftCreator REST PUT response " + response);
		} catch (SocketTimeoutException e1) {
			if (chatty) e1.printStackTrace(listener.getLogger());
    		listener.getLogger().println("\n\n OpenShiftCreator BUILD STEP EXIT:  socket timeout");
			return false;
		} catch (HttpClientException e1) {
			if (chatty) e1.printStackTrace(listener.getLogger());
    		listener.getLogger().println("\n\n OpenShiftCreator BUILD STEP EXIT:  HTTP client exception");
			return false;
		}
		
		return true;
    }
    
    protected boolean coreLogic(AbstractBuild build, Launcher launcher, TaskListener listener) {
		boolean chatty = Boolean.parseBoolean(verbose);
    	System.setProperty(ICapability.OPENSHIFT_BINARY_LOCATION, Constants.OC_LOCATION);
    	listener.getLogger().println("\n\nBUILD STEP:  OpenShiftImageTagger in perform");
    	
    	TokenAuthorizationStrategy bearerToken = new TokenAuthorizationStrategy(Auth.deriveBearerToken(build, authToken, listener, chatty));
    	Auth auth = Auth.createInstance(chatty ? listener : null);
    	    	
    	ModelNode resources = ModelNode.fromJSONString(jsonyaml);
    	    	
    	//cycle through json and POST to appropriate resource
    	String kind = resources.get("kind").asString();
		UrlConnectionHttpClient urlClient = new UrlConnectionHttpClient(
				null, "application/json", null, auth, null, null);
		urlClient.setAuthorizationStrategy(bearerToken);
		
    	boolean success = false;
    	if (kind.equalsIgnoreCase("List")) {
    		List<ModelNode> list = resources.get("items").asList();
    		for (ModelNode node : list) {
    			String path = node.get("kind").asString().toLowerCase();
    			success = this.makeRESTCall(chatty, listener, path, auth, urlClient, node);
    			if (!success)
    				break;
    		}
    	} else {
    		String path = kind.toLowerCase();
    		success = this.makeRESTCall(chatty, listener, path, auth, urlClient, resources);
    	}

    	if (success)
    		listener.getLogger().println("\n\n OpenShiftCreator BUILD STEP EXIT:  resources(s) created");
		return success;
    	
    }
    

	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher,
			TaskListener listener) throws InterruptedException, IOException {
		coreLogic(null, launcher, listener);
	}

	@Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		return coreLogic(build, launcher, listener);
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
            if (value.length() == 0)
                return FormValidation.error("Please set apiURL");
            return FormValidation.ok();
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set namespace");
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
            return "Create resource(s) in OpenShift";
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

