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
import com.openshift.restclient.model.IBuild;

import javax.servlet.ServletException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class OpenShiftBuildVerifier extends OpenShiftBaseStep {
	
    protected String bldCfg = "frontend";
    protected String checkForTriggeredDeployments = "false";
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftBuildVerifier(String apiURL, String bldCfg, String namespace, String authToken, String verbose, String checkForTriggeredDeployments) {
        this.apiURL = apiURL;
        this.bldCfg = bldCfg;
        this.namespace = namespace;
        this.authToken = authToken;
        this.verbose = verbose;
        this.checkForTriggeredDeployments = checkForTriggeredDeployments;
    }

	public String getBldCfg() {
		return bldCfg;
	}
    
	public String getCheckForTriggeredDeployments() {
		return checkForTriggeredDeployments;
	}
	
	@Override
	protected boolean coreLogic(Launcher launcher, TaskListener listener,
			EnvVars env) {
		boolean chatty = Boolean.parseBoolean(verbose);
		boolean checkDeps = Boolean.parseBoolean(checkForTriggeredDeployments);
    	listener.getLogger().println("\n\nBUILD STEP:  OpenShiftBuildVerifier in perform for " + bldCfg + " on namespace " + namespace);
    	
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
				if (checkDeps) {
					if (Deployment.didAllImagesChangeIfNeeded(bldCfg, listener, chatty, client, namespace, getDescriptor().getWait())) {
						listener.getLogger().println("\nBUILD STEP EXIT: OpenShiftBuildVerifier exit successfully");
						return true;
					} else {
						listener.getLogger().println("\nBUILD STEP EXIT:  OpenShiftBuildVerifier not all deployments with ImageChange triggers based on the output of this build config triggered with new images");
						return false;
					}
				} else {
					listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftBuilderVerifier exit successfully (no deploy check)");
					return true;
				}
			}
    				        		
    	} else {
    		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftBuildVerifier could not get oc client");
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
                return FormValidation.warning("Unless you specify a value here, one of the default API endpoints will be used; see this field's help or https://github.com/openshift/jenkins-plugin#common-aspects-across-the-rest-based-functions-build-steps-scm-post-build-actions for details");
            return FormValidation.ok();
        }

        public FormValidation doCheckBldCfg(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("You must set a BuildConfig name");
            return FormValidation.ok();
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.warning("Unless you specify a value here, the default namespace will be used; see this field's help or https://github.com/openshift/jenkins-plugin#common-aspects-across-the-rest-based-functions-build-steps-scm-post-build-actions for details");
            return FormValidation.ok();
        }
        
        public FormValidation doCheckCheckForTriggeredDeployments(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set trigger check");
            try {
            	Boolean.parseBoolean(value);
            } catch (Throwable t) {
            	return FormValidation.error(t.getMessage());
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

