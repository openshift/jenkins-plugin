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

public class OpenShiftScaler extends OpenShiftBaseStep {

	protected final static String DISPLAY_NAME = "Scale OpenShift Deployment";
	
    protected final String depCfg;
    protected final String replicaCount;
    protected final String verifyReplicaCount;
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftScaler(String apiURL, String depCfg, String namespace, String replicaCount, String authToken, String verbose, String verifyReplicaCount) {
    	super(apiURL, namespace, authToken, verbose);
        this.depCfg = depCfg;
        this.replicaCount = replicaCount;
        this.verifyReplicaCount = verifyReplicaCount;
    }

    // generically speaking, Jenkins will always pass in non-null field values.  However, as we have periodically
    // added new fields, jobs created with earlier versions of the plugin get null for the new fields.  Hence, 
    // we have introduced the generic convention (even for fields that existed in the intial incarnations of the plugin)
    // of insuring nulls are not returned for field getters

	public String getDepCfg() {
		if (depCfg == null)
			return "";
		return depCfg;
	}

	public String getDepCfg(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("depCfg"))
			return overrides.get("depCfg");
		return getDepCfg();
	}
	
	public String getReplicaCount() {
		if (replicaCount == null)
			return "";
		return replicaCount;
	}
	
	public String getReplicaCount(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("replicaCount"))
			return overrides.get("replicaCount");
		return getReplicaCount();
	}
	
	public String getVerifyReplicaCount() {
		if (verifyReplicaCount == null)
			return "";
		return verifyReplicaCount;
	}
	
	public String getVerifyReplicaCount(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("verifyReplicaCount"))
			return overrides.get("verifyReplicaCount");
		return getVerifyReplicaCount();
	}
	
	public boolean coreLogic(Launcher launcher, TaskListener listener,
			EnvVars env, Map<String,String> overrides) {
		boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
    	boolean checkCount = Boolean.parseBoolean(getVerifyReplicaCount(overrides));
    	listener.getLogger().println(String.format(MessageConstants.START_DEPLOY_RELATED_PLUGINS, DISPLAY_NAME, getDepCfg(overrides), getNamespace(overrides)));
    	
    	// get oc client 
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    	
    	if (client != null) {
        	IReplicationController rc = null;
        	IDeploymentConfig dc = null;
        	long currTime = System.currentTimeMillis();
        	// in testing with the jenkins-ci sample, the initial deploy after
        	// a build is kinda slow ... gotta wait more than one minute
        	if (chatty)
        		listener.getLogger().println("\nOpenShiftScaler wait " + getDescriptor().getWait());
        	
        	if (!checkCount)
        		listener.getLogger().println(String.format(MessageConstants.SCALING, getReplicaCount(overrides)));
        	else
        		listener.getLogger().println(String.format(MessageConstants.SCALING_PLUS_REPLICA_CHECK, getReplicaCount(overrides)));        	
        	
        	// do the oc scale ... may need to retry        	
        	boolean scaleDone = false;
        	while (System.currentTimeMillis() < (currTime + getDescriptor().getWait())) {
        		dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, getDepCfg(overrides), getNamespace(overrides));
        		if (dc == null) {
			    	listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_NO_CFG, DISPLAY_NAME, getDepCfg(overrides)));
	    			return false;
        		}
        		
        		if (dc.getLatestVersionNumber() > 0)
        			rc = this.getLatestReplicationController(dc, client, overrides);
            	if (rc == null) {
            		//TODO if not found, and we are scaling down to zero, don't consider an error - this may be safety
            		// measure to scale down if exits ... perhaps we make this behavior configurable over time, but for now.
            		// we refrain from adding yet 1 more config option
            		if (getReplicaCount(overrides).equals("0")) {
        		    	listener.getLogger().println(String.format(MessageConstants.EXIT_SCALING_NOOP, getDepCfg(overrides)));
            			return true;
            		}
            	} else {
            		int count = Integer.decode(getReplicaCount(overrides));
    	        	rc.setDesiredReplicaCount(count);
    	        	if (chatty)
    	        		listener.getLogger().println("\nOpenShiftScaler setting desired replica count of " + getReplicaCount(overrides) + " on " + rc.getName());
    	        	try {
    	        		rc = client.update(rc);
    	        		if (chatty)
    	        			listener.getLogger().println("\nOpenShiftScaler rc returned from update current replica count " + rc.getCurrentReplicaCount() + " desired count " + rc.getDesiredReplicaCount());
						scaleDone = this.isReplicationControllerScaledAppropriately(rc, checkCount, count);
    	        	} catch (Throwable t) {
    	        		if (chatty)
    	        			t.printStackTrace(listener.getLogger());
    	        	}
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
        		if (!checkCount) {
        	    	listener.getLogger().println(String.format(MessageConstants.EXIT_SCALING_BAD, getApiURL(overrides)));        			
        		} else {
        	    	listener.getLogger().println(String.format(MessageConstants.EXIT_SCALING_TIMED_OUT, rc.getName(), getReplicaCount(overrides)));
        		}
        		return false;
        	}
        	
	    	if (!checkCount)
	    		listener.getLogger().println(String.format(MessageConstants.EXIT_SCALING_GOOD, rc.getName()));
	    	else
	    		listener.getLogger().println(String.format(MessageConstants.EXIT_SCALING_GOOD_REPLICAS_GOOD, rc.getName(), getReplicaCount(overrides)));
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
        
        
        public FormValidation doCheckReplicaCount(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckReplicaCountRequired(value);
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

