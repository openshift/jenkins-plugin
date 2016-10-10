package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;
import hudson.Launcher;
import hudson.model.TaskListener;

import java.util.Map;

public interface IOpenShiftDeployer extends ITimedOpenShiftPlugin {

    final static String DISPLAY_NAME = "Trigger OpenShift Deployment";

    default String getDisplayName() {
        return DISPLAY_NAME;
    }

    String getDepCfg();

    default long getGlobalTimeoutConfiguration() {
        return GlobalConfig.getDeployWait();
    }

    default String getDepCfg(Map<String, String> overrides) {
        return getOverride(getDepCfg(), overrides);
    }

    default boolean bumpVersion(IDeploymentConfig dc, IClient client, TaskListener listener, Map<String, String> overrides) {
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

    default boolean coreLogic(Launcher launcher, TaskListener listener, Map<String, String> overrides) {
        boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
        listener.getLogger().println(String.format(MessageConstants.START_DEPLOY_RELATED_PLUGINS, DISPLAY_NAME, getDepCfg(overrides), getNamespace(overrides)));

        // get oc client
        IClient client = this.getClient(listener, DISPLAY_NAME, overrides);

        if (client != null) {
            // do the oc deploy with version bump ... may need to retry
            long currTime = System.currentTimeMillis();
            boolean deployDone = false;
            boolean versionBumped = false;
            String state = null;
            IDeploymentConfig dc = null;
            IReplicationController rc = null;
            long wait = getTimeout(listener, chatty, overrides);
            while (System.currentTimeMillis() < (currTime + wait)) {
                dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, getDepCfg(overrides), getNamespace(overrides));
                if (dc != null) {
                    if (!versionBumped) {
                        // allow some retry in case the dc creation request happened before this step ran
                        versionBumped = bumpVersion(dc, client, listener, overrides);
                    }

                    try {
                        rc = getLatestReplicationController(dc, getNamespace(overrides), client, chatty ? listener : null);
                        if (chatty)
                            listener.getLogger().println("\nOpenShiftDeployer returned rep ctrl " + rc);
                        if (rc != null) {
                            annotateJobInfoToResource(client, listener, chatty, overrides, rc);
                            state = this.getReplicationControllerState(rc);
                            if (this.isDeployFinished(state)) {
                                if (state.equalsIgnoreCase(STATE_FAILED)) {
                                    listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_BAD, DISPLAY_NAME, rc.getName(), state));
                                    return false;
                                }
                                if (state.equalsIgnoreCase(STATE_COMPLETE)) {
                                    deployDone = true;
                                }
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
                    listener.getLogger().println(String.format(MessageConstants.EXIT_DEPLOY_TRIGGER_TIMED_OUT, DISPLAY_NAME, (rc != null ? rc.getName() : "<deployment not found>"), state));
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

}
