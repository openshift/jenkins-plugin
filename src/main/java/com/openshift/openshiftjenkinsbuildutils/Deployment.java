package com.openshift.openshiftjenkinsbuildutils;

import hudson.model.TaskListener;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.jboss.dmr.ModelNode;

import com.openshift.internal.restclient.model.BuildConfig;
import com.openshift.internal.restclient.model.DeploymentConfig;
import com.openshift.internal.restclient.model.ReplicationController;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;

public class Deployment {

	
//	public static ModelNode getDeploymentConfigLatestVersion(DeploymentConfig dcImpl, TaskListener listener) {
//		if (dcImpl != null) {
//			ModelNode dcNode = dcImpl.getNode();
//			if (listener != null) 
//				listener.getLogger().println("\n dc json " + dcNode.asString());
//			ModelNode dcStatus = dcNode.get("status");
//			ModelNode dcLatestVersion = dcStatus.get("latestVersion");
//			if (dcLatestVersion != null) {
//				return dcLatestVersion;
//			}
//		}
//		return null;
//	}
	
	public static String getReplicationControllerState(IReplicationController rc, TaskListener listener) {
		// see github.com/openshift/origin/pkg/deploy/api/types.go, values are New, Pending, Running, Complete, or Failed
		String state = "";
		if (rc != null) {
			state = rc.getAnnotation("openshift.io/deployment.phase");
			if (listener != null) listener.getLogger().println("\n phase json " + state);	
		}
		return state;
	}
	
//	public static void updateReplicationControllerAnnotiation(IReplicationController rc, TaskListener listener, String annotation, String value) {
//		if (rc != null) {
//			rc.setAnnotation(annotation, value);
//		}
//	}
	
	public static String pullImageHexID(IDeploymentConfig dc, String imageTag, TaskListener listener, boolean chatty) {
		return dc.getImageHexIDForImageTag(imageTag);
//		ModelNode dcNode = ((DeploymentConfig)dc).getNode();
//		if (chatty) listener.getLogger().println("\n  triggers " + dcNode.get("spec").get("triggers"));
//		List<ModelNode> triggers = dcNode.get("spec").get("triggers").asList();
//		if (triggers == null || triggers.size() == 0) {
//			listener.getLogger().println("\n\n no triggers in the DC");
//			return null;
//		}
//		for (ModelNode trigger : triggers) {
//			if (trigger.get("type").asString().equalsIgnoreCase("ImageChange")) {
//				String tag = null;
//				try {
//					tag = trigger.get("imageChangeParams").get("from").get("name").asString();
//					if (chatty) listener.getLogger().println("\n tag " + tag);
//				} catch (Throwable t) {
//					if (chatty)
//						t.printStackTrace(listener.getLogger());
//				}
//				if (chatty) listener.getLogger().println("\n comparing image tag " + imageTag + " with tag " + tag);
//				if (imageTag.equals(tag)) {
//					if (chatty) listener.getLogger().println("\n tags match returning hex id " + trigger.get("imageChangeParams").get("lastTriggeredImage").asString());
//					return trigger.get("imageChangeParams").get("lastTriggeredImage").asString();
//				}
//			}
//		}
//		return null;
	}
	
	public static boolean doesDCTriggerOnImageTag(IDeploymentConfig dc, String imageTag, boolean chatty, TaskListener listener) {
		if (dc == null || imageTag == null)
			throw new RuntimeException("needed param null for doesDCTriggerOnImageTag");
//		ModelNode dcNode = ((DeploymentConfig)dc).getNode();
		if (listener == null)
			chatty = false;
		
		long currTime = System.currentTimeMillis();
//		List<ModelNode> causes = null;
		while (System.currentTimeMillis() - 30000 <= currTime) {
//			try {
//				causes = dcNode.get("status").get("details").get("causes").asList();
//				if (causes != null && causes.size() > 0) {
//					break;
//				}
//			} catch (Throwable t) {
//				if (chatty)
//					t.printStackTrace(listener.getLogger());
//			}
//			if (causes == null || causes.size() == 0) {
			if (!dc.haveTriggersFired()) {
			
				if (chatty)
					listener.getLogger().println("\n\n could not find a cause for the deployment");
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
				}
			} else {
				break;
			}
		}
		
//		if (causes == null || causes.size() == 0) {
//			if (chatty)
//				listener.getLogger().println("\n no trigger causes detected ");
//			return false;
//		}
		
