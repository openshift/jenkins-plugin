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

import com.openshift.internal.restclient.model.ReplicationController;
import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.capability.ICapability;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;

import javax.servlet.ServletException;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map.Entry;

import jenkins.tasks.SimpleBuildStep;

public class OpenShiftDeployer extends Builder implements SimpleBuildStep, Serializable {

    private String apiURL = "https://openshift.default.svc.cluster.local";
    private String depCfg = "frontend";
    private String namespace = "test";
    private String authToken = "";
    private String verbose = "false";
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftDeployer(String apiURL, String depCfg, String namespace, String authToken, String verbose) {
        this.apiURL = apiURL;
        this.depCfg = depCfg;
        this.namespace = namespace;
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
	    	listener.getLogger().println("\n\nBUILD STEP:  OpenShiftDeployer in perform for " + depCfg + " on namespace " + namespace);
	    	
	    	TokenAuthorizationStrategy bearerToken = new TokenAuthorizationStrategy(Auth.deriveBearerToken(build, authToken, listener, chatty));
	    	Auth auth = Auth.createInstance(chatty ? listener : null);
	    	    	
	    	// get oc client (sometime REST, sometimes Exec of oc command
	    	IClient client = new ClientFactory().create(apiURL, auth);
	    	
	    	if (client != null) {
	    		// seed the auth
	        	client.setAuthorizationStrategy(bearerToken);
	        	
	        	
	        	// do the oc deploy ... may need to retry
	        	long currTime = System.currentTimeMillis();
	        	boolean deployDone = false;
	        	if (chatty)
	        		listener.getLogger().println("\nOpenShiftDeployer wait " + getDescriptor().getWait());
	        	while (System.currentTimeMillis() < (currTime + getDescriptor().getWait())) {
	        		IDeploymentConfig dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, depCfg, namespace);
					int latestVersion =  -1;
	        		if (dc != null) {
	        			latestVersion = dc.getLatestVersionNumber();

	        			// oc deploy gets the rc after the dc prior to putting the dc;
	        			// we'll do the same ... currently, if a rc exists at the right level,
	        			// the deployment is cancelled by oc; we won't fail the build step, just 
	        			// print the info message; no rc result in exception with openshift-restclient-java api
	        			// but will still check for null in try block in case that changes
	    				try {
	    					IReplicationController rc = client.get(ResourceKind.REPLICATION_CONTROLLER, depCfg + "-" + latestVersion, namespace);
	    					if (chatty)
	    						listener.getLogger().println("\nOpenShiftDeployer returned rep ctrl " + rc);
	    					if (rc != null) {
	    						ReplicationController rcImpl = (ReplicationController)rc;
	    						String state = rc.getAnnotation("openshift.io/deployment.phase");//Deployment.getReplicationControllerState(rcImpl, chatty ? listener : null);
	    						if (!state.equals("Complete") && !state.equals("Failed")) {
	    							listener.getLogger().println("\n\nBUILD STEP EXIT:  " + rc.getName() + " is in progress.");
	    							return true;
	    						}
	    					}
	    				} catch (Throwable t) {
	    				}
	    				
	    				// now lets update the latest version of the dc
	    				try {
	    					dc.setLatestVersionNumber(latestVersion + 1);
	    					client.update(dc);
	    					deployDone = true;
	    				} catch (Throwable t) {
	    					if (chatty)
	    						t.printStackTrace(listener.getLogger());
	    				}
						
						if (deployDone) {
							break;
						} else {
							if (chatty)
		        				listener.getLogger().println("\nOpenShiftDeployer wait 10 seconds, then try oc scale again");
							try {
								Thread.sleep(10000);
							} catch (InterruptedException e) {
							}
						}
	        				
	        			
	        		}
	        	}
	        	
	        	if (!deployDone) {
	        		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftDeployer could not get oc deploy executed");
	        		return false;
	        	}
	        	
	        	
	    	} else {
	    		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftDeployer could not get oc client");
	    		return false;
	    	}

	    	listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftDeployer exit successfully");
	    	return true;
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
     * Descriptor for {@link OpenShiftDeployer}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    	private long wait = 60000;
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

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Trigger a deployment in OpenShift";
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

