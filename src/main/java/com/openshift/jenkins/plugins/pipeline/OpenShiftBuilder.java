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

import com.openshift.internal.restclient.http.HttpClientException;
import com.openshift.internal.restclient.http.UrlConnectionHttpClient;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.capability.CapabilityVisitor;
import com.openshift.restclient.capability.resources.IBuildTriggerable;
import com.openshift.restclient.model.IBuild;
import com.openshift.restclient.model.IBuildConfig;
import com.openshift.restclient.model.IPod;

import javax.servlet.ServletException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.List;
import java.util.Map;


public class OpenShiftBuilder extends OpenShiftBaseStep {
	
	protected final static String DISPLAY_NAME = "Trigger OpenShift Build";
	
    protected final String bldCfg;
    protected final String commitID;
    protected final String buildName;
    protected final String showBuildLogs;
    protected final String checkForTriggeredDeployments;
    protected final String waitTime;
    
    
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public OpenShiftBuilder(String apiURL, String bldCfg, String namespace, String authToken, String verbose, String commitID, String buildName, String showBuildLogs, String checkForTriggeredDeployments, String waitTime) {
    	super(apiURL, namespace, authToken, verbose);
        this.bldCfg = bldCfg;
        this.commitID = commitID;
        this.buildName = buildName;
        this.showBuildLogs = showBuildLogs;
        this.checkForTriggeredDeployments = checkForTriggeredDeployments;
        this.waitTime = waitTime;
    }

	public String getCommitID() {
		return commitID;
	}
	
	public String getCommitID(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("commitID"))
			return overrides.get("commitID");
		else return getCommitID();
	}

	public String getBuildName() {
		return buildName;
	}

	public String getBuildName(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("buildName"))
			return overrides.get("buildName");
		else return getBuildName();
	}

	public String getShowBuildLogs() {
		return showBuildLogs;
	}
	
	public String getShowBuildLogs(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("showBuildLogs"))
			return overrides.get("showBuildLogs");
		else return getShowBuildLogs();
	}

	public String getBldCfg() {
		return bldCfg;
	}
	
	public String getBldCfg(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("bldCfg"))
			return overrides.get("bldCfg");
		else return getBldCfg();
	}

	public String getCheckForTriggeredDeployments() {
		return checkForTriggeredDeployments;
	}
	
	public String getCheckForTriggeredDeployments(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("checkForTriggeredDeployments"))
			return overrides.get("checkForTriggeredDeployments");
		else return getCheckForTriggeredDeployments();
	}
	
	public String getWaitTime() {
		return waitTime;
	}
	
	public String getWaitTime(Map<String,String> overrides) {
		if (overrides != null && overrides.containsKey("waitTime"))
			return overrides.get("waitTime");
		else return getWaitTime();
	}
	
	protected IBuild startBuild(IBuildConfig bc, IBuild prevBld, Map<String,String> overrides) {
		IBuild bld = null;
		if (bc != null) {
			if (getCommitID(overrides) != null && getCommitID(overrides).length() > 0) {
				final String cid = getCommitID(overrides);
    			bld = bc.accept(new CapabilityVisitor<IBuildTriggerable, IBuild>() {
				    public IBuild visit(IBuildTriggerable triggerable) {
				 		return triggerable.trigger(cid);
				 	}
				 }, null);
			} else {
    			bld = bc.accept(new CapabilityVisitor<IBuildTriggerable, IBuild>() {
				    public IBuild visit(IBuildTriggerable triggerable) {
				 		return triggerable.trigger();
				 	}
				 }, null);
			}
		} else if (prevBld != null) {
			bld = prevBld.accept(new CapabilityVisitor<IBuildTriggerable, IBuild>() {
				public IBuild visit(IBuildTriggerable triggerable) {
					return triggerable.trigger();
				}
			}, null);
		}
		return bld;
	}
	
	protected void waitOnBuild(IClient client, long startTime, String bldId, TaskListener listener, Map<String,String> overrides, long wait) {
		IBuild bld = null;
		String bldState = null;
		//TODO leaving this code, commented out, in for now ... the use of the oc binary for log following allows for
		// interactive log dumping, while simply make the REST call provides dumping of the build logs once the build is
		// complete        						
		// get log "retrieve" and dump build logs
//		IPodLogRetrieval logger = pod.getCapability(IPodLogRetrieval.class);
//		listener.getLogger().println("\n\nOpenShiftBuilder obtained pod logger " + logger);
		
//		if (logger != null) {
			
		// get internal OS Java REST Client error if access pod logs while bld is in Pending state
		// instead of Running, Complete, or Failed
		while (System.currentTimeMillis() < (startTime + wait)) {
			bld = client.get(ResourceKind.BUILD, bldId, getNamespace(overrides));
			bldState = bld.getStatus();
			if (Boolean.parseBoolean(getVerbose(overrides)))
				listener.getLogger().println("\nOpenShiftBuilder bld state:  " + bldState);
			if ("Pending".equals(bldState) || "New".equals(bldState)) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			} else {
				break;
			}
		}
