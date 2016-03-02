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

import org.jboss.dmr.ModelNode;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.internal.restclient.http.HttpClientException;
import com.openshift.internal.restclient.http.UrlConnectionHttpClient;
import com.openshift.internal.restclient.model.KubernetesResource;
import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;

import javax.servlet.ServletException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class OpenShiftCreator extends OpenShiftBaseStep {
	
	protected final static String DISPLAY_NAME = "Create OpenShift Resource(s)";
    protected String jsonyaml = "";
    
    private static final Map<String, String[]> apiMap;
    static
    {
    	//POST https://localhost:8443/apis/extensions/v1beta1/namespaces/test/jobs
    	//POST https://localhost:8443/oapi/v1/namespaces/test/imagestreams
        String api = "/api";
        String oapi = "/oapi";
        String apis = "/apis";

        // OpenShift API endpoints
        apiMap = new HashMap<String, String[]>();
    	apiMap.put("BuildConfig", new String[]{oapi, "buildconfigs"});
    	apiMap.put("Build", new String[]{oapi, "builds"});
    	apiMap.put("DeploymentConfigRollback", new String[]{oapi, "deploymentconfigrollbacks"});
    	apiMap.put("DeploymentConfig", new String[]{oapi, "deploymentconfigs"});
    	apiMap.put("ImageStreamMapping", new String[]{oapi, "imagestreammappings"});
    	apiMap.put("ImageStream", new String[]{oapi, "imagestreams"});
    	apiMap.put("LocalResourceAccessReview", new String[]{oapi, "localresourceaccessreviews"});
    	apiMap.put("LocalSubjectAccessReview", new String[]{oapi, "localsubjectaccessreviews"});
    	apiMap.put("Policy", new String[]{oapi, "policies"});
    	apiMap.put("PolicyBinding", new String[]{oapi, "policybindings"});
    	//apiMap.put("Template", new String[]{oapi, "processedtemplates"}); // Different from templates?
    	apiMap.put("ResourceAccessReview", new String[]{oapi, "resourceaccessreviews"});
    	apiMap.put("RoleBinding", new String[]{oapi, "rolebindings"});
    	apiMap.put("Role", new String[]{oapi, "roles"});
    	apiMap.put("Route", new String[]{oapi, "routes"});
    	apiMap.put("SubjectAccessReview", new String[]{oapi, "subjectaccessreviews"});
    	apiMap.put("Template", new String[]{oapi, "templates"});

        // Kubernetes API endpoints
    	apiMap.put("Binding", new String[]{api, "bindings"});
    	apiMap.put("Endpoint", new String[]{api, "endpoints"});
    	apiMap.put("Event", new String[]{api, "events"});
    	apiMap.put("LimitRange", new String[]{api, "limitranges"});
    	apiMap.put("PersistentVolumeClaim", new String[]{api, "persistentvolumeclaims"});
    	apiMap.put("Pod", new String[]{api, "pods"});
    	apiMap.put("PodTemplate", new String[]{api, "podtemplates"});
    	apiMap.put("ReplicationController", new String[]{api, "replicationcontrollers"});
    	apiMap.put("ResourceQuota", new String[]{api, "resourcequotas"});
    	apiMap.put("Secret", new String[]{api, "secrets"});
    	apiMap.put("ServiceAccount", new String[]{api, "serviceaccounts"});
    	apiMap.put("Service", new String[]{api, "services"});
    	apiMap.put("Job", new String[]{apis, "jobs"});
    }
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftCreator(String apiURL, String namespace, String authToken, String verbose, String jsonyaml) {
        this.apiURL = apiURL;
        this.namespace = namespace;
        this.authToken = authToken;
        this.verbose = verbose;
        this.jsonyaml = jsonyaml;
    }

    public String getJsonyaml() {
    	return jsonyaml;
    }
    
    protected boolean makeRESTCall(boolean chatty, TaskListener listener, String path, ModelNode resource) {
		String response = null;
		URL url = null;
		if (apiMap.get(path) == null) {
			listener.getLogger().println(String.format("  The API resource \"%s\" is not currently supported by this step.", path));
			return false;
		}
		
    	try {
    		if (chatty) listener.getLogger().println("\nOpenShiftCreator POST URI " + apiMap.get(path)[0] + "/" + resource.get("apiVersion").asString() + "/namespaces/" +namespace + "/" + apiMap.get(path)[1]);
			url = new URL(apiURL + apiMap.get(path)[0] + "/" + resource.get("apiVersion").asString() + "/namespaces/" + namespace + "/" + apiMap.get(path)[1]);
		} catch (MalformedURLException e1) {
			e1.printStackTrace(listener.getLogger());
			return false;
		}
    	
    	IClient client = new ClientFactory().create(apiURL, auth);
    	if (client == null) {
    		return false;
    	}
		try {
	    	KubernetesResource kr = new KubernetesResource(resource, client, null);
			response = createHttpClient().post(url, 10 * 1000, kr);
			if (chatty) listener.getLogger().println("\nOpenShiftCreator REST POST response " + response);
		} catch (SocketTimeoutException e1) {
			if (chatty) e1.printStackTrace(listener.getLogger());
	    	listener.getLogger().println(String.format(" a socket level communication timeout to  \"%s\" occurred.", DISPLAY_NAME, apiURL));
			return false;
		} catch (HttpClientException e1) {
			if (chatty) e1.printStackTrace(listener.getLogger());
	    	listener.getLogger().println(String.format(" a HTTP level communication error to  \"%s\" occurred.", DISPLAY_NAME, apiURL));
			return false;
		}
		
		return true;
    }
    
    protected UrlConnectionHttpClient createHttpClient() {
		UrlConnectionHttpClient urlClient = new UrlConnectionHttpClient(
				null, "application/json", null, auth, null, null);
		urlClient.setAuthorizationStrategy(bearerToken);
		return urlClient;
    }
    
	public boolean coreLogic(Launcher launcher, TaskListener listener,
			EnvVars env) {
		boolean chatty = Boolean.parseBoolean(verbose);
    	listener.getLogger().println(String.format("\n\nStarting the \"%s\" step with the project \"%s\".", DISPLAY_NAME, namespace));
    	
    	// construct json/yaml node
    	ModelNode resources = ModelNode.fromJSONString(jsonyaml);
    	    	
    	//cycle through json and POST to appropriate resource
    	String kind = resources.get("kind").asString();
    	int created = 0;
    	int failed = 0;
    	if (kind.equalsIgnoreCase("List")) {
    		List<ModelNode> list = resources.get("items").asList();
    		for (ModelNode node : list) {
    			String path = node.get("kind").asString();
				listener.getLogger().println(String.format("  Creating a \"%s\"...", path));
				
    			boolean success = this.makeRESTCall(chatty, listener, path, node);
    			if (!success)
    				failed++;
    			else
    				created++;
    		}
    	} else {
    		String path = kind;
			listener.getLogger().println(String.format("  Creating a \"%s\"...", path));
			
    		boolean success = this.makeRESTCall(chatty, listener, path, resources);
    		if (success)
    			created = 1;
    		else
    			failed = 1;
    	}

    	if (failed > 0) {
    		listener.getLogger().println(String.format("\n\nExiting \"%s\" unsuccessfully, with %d resource(s) created at \"%s\" and %d failed attempt(s) under project \"%s\".", DISPLAY_NAME, created, apiURL, failed, namespace));
			return false;
    	} else {
    		listener.getLogger().println(String.format("\n\nExiting \"%s\" successfully, with %d resource(s) created at \"%s\" under project \"%s\".", DISPLAY_NAME, created, apiURL, namespace));
    		return true;
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

        public FormValidation doCheckJsonyaml(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckJsonyaml(value);
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
