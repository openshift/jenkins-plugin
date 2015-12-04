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

import javax.servlet.ServletException;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map.Entry;

import jenkins.tasks.SimpleBuildStep;

/**
 * OpenShift {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link OpenShiftScaler} is created. The created
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
public class OpenShiftScaler extends Builder implements SimpleBuildStep, Serializable {

    private String apiURL = "https://openshift.default.svc.cluster.local";
    private String depCfg = "frontend";
    private String namespace = "test";
    private String replicaCount = "0";
    private String authToken = "";
    private String verbose = "false";
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftScaler(String apiURL, String depCfg, String namespace, String replicaCount, String authToken, String verbose) {
        this.apiURL = apiURL;
        this.depCfg = depCfg;
        this.namespace = namespace;
        this.replicaCount = replicaCount;
        this.authToken = authToken;
        this.verbose = verbose;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
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

    public String getVerbose() {
		return verbose;
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
	    	listener.getLogger().println("\n\nBUILD STEP:  OpenShiftScaler in perform for " + depCfg + " wanting to get to replica count " + replicaCount + " on namespace " + namespace);
	    	
	    	TokenAuthorizationStrategy bearerToken = new TokenAuthorizationStrategy(Auth.deriveBearerToken(build, authToken, listener, chatty));
	    	Auth auth = Auth.createInstance(chatty ? listener : null);
	    	    	
	    	// get oc client (sometime REST, sometimes Exec of oc command
	    	IClient client = new ClientFactory().create(apiURL, auth);
	    	
	    	if (client != null) {
	    		// seed the auth
	        	client.setAuthorizationStrategy(bearerToken);
	        	
	        	String depId = null;
	        	ReplicationController rc = null;
	        	int latestVersion = -1;
	        	// find corresponding rep ctrl and scale it to the right value
	        	long currTime = System.currentTimeMillis();
	        	// in testing with the jenkins-ci sample, the initial deploy after
	        	// a build is kinda slow ... gotta wait more than one minute
	        	if (chatty)
	        		listener.getLogger().println("\nOpenShiftScaler wait " + getDescriptor().getWait());
	        	while (System.currentTimeMillis() < (currTime + getDescriptor().getWait())) {
	            	// get ReplicationController ref
	        		DeploymentConfig dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, depCfg, namespace);
	        		
	        		if (dc == null) {
	        			if (chatty) listener.getLogger().println("\nOpenShiftScaler dc is null");
	        		} else {
	    				try {
	    					latestVersion = dc.getLatestVersionNumber();//Deployment.getDeploymentConfigLatestVersion(dc, chatty ? listener : null).asInt();
	    				} catch (Throwable t) {
	    					latestVersion = 0;
	    				}
	    				depId = depCfg + "-" + latestVersion;
	    				try {
	    					rc = client.get(ResourceKind.REPLICATION_CONTROLLER, depId, namespace);
	    				} catch (Throwable t) {
	    					
	    				}
	        		}
	        		
	            	// if they want to scale down to 0 and there is no rc to begin with, just punt / consider a no-op
	            	if (rc != null || replicaCount.equals("0")) {
	            		if (chatty) listener.getLogger().println("\nOpenShiftScaler rc to use " + rc);
	            		break;
	            	} else {
						if (chatty) listener.getLogger().println("\nOpenShiftScaler wait 10 seconds, then look for rep ctrl again");
						try {
							Thread.sleep(10000);
						} catch (InterruptedException e) {
						}
	            	}
	        	}
	        	
	        	if (rc == null) {
	        		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftScaler did not find any replication controllers for " + depCfg);
	        		//TODO if not found, and we are scaling down to zero, don't consider an error - this may be safety
	        		// measure to scale down if exits ... perhaps we make this behavior configurable over time, but for now.
	        		// we refrain from adding yet 1 more config option
	        		if (replicaCount.equals("0"))
	        			return true;
	        		else
	        			return false;
	        	}
	        	
	        	// do the oc scale ... may need to retry        	
	        	boolean scaleDone = false;
	        	while (System.currentTimeMillis() < (currTime + getDescriptor().getWait())) {
	        		// Find the right node in the json and update it
	        		// refetch to avoid optimistic update collision on k8s side
		        	ReplicationController rcImpl = client.get(ResourceKind.REPLICATION_CONTROLLER, depId, namespace);
		        	rcImpl.setDesiredReplicaCount(Integer.decode(replicaCount));
		        	if (chatty)
		        		listener.getLogger().println("\nOpenShiftScaler setting desired replica count of " + replicaCount + " on " + depId);
		        	try {
		        		rcImpl = client.update(rcImpl);
		        		if (chatty)
		        			listener.getLogger().println("\nOpenShiftScaler rc returned from update current replica count " + rcImpl.getCurrentReplicaCount() + " desired count " + rcImpl.getDesiredReplicaCount());
			        	scaleDone = true;
		        	} catch (Throwable t) {
		        		if (chatty)
		        			t.printStackTrace(listener.getLogger());
		        	}
					
					if (scaleDone) {
						break;
					} else {
						if (chatty) listener.getLogger().println("\nOpenShiftScaler will wait 10 seconds, then try to scale again");
						try {
							Thread.sleep(10000);
						} catch (InterruptedException e) {
						}
					}
		    	}
	        	
	        	if (!scaleDone) {
	        		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftScaler could not get the scale request through");
	        		return false;
	        	}
	        	
	        	listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftScaler got the scale request through");
	        	return true;
	        	        	
	    	} else {
	    		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftScaler could not get the scale request through");
	    		return false;
	    	}
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
     * Descriptor for {@link OpenShiftScaler}. Used as a singleton.
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

        public FormValidation doCheckReplicaCount(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set replicaCount");
            try {
            	Integer.decode(value);
            } catch (NumberFormatException e) {
            	return FormValidation.error("Please specify an integer for replicaCount");
            }
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
            return "Scale deployments in OpenShift";
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