//			} else {
//			listener.getLogger().println("\n\nOpenShiftBuilder logger for pod " + pod.getName() + " not available");
//			bldState = pod.getStatus();
//		}
	}
	
	protected void dumpLogs(String bldId, TaskListener listener, Map<String,String> overrides, long wait) {
		// create stream and copy bytes
    	URL url = null;
    	try {
			url = new URL(getApiURL(overrides) + "/oapi/v1/namespaces/"+getNamespace(overrides)+"/builds/" + bldId + "/log?follow=true");
		} catch (MalformedURLException e1) {
			e1.printStackTrace(listener.getLogger());
		}
		UrlConnectionHttpClient urlClient = new UrlConnectionHttpClient(
				null, "application/json", null, auth, null, null);
		urlClient.setAuthorizationStrategy(bearerToken);
		String response = null;
		try {
			response = urlClient.get(url, (int) wait);
		} catch (SocketTimeoutException e1) {
			e1.printStackTrace(listener.getLogger());
		} catch (HttpClientException e1) {
			e1.printStackTrace(listener.getLogger());
		}
		listener.getLogger().println(response);
		
		//TODO leaving this code, commented out, in for now ... the use of the oc binary for log following allows for
		// interactive log dumping, while simply make the REST call provides dumping of the build logs once the build is
		// complete        						
//		InputStream logs = new BufferedInputStream(logger.getLogs(true));
//		int b;
//		try {
//			// we still process the build stream if followLogs is false, as 
//			// it is a good indication of build progress (vs. periodically polling);
//			// we simply do not dump the data to the Jenkins console if set to false
//			while ((b = logs.read()) != -1) {
//				if (follow)
//					listener.getLogger().write(b);
//			}
//		} catch (IOException e) {
//			e.printStackTrace(listener.getLogger());
//		} finally {
//			try {
//				logs.close();
//			} catch (final IOException e) {
//				e.printStackTrace(listener.getLogger());
//			}
//		}
	}
	
	public boolean coreLogic(Launcher launcher, TaskListener listener, EnvVars env, Map<String,String> overrides) {
		boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
		boolean checkDeps = Boolean.parseBoolean(getCheckForTriggeredDeployments(overrides));
    	listener.getLogger().println(String.format(MessageConstants.START_BUILD_RELATED_PLUGINS, DISPLAY_NAME, getBldCfg(overrides), getNamespace(overrides)));
		
    	boolean follow = Boolean.parseBoolean(getShowBuildLogs(overrides));
    	if (chatty)
    		listener.getLogger().println("\nOpenShiftBuilder logger follow " + follow);
    	
    	// get oc client 
    	IClient client = this.getClient(listener, DISPLAY_NAME, overrides);
    	
    	if (client != null) {
			long startTime = System.currentTimeMillis();
			boolean skipBC = getBuildName(overrides) != null && getBuildName(overrides).length() > 0;
        	IBuildConfig bc = null;
        	IBuild prevBld = null;
        	if (!skipBC) {
        		bc = client.get(ResourceKind.BUILD_CONFIG, getBldCfg(overrides), getNamespace(overrides));
        	} else {
        		prevBld = client.get(ResourceKind.BUILD, getBuildName(overrides), getNamespace(overrides));
        	}
        	

        	if (chatty)
        		listener.getLogger().println("\nOpenShiftBuilder build config retrieved " + bc + " buildName " + getBuildName(overrides));
        	
        	if (bc != null || prevBld != null) {
    			
        		// Trigger / start build
    			IBuild bld = this.startBuild(bc, prevBld, overrides);
    			
    			
    			if(bld == null) {
    		    	listener.getLogger().println(MessageConstants.EXIT_BUILD_NO_BUILD_OBJ);
    				return false;
    			} else {
    				String bldId = bld.getName();
    				if (!checkDeps)
    					listener.getLogger().println(String.format(MessageConstants.WAITING_ON_BUILD, bldId));
    				else
    					listener.getLogger().println(String.format(MessageConstants.WAITING_ON_BUILD_PLUS_DEPLOY, bldId));
    				
    				
    				boolean foundPod = false;
    				startTime = System.currentTimeMillis();
					if (chatty)
						listener.getLogger().println("\nOpenShiftBuilder global wait time " + getDescriptor().getWait() + " and step specific " + getWaitTime(overrides));
					
					long wait = getDescriptor().getWait();
					if (getWaitTime(overrides).length() > 0) {
						wait = Long.parseLong(getWaitTime(overrides));
					}
    				
    				// Now find build Pod, attempt to dump the logs to the Jenkins console
    				while (!foundPod && startTime > (System.currentTimeMillis() - wait)) {
    					
    					// fetch current list of pods ... this has proven to not be immediate in finding latest
    					// entries when compared with say running oc from the cmd line
        				List<IPod> pods = client.list(ResourceKind.POD, getNamespace(overrides));
        				for (IPod pod : pods) {
        					if (chatty)
        						listener.getLogger().println("\nOpenShiftBuilder found pod " + pod.getName());
     
        					// build pod starts with build id
        					if(pod.getName().startsWith(bldId)) {
        						foundPod = true;
        						if (chatty)
        							listener.getLogger().println("\nOpenShiftBuilder found build pod " + pod);
        						
        							waitOnBuild(client, startTime, bldId, listener, overrides, wait);
            						
            						if (follow)
            							dumpLogs(bldId, listener, overrides, wait / 100);
    								
        					}
        					
        					if (foundPod)
        						break;
        				}
        				
        				try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}
        				
    				}
    				
    				if (!foundPod) {
        		    	listener.getLogger().println(String.format(MessageConstants.EXIT_BUILD_NO_POD_OBJ, bldId));
    					return false;
    				}
    				
    				return this.verifyBuild(startTime, wait, client, getBldCfg(overrides), bldId, getNamespace(overrides), chatty, listener, DISPLAY_NAME, checkDeps);
    				    				
    			}
        		
        		
        	} else {
		    	listener.getLogger().println(String.format(MessageConstants.EXIT_BUILD_NO_BUILD_CONFIG_OBJ, getBldCfg(overrides)));
        		return false;
        	}
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
     * Descriptor for {@link OpenShiftBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    	private long wait = 300000;
    	
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

        public FormValidation doCheckBldCfg(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckBldCfg(value);
        }

        public FormValidation doCheckNamespace(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckNamespace(value);
        }
        
        public FormValidation doCheckCheckForTriggeredDeployments(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckCheckForTriggeredDeployments(value);
        }
        
        public FormValidation doCheckWaitTime(@QueryParameter String value)
                throws IOException, ServletException {
        	return ParamVerify.doCheckCheckForWaitTime(value);
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

