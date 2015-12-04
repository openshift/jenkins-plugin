package com.openshift.jenkins.plugins.pipeline;
import hudson.EnvVars;
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

import javax.servlet.ServletException;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jenkins.tasks.SimpleBuildStep;

public class OpenShiftCreator extends Builder implements SimpleBuildStep, Serializable {

    private String apiURL = "https://openshift.default.svc.cluster.local";
    private String namespace = "test";
    private String authToken = "";
    private String verbose = "false";
    private String jsonyaml = "";
    
    private static final Map<String, String[]> apiMap;
    static
    {
        String api = "/api";
        String oapi = "/oapi";

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
    		if (chatty) listener.getLogger().println("\nOpenShiftCreator POST URI " + apiMap.get(path)[0] + "/v1/namespaces/" +namespace + "/" + apiMap.get(path)[1]);
			url = new URL(apiURL + apiMap.get(path)[0] + "/v1/namespaces/" + namespace + "/" + apiMap.get(path)[1]);
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
			if (chatty) listener.getLogger().println("\nOpenShiftCreator REST POST response " + response);
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
    
	// unfortunately a base class would not have access to private fields in this class; could munge our way through
	// inspecting the methods and try to match field names and methods starting with get/set ... seems problematic;
	// for now, duplicating this small piece of logic in each build step is the path taken
	protected HashMap<String,String> inspectBuildEnvAndOverrideFields(AbstractBuild build, TaskListener listener, boolean chatty) {
		String className = this.getClass().getName();
		HashMap<String,String> overridenFields = new HashMap<String,String>();
		try {
			EnvVars env = build.getEnvironment(listener);
			if (env == null)
				return overridenFields;
			Class<?> c = Class.forName(className);
			Field[] fields = c.getDeclaredFields();
			for (Field f : fields) {
				String key = f.getName();
				// the json field can show up as a Map 
				Object val = f.get(this);
				if (chatty)
					listener.getLogger().println("inspectBuildEnvAndOverrideFields found field " + key + " with current value " + val);
				if (val == null)
					continue;
				if (!(val instanceof String))
					continue;
				Object envval = env.get(val);
				if (chatty)
					listener.getLogger().println("inspectBuildEnvAndOverrideFields for field " + key + " got val from build env " + envval);
				if (envval != null) {
					f.set(this, envval);
					overridenFields.put(f.getName(), (String)val);
				}
			}
		} catch (ClassNotFoundException e1) {
			e1.printStackTrace(listener.getLogger());
		} catch (IOException e) {
			e.printStackTrace(listener.getLogger());
		} catch (InterruptedException e) {
			e.printStackTrace(listener.getLogger());
		} catch (IllegalArgumentException e) {
			e.printStackTrace(listener.getLogger());
		} catch (IllegalAccessException e) {
			e.printStackTrace(listener.getLogger());
		}
		return overridenFields;
	}
	
	protected void restoreOverridenFields(HashMap<String,String> overrides, TaskListener listener) {
		String className = this.getClass().getName();
		try {
			Class<?> c = Class.forName(className);
			for (Entry<String, String> entry : overrides.entrySet()) {
				Field f = c.getDeclaredField(entry.getKey());
				f.set(this, entry.getValue());
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace(listener.getLogger());
		} catch (NoSuchFieldException e) {
			e.printStackTrace(listener.getLogger());
		} catch (SecurityException e) {
			e.printStackTrace(listener.getLogger());
		} catch (IllegalArgumentException e) {
			e.printStackTrace(listener.getLogger());
		} catch (IllegalAccessException e) {
			e.printStackTrace(listener.getLogger());
		}
	}
	
    protected boolean coreLogic(AbstractBuild build, Launcher launcher, TaskListener listener) {
		boolean chatty = Boolean.parseBoolean(verbose);
		HashMap<String,String> overrides = inspectBuildEnvAndOverrideFields(build, listener, chatty);
		try {
	    	listener.getLogger().println("\n\nBUILD STEP:  OpenShiftImageTagger in perform on namespace " + namespace);
	    	
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
	    			String path = node.get("kind").asString();
	    			success = this.makeRESTCall(chatty, listener, path, auth, urlClient, node);
	    			if (!success)
	    				break;
	    		}
	    	} else {
	    		String path = kind;
	    		success = this.makeRESTCall(chatty, listener, path, auth, urlClient, resources);
	    	}

	    	if (success)
	    		listener.getLogger().println("\n\n OpenShiftCreator BUILD STEP EXIT:  resources(s) created");
			return success;
		} finally {
			this.restoreOverridenFields(overrides, listener);
		}
    	
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
