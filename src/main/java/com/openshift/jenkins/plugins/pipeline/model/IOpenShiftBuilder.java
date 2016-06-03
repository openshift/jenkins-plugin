package com.openshift.jenkins.plugins.pipeline.model;

import hudson.Launcher;
import hudson.model.TaskListener;

import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.openshift.internal.restclient.http.HttpClientException;
import com.openshift.internal.restclient.http.UrlConnectionHttpClient;
import com.openshift.jenkins.plugins.pipeline.Auth;
import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.capability.CapabilityVisitor;
import com.openshift.restclient.capability.resources.IBuildTriggerable;
import com.openshift.restclient.model.IBuild;
import com.openshift.restclient.model.IBuildConfig;
import com.openshift.restclient.model.IPod;

public interface IOpenShiftBuilder extends IOpenShiftPlugin {

	public final static String DISPLAY_NAME = "Trigger OpenShift Build";
	
	default String getDisplayName() {
		return DISPLAY_NAME;
	}
	
	String getCommitID();
		
	String getBuildName();
		
	String getShowBuildLogs();
		
	String getBldCfg();
		
	String getCheckForTriggeredDeployments();
		
	String getWaitTime();
	
	String getWaitTime(Map<String, String> overrides);
	
	default String getCommitID(Map<String,String> overrides) {
		return getOverride(getCommitID(), overrides);
	}

	default String getBuildName(Map<String,String> overrides) {
		return getOverride(getBuildName(), overrides);
	}

	default String getShowBuildLogs(Map<String,String> overrides) {
		return getOverride(getShowBuildLogs(), overrides);
	}

	default String getBldCfg(Map<String,String> overrides) {
		return getOverride(getBldCfg(), overrides);
	}

	default String getCheckForTriggeredDeployments(Map<String,String> overrides) {
		return getOverride(getCheckForTriggeredDeployments(), overrides);
	}
	
	default IBuild startBuild(IBuildConfig bc, IBuild prevBld, Map<String,String> overrides) {
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
	
	default String waitOnBuild(IClient client, long startTime, String bldId, TaskListener listener, Map<String,String> overrides, long wait, boolean follow, boolean chatty) {
		IBuild bld = null;
		String bldState = null;
		String logs = "";
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
			
			if (follow) {
				String tmp = this.dumpLogs(bldId, listener, overrides, wait / 10, chatty);
				if (chatty)
					listener.getLogger().println("\n current logs :  " + tmp);
				if (tmp != null && tmp.length() > logs.length()) {
					logs = tmp;
				}
			}
							
			if ("Pending".equals(bldState) || "New".equals(bldState) || "Running".equals(bldState)) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			} else {
				break;
			}
		}
		
		return logs;
//			} else {
//			listener.getLogger().println("\n\nOpenShiftBuilder logger for pod " + pod.getName() + " not available");
//			bldState = pod.getStatus();
//		}
	}
	
	default String dumpLogs(String bldId, TaskListener listener, Map<String,String> overrides, long wait, boolean chatty) {
    	// our lower level openshift-restclient-java usage here is not agreeable with the TrustManager maintained there,
    	// so we set up our own trust manager like we used to do in order to verify the server cert
    	Auth.createLocalTrustStore(getAuth(), getApiURL(overrides));
		// create stream and copy bytes
    	URL url = null;
    	try {
			url = new URL(getApiURL(overrides) + "/oapi/v1/namespaces/"+getNamespace(overrides)+"/builds/" + bldId + "/log?follow=true");
		} catch (MalformedURLException e1) {
			e1.printStackTrace(listener.getLogger());
		}
    	
    	if (chatty)
    		listener.getLogger().println(" dump logs URL " + url.toString());
    	
		UrlConnectionHttpClient urlClient = new UrlConnectionHttpClient(
				null, "application/json", null, getAuth(), null, null);
		urlClient.setAuthorizationStrategy(getToken());
		String response = null;
		try {
			response = urlClient.get(url, (int) wait);
		} catch (SocketTimeoutException e1) {
			if (chatty)
				e1.printStackTrace(listener.getLogger());
		} catch (HttpClientException e1) {
			if (chatty)
				e1.printStackTrace(listener.getLogger());
		}
		return response;
		
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
	
	default boolean coreLogic(Launcher launcher, TaskListener listener, Map<String,String> overrides) {
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
    				annotateJobInfoToResource(client, listener, chatty, overrides, bld);
    				
    				String bldId = bld.getName();
    				if (!checkDeps)
    					listener.getLogger().println(String.format(MessageConstants.WAITING_ON_BUILD, bldId));
    				else
    					listener.getLogger().println(String.format(MessageConstants.WAITING_ON_BUILD_PLUS_DEPLOY, bldId));    				
    				
    				boolean foundPod = false;
    				startTime = System.currentTimeMillis();
					
					long wait = Long.parseLong(getWaitTime(overrides));
					
					if (chatty)
						listener.getLogger().println("\n OpenShiftBuilder looking for build " + bldId);
    				
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
        						
        							String logs = waitOnBuild(client, startTime, bldId, listener, overrides, wait, follow, chatty);
            						
            						if (follow)
            							listener.getLogger().println("\nBuild logs:  \n" + logs);
    								
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
    				
    				return this.verifyBuild(startTime, wait, client, getBldCfg(overrides), bldId, getNamespace(overrides), chatty, listener, DISPLAY_NAME, checkDeps, true, overrides);
    				    				
    			}
        		
        		
        	} else {
		    	listener.getLogger().println(String.format(MessageConstants.EXIT_BUILD_NO_BUILD_CONFIG_OBJ, getBldCfg(overrides)));
        		return false;
        	}
    	} else {
    		return false;
    	}

	}
}
