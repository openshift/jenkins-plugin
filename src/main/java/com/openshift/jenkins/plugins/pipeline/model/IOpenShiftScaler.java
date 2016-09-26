package com.openshift.jenkins.plugins.pipeline.model;

import hudson.Launcher;
import hudson.model.TaskListener;

import java.util.Map;

import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;

public interface IOpenShiftScaler extends IOpenShiftTimedPlugin {
	final static String DISPLAY_NAME = "Scale OpenShift Deployment";

	default String getDisplayName() {
		return DISPLAY_NAME;
	}
	
	public String getDepCfg();
	
	public String getReplicaCount();
		
	public String getVerifyReplicaCount();
	
	default String getDepCfg(Map<String,String> overrides) {
		return getOverride(getDepCfg(), overrides);
	}
	
	default String getReplicaCount(Map<String,String> overrides) {
		return getOverride(getReplicaCount(), overrides);
	}
	
	default String getVerifyReplicaCount(Map<String,String> overrides) {
		return getOverride(getVerifyReplicaCount(), overrides);
	}


	default boolean coreLogic(Launcher launcher, TaskListener listener, Map<String,String> overrides) {
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
				listener.getLogger().println("\nOpenShiftScaler wait " + convertUnitNotation(getWaitTime(overrides)));

			if (!checkCount)
				listener.getLogger().println(String.format(MessageConstants.SCALING, getReplicaCount(overrides)));
			else
				listener.getLogger().println(String.format(MessageConstants.SCALING_PLUS_REPLICA_CHECK, getReplicaCount(overrides)));

			// do the oc scale ... may need to retry
			boolean scaleDone = false;
			while (System.currentTimeMillis() < (currTime + convertUnitNotation(getWaitTime(overrides)))) {
				dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, getDepCfg(overrides), getNamespace(overrides));
				if (dc == null) {
					listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_NO_CFG, DISPLAY_NAME, getDepCfg(overrides)));
					return false;
				}

				if (dc.getLatestVersionNumber() > 0)
					rc = getLatestReplicationController(dc, getNamespace(overrides), client, chatty ? listener : null);

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
							listener.getLogger().println("\nOpenShiftScaler rc returned from update current replica count " + rc.getCurrentReplicaCount() + " desired count " + rc.getDesiredReplicaCount() + " and state " + getReplicationControllerState(rc));
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
					listener.getLogger().println(String.format(MessageConstants.EXIT_SCALING_TIMED_OUT, (rc != null ? rc.getName() : "<deployment not found>"), getReplicaCount(overrides)));
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
}