		return dc.didImageTrigger(imageTag);
		
//		for (ModelNode cause : causes) {
//			String type = cause.get("type").asString();
//			if (chatty) listener.getLogger().println("\n checking cause type " + type);
//			if (type.equalsIgnoreCase("ImageChange")) {
//				try {
//					String triggerName = cause.get("imageTrigger").get("from").get("name").asString();
//					if (chatty) listener.getLogger().println("\n cause image tag " + triggerName);
//					if (triggerName.contains("/")) {
//						StringTokenizer st = new StringTokenizer(imageTag, "/");
//						String tmp = null;
//						while (st.hasMoreTokens()) {
//							tmp = st.nextToken();
//						}
//						if (chatty) listener.getLogger().println("\n comparing " + imageTag + " with " + tmp);
//						if (imageTag.equals(tmp)) {
//							if (chatty) listener.getLogger().println("\n found match for imageTag " + imageTag);
//							return true;
//						}
//					}
//				} catch (Throwable t) {
//					if (chatty)
//						t.printStackTrace(listener.getLogger());
//				}
//			}
//		}
//		return false;
	}
	
	public static boolean didImageChangeFromPreviousVersion(IClient client, int latestVersion, boolean chatty, TaskListener listener, 
			String depCfg, String namespace, String latestImageHexID, String imageTag) {
		// now get previous RC, fetch image Hex ID, and compare
		int previousVersion = latestVersion -1;
		if (previousVersion < 1) {
			if (chatty) listener.getLogger().println("\n first version skip image compare");
			return true;
		}
		ReplicationController prevRC = null;
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
		DeploymentConfig dc = new DeploymentConfig(dcNode, client, null);
		String previousImageHexID = pullImageHexID(dc, imageTag, listener, chatty);
		
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
	
	public static boolean didAllImagesChangeIfNeeded(String buildConfig, TaskListener listener, boolean chatty, IClient client, String namespace) {
		if (chatty)
			listener.getLogger().println("\n checking if the build config " + buildConfig + " got the image changes it needed");
		BuildConfig bc = client.get(ResourceKind.BUILD_CONFIG, buildConfig, namespace);
		if (bc == null) {
			if (chatty)
				listener.getLogger().println("\n bc null for " +buildConfig);
		}
		ModelNode bcJson = bc.getNode();
		String imageTag = null;
		try {
			imageTag = bc.getOutputRepositoryName();//bcJson.get("spec").get("output").get("to").get("name").asString();
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
			if (doesDCTriggerOnImageTag(dc, imageTag, chatty, listener)) {
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
			String latestImageHexID = pullImageHexID(dc, imageTag, listener, chatty);
			
			if (latestImageHexID == null) {
				if (chatty)
					listener.getLogger().println("\n dc " + dc.getName() + " did not have a reference to " + imageTag);
				continue;
			}
			
			if (didImageChangeFromPreviousVersion(client, dc.getLatestVersionNumber()/*getDeploymentConfigLatestVersion((DeploymentConfig)dc, listener).asInt()*/, 
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
//		List<ModelNode> causes = null;
//		try {
//			causes = dcNode.get("status").get("details").get("causes").asList();
//		} catch (Throwable t) {
//			if (chatty)
//				t.printStackTrace(listener.getLogger());
//		}
//		if (causes == null || causes.size() == 0) {
//			listener.getLogger().println("\n\n could not find a cause for the deployment");
//			return false;
//		}
		
		if (!dc.haveTriggersFired()) {
			listener.getLogger().println("\n\n could not find a cause for the deployment");
			return false;
		}
		
		String imageTag = dc.getImageTagForTriggeredDeployment();//null;
//		for (ModelNode cause : causes) {
//			String type = cause.get("type").asString();
//			if (type.equalsIgnoreCase("ImageChange")) {
//				try {
//					imageTag = cause.get("imageTrigger").get("from").get("name").asString();
//					if (imageTag.contains("/")) {
//						StringTokenizer st = new StringTokenizer(imageTag, "/");
//						String tmp = null;
//						while (st.hasMoreTokens()) {
//							tmp = st.nextToken();
//						}
//						imageTag = tmp;
//						if (chatty) listener.getLogger().println("\n image tag from trigger " + imageTag);
//						break;
//					}
//				} catch (Throwable t) {
//					if (chatty)
//						t.printStackTrace(listener.getLogger());
//				}
//			}
//		}
		
		if (imageTag == null) {
			if (chatty)
				listener.getLogger().println("\n deployment did not stem from an image change");
			return true;
		}
		
		// 3) now get hexadecimal image id for latest RC
	    return didImageChangeFromPreviousVersion(client, latestVersion, chatty, listener, depCfg, namespace, latestImageHexID, imageTag);
	}
	
}
