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

import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.authorization.TokenAuthorizationStrategy;
import com.openshift.restclient.model.IBuild;

import javax.servlet.ServletException;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jenkins.tasks.SimpleBuildStep;

public class OpenShiftBuildVerifier extends Builder implements SimpleBuildStep, Serializable {
	
    private String apiURL = "https://openshift.default.svc.cluster.local";
    private String bldCfg = "frontend";
    private String namespace = "test";
    private String authToken = "";
    private String verbose = "false";
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftBuildVerifier(String apiURL, String bldCfg, String namespace, String authToken, String verbose) {
        this.apiURL = apiURL;
        this.bldCfg = bldCfg;
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

	public String getBldCfg() {
		return bldCfg;
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
	
    protected boolean coreLogic(AbstractBuild build, Launcher launcer, TaskListener listener) {
		boolean chatty = Boolean.parseBoolean(verbose);
		HashMap<String,String> overrides = inspectBuildEnvAndOverrideFields(build, listener, chatty);
		try {
	    	listener.getLogger().println("\n\nBUILD STEP:  OpenShiftBuildVerifier in perform for " + bldCfg + " on namespace " + namespace);
	    	
	    	TokenAuthorizationStrategy bearerToken = new TokenAuthorizationStrategy(Auth.deriveBearerToken(build, authToken, listener, chatty));
	    	Auth auth = Auth.createInstance(chatty ? listener : null);
	    	
	    	
	    	// get oc client (sometime REST, sometimes Exec of oc command
	    	IClient client = new ClientFactory().create(apiURL, auth);
	    	
	    	if (client != null) {
	    		// seed the auth
	        	client.setAuthorizationStrategy(bearerToken);
	        	
				String bldState = null;
				long currTime = System.currentTimeMillis();
				if (chatty)
					listener.getLogger().println("\nOpenShiftBuildVerifier wait " + getDescriptor().getWait());
				while (System.currentTimeMillis() < (currTime + getDescriptor().getWait())) {
					List<IBuild> blds = client.list(ResourceKind.BUILD, namespace);
					Map<String,IBuild> ourBlds = new HashMap<String,IBuild>();
					List<String> ourKeys = new ArrayList<String>();
					for (IBuild bld : blds) {
						if (bld.getName().startsWith(bldCfg)) {
							ourKeys.add(bld.getName());
							ourBlds.put(bld.getName(), bld);
						}
					}
					
					if (ourKeys.size() > 0) {
						Collections.sort(ourKeys);
						IBuild bld = ourBlds.get(ourKeys.get(ourKeys.size() - 1));
						if (chatty)
							listener.getLogger().println("\nOpenShiftBuildVerifier latest bld id " + ourKeys.get(ourKeys.size() - 1));
						bldState = bld.getStatus();
					}
					
					if (chatty)
						listener.getLogger().println("\nOpenShiftBuildVerifier post bld launch bld state:  " + bldState);
					if (bldState == null || !bldState.equals("Complete")) {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}
					} else {
						break;
					}
				}
				
				if (bldState == null || !bldState.equals("Complete")) {
					listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftBuildVerifier build state is " + bldState + ".  If possible interrogate the OpenShift server with the oc command and inspect the server logs");
					return false;
				} else {
					if (Deployment.didAllImagesChangeIfNeeded(bldCfg, listener, chatty, client, namespace, getDescriptor().getWait())) {
						listener.getLogger().println("\nBUILD STEP EXIT: OpenShiftBuildVerifier exit successfully");
						return true;
					} else {
						listener.getLogger().println("\nBUILD STEP EXIT:  OpenShiftBuildVerifier not all deployments with ImageChange triggers based on the output of this build config triggered with new images");
						return false;
					}
				}
	    				        		
	    	} else {
	    		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftBuildVerifier could not get oc client");
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
/*
    public void setApiURL(String apiURL) {
		this.apiURL = apiURL;
	}

	public void setBldCfg(String bldCfg) {
		this.bldCfg = bldCfg;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public void setAuthToken(String authToken) {
		this.authToken = authToken;
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
     * Descriptor for {@link OpenShiftBuildVerifier}. Used as a singleton.
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

        public FormValidation doCheckBldCfg(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set bldCfg");
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
            return "Get latest OpenShift build status";
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

