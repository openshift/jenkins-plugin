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

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.openshift.internal.restclient.model.DeploymentConfig;
import com.openshift.internal.restclient.model.ReplicationController;
import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.capability.ICapability;

import javax.servlet.ServletException;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map.Entry;

import jenkins.tasks.SimpleBuildStep;

public class OpenShiftDeploymentVerifier extends Builder implements SimpleBuildStep, Serializable {

    private String apiURL = "https://openshift.default.svc.cluster.local";
    private String depCfg = "frontend";
    private String namespace = "test";
    private String replicaCount = "0";
    private String authToken = "";
    private String verbose = "false";
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftDeploymentVerifier(String apiURL, String depCfg, String namespace, String replicaCount, String authToken, String verbose) {
        this.apiURL = apiURL;
        this.depCfg = depCfg;
        this.namespace = namespace;
        this.replicaCount = replicaCount;
        this.authToken = authToken;
        this.verbose = verbose;
    }

    public String getVerbose() {
		return verbose;
	}

	public String getApiURL() {
		return apiURL;
	}

	public String getDepCfg() {
		return depCfg;
	}

	public String getNamespace() {
		return namespace;
	}
	
	public String getReplicaCount() {
		return replicaCount;
	}
	
	public String getAuthToken() {
		return authToken;
	}
	
	// unfortunately a base class would not have access to private fields in this class; could munge our way through
	// inspecting the methods and try to match field names and methods starting with get/set ... seems problematic;
	// for now, duplicating this small piece of logic in each build step
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
				// can assume field is of type String 
				String val = (String) f.get(this);
				if (chatty)
					listener.getLogger().println("inspectBuildEnvAndOverrideFields found field " + key + " with current value " + val);
				if (val == null)
					continue;
				String envval = env.get(val);
				if (chatty)
					listener.getLogger().println("inspectBuildEnvAndOverrideFields for field " + key + " got val from build env " + envval);
				if (envval != null && envval.length() > 0) {
					f.set(this, envval);
					overridenFields.put(f.getName(), val);
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
        	listener.getLogger().println("\n\nBUILD STEP:  OpenShiftDeploymentVerifier in perform checking for " + depCfg + " wanting to confirm we are at least at replica count " + replicaCount + " on namespace " + namespace);
        	
        	TokenAuthorizationStrategy bearerToken = new TokenAuthorizationStrategy(Auth.deriveBearerToken(build, authToken, listener, chatty));
        	Auth auth = Auth.createInstance(chatty ? listener : null);
        	
        	// get oc client (sometime REST, sometimes Exec of oc command
        	IClient client = new ClientFactory().create(apiURL, auth);
        	
        	if (client != null) {
        		// seed the auth
            	client.setAuthorizationStrategy(bearerToken);
            	
            	// explicitly set replica count, save that
            	int count = -1;
            	if (replicaCount.length() > 0)
            		count = Integer.parseInt(replicaCount);
            		
                						
            	// if the deployment config for this app specifies a desired replica count of 
            	// of greater than zero, let's also confirm the deployment occurs;
            	// first, get the deployment config
        		DeploymentConfig dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, depCfg, namespace);
        		
        		if (dc == null) {
        			listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftDeploymentVerification no valid deployment config found for " + depCfg);
        			return false;
        		}
        		
            	boolean dcWithReplicas = false;
            	boolean scaledAppropriately = false;
            			
    			// if replicaCount not set, get it from config
    			if (count == -1)
    				count = dc.getReplicas();
    			
    			if (count > 0)
    				dcWithReplicas = true;
    				
    			if (chatty) listener.getLogger().println("\nOpenShiftDeploymentVerifier checking if deployment out there for " + depCfg);
    			
    			// confirm the deployment has kicked in from completed build;
            	// in testing with the jenkins-ci sample, the initial deploy after
            	// a build is kinda slow ... gotta wait more than one minute
    			long currTime = System.currentTimeMillis();
    			if (chatty)
    				listener.getLogger().println("\nOpenShiftDeploymentVerifier wait " + getDescriptor().getWait());
    			while (System.currentTimeMillis() < (currTime + getDescriptor().getWait())) {
    				int latestVersion = -1;
    				try {
    					// refresh dc first
    					dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, depCfg, namespace);
    					latestVersion = dc.getLatestVersionNumber();
    				} catch (Throwable t) {
    					latestVersion = 0;
    				}
    				
    				if (chatty)
    					listener.getLogger().println("\nOpenShiftDeploymentVerifier latest version:  " + latestVersion);
    				
    				if (latestVersion == 0 && !dcWithReplicas) {
    					listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftDeploymentVerifier not yet deployed and expecting no replicas");
    					return true;
    				}
    				
    				ReplicationController rc = null;
    				try {
    					rc = client.get(ResourceKind.REPLICATION_CONTROLLER, depCfg + "-" + latestVersion, namespace);
    				} catch (Throwable t) {
    					if (chatty)
    						t.printStackTrace(listener.getLogger());
    				}
    					
    				if (rc != null) {
    					if (chatty)
    						listener.getLogger().println("\nOpenShiftDeploymentVerifier current rc " + rc.toPrettyString());
    					String state = rc.getAnnotation("openshift.io/deployment.phase");
    					// first check state
    	        		if (state.equalsIgnoreCase("Failed")) {
    	        			listener.getLogger().println("\n\nBUILD STEP EXIT: OpenShiftDeploymentVerifier deployment " + rc.getName() + " failed");
    	        			return false;
    	        		}
    					if (chatty) listener.getLogger().println("\nOpenShiftDeploymentVerifier rc current count " + rc.getCurrentReplicaCount() + " rc desired count " + rc.getDesiredReplicaCount() + " step verification amount " + count + " current state " + state);
    					
    					// then check replica count
    	        		if (rc.getCurrentReplicaCount() >= count && state.equalsIgnoreCase("Complete")) {
    	        			scaledAppropriately = true;
    	        			break;
    	        		}
    	        		
    				}
    									        										
            		try {
    					Thread.sleep(1000);
    				} catch (InterruptedException e) {
    				}

    			}
            			
            		
            	if (dcWithReplicas && scaledAppropriately ) {
            		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftDeploymentVerifier scaled with appropriate number of replicas and any necessary image changes");
            		return true;
            	}
            	
            	if (!dcWithReplicas) {
            		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftDeploymentVerifier scaled approrpiately with no replicas");
            		return true;
            	}
            	
            		
            		
        	} else {
        		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftDeploymentVerifier could not get oc client");
        		return false;
        	}

        	listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftDeploymentVerifier exit unsuccessfully, unexpected conditions occurred");
        	return false;
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
     * Descriptor for {@link OpenShiftDeploymentVerifier}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    	private long wait = 180000;
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

        public FormValidation doCheckDepCfg(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set depCfg");
            return FormValidation.ok();
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set namespace");
            return FormValidation.ok();
        }
        
        public FormValidation doCheckReplicaCount(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.ok();
            try {
            	Integer.decode(value);
            } catch (NumberFormatException e) {
            	return FormValidation.error("Please specify an integer for replicaCount");
            }
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
            return "Check Deployment Success in OpenShift";
        }
        
        public long getWait() {
        	return wait;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // pull info from formData, set appropriate instance field (which should have a getter), and call save().
        	wait = formData.getLong("wait");
            save();
            return super.configure(req,formData);
        }

    }

}

