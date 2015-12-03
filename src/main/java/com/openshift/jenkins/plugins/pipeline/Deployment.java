package com.openshift.jenkins.plugins.pipeline;

import hudson.model.TaskListener;

import java.util.ArrayList;
import java.util.List;

import org.jboss.dmr.ModelNode;

import com.openshift.internal.restclient.model.DeploymentConfig;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IBuildConfig;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;

public class Deployment {

	
	public static boolean doesDCTriggerOnImageTag(IDeploymentConfig dc, String imageTag, boolean chatty, TaskListener listener, long wait) {
		if (dc == null || imageTag == null)
			throw new RuntimeException("needed param null for doesDCTriggerOnImageTag");
		if (listener == null)
			chatty = false;
		
		long currTime = System.currentTimeMillis();
		while (System.currentTimeMillis() - (wait / 3) <= currTime) {
			if (!dc.haveTriggersFired()) {
			
				if (chatty)
					listener.getLogger().println("\n could not find a cause for the deployment");
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
				}
			} else {
				if (chatty)
					listener.getLogger().println("\n trigger fired for deployment");
				break;
			}
		}
		
		if (chatty) {
			listener.getLogger().println("\n done checking dc " + dc.getName() );
		}
		
		return dc.didImageTrigger(imageTag);
		
	}
	
	public static boolean didImageChangeFromPreviousVersion(IClient client, int latestVersion, boolean chatty, TaskListener listener, 
			String depCfg, String namespace, String latestImageHexID, String imageTag) {
		// now get previous RC, fetch image Hex ID, and compare
		int previousVersion = latestVersion -1;
		if (previousVersion < 1) {
			if (chatty) listener.getLogger().println("\n first version skip image compare");
			return true;
		}
		IReplicationController prevRC = null;
		try {
			prevRC = client.get(ResourceKind.REPLICATION_CONTROLLER, depCfg + "-" + previousVersion, namespace);
		} catch (Throwable t) {
			if (chatty)
				t.printStackTrace(listener.getLogger());
		}
		
		if (prevRC == null) {
			listener.getLogger().println("\n\n could not obtain previous replication controller");
			return false;
		}
		
		// get the dc again from the rc vs. passing the dc in as a form of cross reference verification
		String dcJson = prevRC.getAnnotation("openshift.io/encoded-deployment-config");
		if (dcJson == null || dcJson.length() == 0) {
			listener.getLogger().println("\n\n assoicated DeploymentConfig for previous ReplicationController missing");
			return false;
		}
		ModelNode dcNode = ModelNode.fromJSONString(dcJson);
		IDeploymentConfig dc = new DeploymentConfig(dcNode, client, null);
		String previousImageHexID = dc.getImageHexIDForImageNameAndTag(imageTag);
		
		if (previousImageHexID == null || previousImageHexID.length() == 0) {
			// don't count ill obtained prev image id as successful image id change
			listener.getLogger().println("\n\n could not obtain hex image ID for previous deployment");
			return false;
		}
		
		if (latestImageHexID.equals(previousImageHexID)) {
			if (chatty) listener.getLogger().println("\n images still the same " + latestImageHexID);
			return false;
		} else {
			if (chatty) listener.getLogger().println("\n image did change, new image " + latestImageHexID + " old image " + previousImageHexID);
			return true;
		}
	}
	
	public static boolean didAllImagesChangeIfNeeded(String buildConfig, TaskListener listener, boolean chatty, IClient client, String namespace, long wait) {
		if (chatty)
			listener.getLogger().println("\n checking if the build config " + buildConfig + " got the image changes it needed");
		IBuildConfig bc = client.get(ResourceKind.BUILD_CONFIG, buildConfig, namespace);
		if (bc == null) {
			if (chatty)
				listener.getLogger().println("\n bc null for " +buildConfig);
		}

		String imageTag = null;
		try {
			imageTag = bc.getOutputRepositoryName();
			if (chatty) listener.getLogger().println("\n\n build config output image tag " + imageTag);
		} catch (Throwable t) {
		}
		
		if (imageTag == null) {
			if (chatty)
				listener.getLogger().println("\n\n build config " + bc.getName() + " does not output an image");
			return true;
		}
		
		// find deployment configs with image change triggers 
		List<IDeploymentConfig> allDC = client.list(ResourceKind.DEPLOYMENT_CONFIG, namespace);
		if (allDC == null || allDC.size() == 0) {
			if (chatty)
				listener.getLogger().println("\n\n no deployment configs present");
			return true;
		}
		List<IDeploymentConfig> dcsToCheck = new ArrayList<IDeploymentConfig>();
		for (IDeploymentConfig dc : allDC) {
			if (chatty) listener.getLogger().println("\n checking triggers on dc " + dc.getName());
			if (doesDCTriggerOnImageTag(dc, imageTag, chatty, listener, wait)) {
				if (chatty) listener.getLogger().println("\n adding dc to check " + dc.getName());
				dcsToCheck.add(dc);
			}
		}
		
		// cycle through the DCs triggering, comparing latest and previous RC, see if image changed
		for (IDeploymentConfig dc : dcsToCheck) {
			if (chatty) {
				ModelNode dcNode = ((DeploymentConfig)dc).getNode();
				listener.getLogger().println("\n looking at image ids for " + dc.getName() + " with json " + dcNode.toJSONString(false));
			}
			String latestImageHexID = dc.getImageHexIDForImageNameAndTag(imageTag);
			
			if (latestImageHexID == null) {
				if (chatty)
					listener.getLogger().println("\n dc " + dc.getName() + " did not have a reference to " + imageTag);
				continue;
			}
			
			if (didImageChangeFromPreviousVersion(client, dc.getLatestVersionNumber(), 
					chatty, listener, dc.getName(), namespace, latestImageHexID, imageTag)) {
				if (chatty)
					listener.getLogger().println("\n dc " + dc.getName() + " did trigger based on image change as expected");
			} else {
				if (chatty)
					listener.getLogger().println("\n dc " + dc.getName() + " did not trigger based on image change as expected");
				return false;
			}
		}
		
		return true;
	}

	public static boolean didImageChangeIfNeeded(IReplicationController rc, TaskListener listener, boolean chatty, int latestVersion,
			String depCfg, IClient client, String namespace) {
		String latestImageHexID = null;
		
		// 1) pull the dc from the rc annotation
		// we are explicitly constructing the DC from the RC JSON as a form of cross verification
		// (vs. doing another IClient lookup)
		String dcJson = rc.getAnnotation("openshift.io/encoded-deployment-config");
		if (dcJson == null || dcJson.length() == 0) {
			listener.getLogger().println("\n\n assoicated DeploymentConfig for ReplicationController missing");
			return false;
		}
		ModelNode dcNode = ModelNode.fromJSONString(dcJson);
		DeploymentConfig dc = new DeploymentConfig(dcNode, client, null);
		
		if (chatty)
			listener.getLogger().println("\n  dep cfg from rep ctrl json " + dcNode.toJSONString(true));
		
		// 2) See if the deployment resulted from an image change 
		if (!dc.haveTriggersFired()) {
			listener.getLogger().println("\n\n could not find a cause for the deployment");
			return false;
		}
		
		String imageTag = dc.getImageNameAndTagForTriggeredDeployment();//null;
		
		if (imageTag == null) {
			if (chatty)
				listener.getLogger().println("\n deployment did not stem from an image change");
			return true;
		}
		
		// 3) now get hexadecimal image id for latest RC
	    return didImageChangeFromPreviousVersion(client, latestVersion, chatty, listener, depCfg, namespace, latestImageHexID, imageTag);
	}
	
}
