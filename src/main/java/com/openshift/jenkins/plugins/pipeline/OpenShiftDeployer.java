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

import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;

import javax.servlet.ServletException;

import java.io.IOException;
import java.util.Map;

public class OpenShiftDeployer extends OpenShiftBaseStep {

	protected final static String DISPLAY_NAME = "Trigger OpenShift Deployment";
	
    protected final String depCfg;
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftDeployer(String apiURL, String depCfg, String namespace, String authToken, String verbose) {
    	super(apiURL, namespace, authToken, verbose);
        this.depCfg = depCfg;
    }

	public String getDepCfg() {
		return depCfg;
	}

	public String getDepCfg(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("depCfg"))
			return overrides.get("depCfg");
		return getDepCfg();
	}
	
	protected boolean bumpVersion(IDeploymentConfig dc, IClient client, TaskListener listener, Map<String,String> overrides) {
		int latestVersion = dc.getLatestVersionNumber() + 1;
		boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
		try {
			dc.setLatestVersionNumber(latestVersion);
			client.update(dc);
			if (chatty) 
				listener.getLogger().println("\nOpenShiftDeployer latest version now " + dc.getLatestVersionNumber());

		} catch (Throwable t) {
			if (chatty)
				t.printStackTrace(listener.getLogger());
			return false;
		}
		return true;
	}
	
	public boolean coreLogic(Launcher launcher, TaskListener listener,
			EnvVars env, Map<String,String> overrides) {
		boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
    	listener.getLogger().println(String.format(MessageConstants.START_DEPLOY_RELATED_PLUGINS, DISPLAY_NAME, getDepCfg(overrides), getNamespace(overrides)));
    	
    	// get oc client 
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    	
    	if (client != null) {
        	if (chatty)
        		listener.getLogger().println("\nOpenShiftDeployer wait " + getDescriptor().getWait());
        	// do the oc deploy with version bump ... may need to retry
        	long currTime = System.currentTimeMillis();
        	boolean deployDone = false;
        	boolean versionBumped = false;
			String state = null;
	    	IDeploymentConfig dc = null;
			IReplicationController rc = null; 
			while (System.currentTimeMillis() < (currTime + getDescriptor().getWait())) {
        		dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, getDepCfg(overrides), getNamespace(overrides));
        		if (dc != null) {
        			if (!versionBumped) {
        				// allow some retry in case the dc creation request happened before this step ran
        				versionBumped = bumpVersion(dc, client, listener, overrides);
        			}
        			
    				try {
    					rc = this.getLatestReplicationController(dc, client, overrides);
    					if (chatty)
    						listener.getLogger().println("\nOpenShiftDeployer returned rep ctrl " + rc);
    					if (rc != null) {
    						state = this.getReplicationControllerState(rc);
    						if (state.equalsIgnoreCase("Complete")) {
            					deployDone = true;
    						} else if (state.equalsIgnoreCase("Failed")) {
    	        		    	listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_BAD, DISPLAY_NAME, rc.getName(), state));
    							return false;
    						} else {
    							if (chatty)
    								listener.getLogger().println("\nOpenShiftDeploy current phase " + state);
    						}
    					} else {
    						if (chatty)
    							listener.getLogger().println("\nOpenShiftDeploy no rc for latest version yet");
    					}
    				} catch (Throwable t) {
    					if (chatty)
    						t.printStackTrace(listener.getLogger());
    				}
    				
					
					if (deployDone) {
						break;
					} else {
						if (chatty)
	        				listener.getLogger().println("\nOpenShiftDeployer wait 10 seconds, then try oc deploy again");
						try {
							Thread.sleep(10000);
						} catch (InterruptedException e) {
						}
					}
        				
        			
        		}
        	}
        	
        	if (!deployDone) {
		    	if (dc != null)
		    		listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_TRIGGER_TIMED_OUT, DISPLAY_NAME, rc.getName(), state));
		    	else
		    		listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_NO_CFG, DISPLAY_NAME, getDepCfg(overrides)));
        		return false;
        	}

	    	listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_GOOD_REPLICAS_IGNORED, DISPLAY_NAME, rc.getName()));
        	return true;
        	
        	
    	} else {
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
        	return ParamVerify.doCheckApiURL(value);
        }

        public FormValidation doCheckDepCfg(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckDepCfg(value);
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckNamespace(value);
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

