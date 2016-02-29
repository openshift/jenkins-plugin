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

import com.openshift.internal.restclient.model.DeploymentConfig;
import com.openshift.internal.restclient.model.ReplicationController;
import com.openshift.restclient.ClientFactory;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;

import javax.servlet.ServletException;

import java.io.IOException;

public class OpenShiftScaler extends OpenShiftBaseStep {

	protected final static String DISPLAY_NAME = "Scale OpenShift Deployment";
	
    protected String depCfg = "frontend";
    protected String replicaCount = "0";
    protected String verifyReplicaCount = "false";
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftScaler(String apiURL, String depCfg, String namespace, String replicaCount, String authToken, String verbose, String verifyReplicaCount) {
        this.apiURL = apiURL;
        this.depCfg = depCfg;
        this.namespace = namespace;
        this.replicaCount = replicaCount;
        this.authToken = authToken;
        this.verbose = verbose;
        this.verifyReplicaCount = verifyReplicaCount;
}

	public String getDepCfg() {
		return depCfg;
	}

	public String getReplicaCount() {
		return replicaCount;
	}
	
	public String getVerifyReplicaCount() {
		return verifyReplicaCount;
	}
	
	@Override
	protected boolean coreLogic(Launcher launcher, TaskListener listener,
			EnvVars env) {
		boolean chatty = Boolean.parseBoolean(verbose);
    	boolean checkCount = Boolean.parseBoolean(verifyReplicaCount);
    	listener.getLogger().println(String.format("\n\nStarting the \"%s\" step with deployment config \"%s\" from the project \"%s\".", DISPLAY_NAME, depCfg, namespace));
    	
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
        		if (replicaCount.equals("0")) {
    		    	listener.getLogger().println(String.format("\n\nExiting \"%s\" successfully; no deployments for \"%s\" were found, so a replica count of \"0\" already exists.", DISPLAY_NAME, depCfg));
        			return true;
        		} else if (checkCount) {
    		    	listener.getLogger().println(String.format("\n\nExiting \"%s\" unsuccessfully; no deployments for \"%s\" were found.", DISPLAY_NAME, depCfg));
        			return false;
        		}
        	}
        	
        	listener.getLogger().println(String.format("  Scaling to \"%s\" replicas%s...", replicaCount, checkCount ? " and verifying the replica count is reached." : ""));        	
        	
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
	        		if (checkCount) {
	        			rcImpl = client.get(ResourceKind.REPLICATION_CONTROLLER, depId, namespace);
	        			if (rcImpl.getCurrentReplicaCount() == Integer.parseInt(replicaCount)) {
	        				scaleDone = true;
	        			}
	        		} else
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
        		if (!checkCount) {
        	    	listener.getLogger().println(String.format("\n\nExiting \"%s\" unsuccessfully; the call to \"%s\" failed.", DISPLAY_NAME, apiURL));        			
        		} else {
        	    	listener.getLogger().println(String.format("\n\nExiting \"%s\" unsuccessfully; the deployment  \"%s\" did not reach \"%s\" replica(s) in time.", DISPLAY_NAME, depId, replicaCount));
        		}
        		listener.getLogger().println("\n\nBUILD STEP EXIT:  OpenShiftScaler could not get the scale request through");
        		return false;
        	}
        	
	    	listener.getLogger().println(String.format("\n\nExiting \"%s\" successfully%s.", DISPLAY_NAME, checkCount ? String.format(", where the deployment \"%s\" reached \"%s\" replicas", depId, replicaCount) : ""));
        	return true;
        	        	
    	} else {
	    	listener.getLogger().println(String.format("\n\nExiting \"%s\" unsuccessfully; a client connection to \"%s\" could not be obtained.", DISPLAY_NAME, apiURL));
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
            if (value.length() == 0)
                return FormValidation.warning("Unless you specify a value here, one of the default API endpoints will be used; see this field's help or https://github.com/openshift/jenkins-plugin#common-aspects-across-the-rest-based-functions-build-steps-scm-post-build-actions for details");
            return FormValidation.ok();
        }

        public FormValidation doCheckDepCfg(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("You must set a DeploymentConfig name");
            return FormValidation.ok();
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.warning("Unless you specify a value here, the default namespace will be used; see this field's help or https://github.com/openshift/jenkins-plugin#common-aspects-across-the-rest-based-functions-build-steps-scm-post-build-actions for details");
            return FormValidation.ok();
        }
        
        public FormValidation doCheckReplicaCount(@QueryParameter String value)
                throws IOException, ServletException {
            try {
            	Integer.decode(value);
            } catch (NumberFormatException e) {
            	return FormValidation.error("Please specify an integer for the replica count you want to reach");
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

