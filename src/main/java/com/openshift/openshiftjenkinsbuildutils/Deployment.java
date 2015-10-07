package com.openshift.openshiftjenkinsbuildutils;

import hudson.model.BuildListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.dmr.ModelNode;

import com.openshift.internal.restclient.model.DeploymentConfig;
import com.openshift.internal.restclient.model.ReplicationController;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;

public class Deployment {

	
	public static ModelNode getDeploymentConfigLatestVersion(DeploymentConfig dcImpl, BuildListener listener) {
		if (dcImpl != null) {
			ModelNode dcNode = dcImpl.getNode();
			if (listener != null) 
				listener.getLogger().println("\n dc json " + dcNode.asString());
			ModelNode dcStatus = dcNode.get("status");
			if (listener != null)
				listener.getLogger().println("\n status json " + dcStatus.asString());
			ModelNode dcLatestVersion = dcStatus.get("latestVersion");
			if (listener != null)
				listener.getLogger().println("\n version json " + dcStatus.asString());
			if (dcLatestVersion != null) {
				return dcLatestVersion;
			}
		}
		return null;
	}
	
	public static String getReplicationControllerState(ReplicationController rcImpl, BuildListener listener) {
		String state = "";
		if (rcImpl != null) {
			ModelNode node = rcImpl.getNode();
			if (listener != null) listener.getLogger().println("\n rc json " + node.asString());
			ModelNode metadata = node.get("metadata");
			if (listener != null) listener.getLogger().println("\n meta json " + metadata.asString());
			ModelNode annotations = metadata.get("annotations");
			if (listener != null) listener.getLogger().println("\n annotations json " + annotations.asString());
			ModelNode phase = annotations.get("openshift.io/deployment.phase");
			state = phase.asString();
			if (listener != null) listener.getLogger().println("\n phase json " + state);			
		}
		return state;
	}
}
