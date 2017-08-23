package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.jenkins.plugins.pipeline.OpenShiftDeployCanceller;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.capability.CapabilityVisitor;
import com.openshift.restclient.capability.resources.IDeploymentTriggerable;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;

import hudson.Launcher;
import hudson.model.TaskListener;

import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    default boolean coreLogic(Launcher launcher, TaskListener listener,
            Map<String, String> overrides) throws InterruptedException {
        boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
        listener.getLogger().println(
                String.format(MessageConstants.START_DEPLOY_RELATED_PLUGINS,
                        DISPLAY_NAME, getDepCfg(overrides),
                        getNamespace(overrides)));

        // get oc client
        IClient client = this.getClient(listener, DISPLAY_NAME, overrides);

        if (client != null) {
            long currTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
            boolean deployDone = false;
            boolean versionBumped = false;
            String state = null;
            IDeploymentConfig dc = null;
            IDeploymentConfig newdc = null;
            IReplicationController rc = null;
            long wait = getTimeout(listener, chatty, overrides);
            while (TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) < (currTime + wait)) {
                if (dc == null)
                    dc = client.get(ResourceKind.DEPLOYMENT_CONFIG,
                            getDepCfg(overrides), getNamespace(overrides));
                if (dc != null) {
                    if (!versionBumped) {
                        // allow some retry in case the dc creation request
                        // happened before this step ran
                        try {
                            final String dcName = dc.getName();
                            newdc = dc
                                    .accept(new CapabilityVisitor<IDeploymentTriggerable, IDeploymentConfig>() {

                                        @Override
                                        public IDeploymentConfig visit(
                                                IDeploymentTriggerable triggerable) {
                                            triggerable.setForce(true);
                                            triggerable.setLatest(true);
                                            triggerable.setResourceName(dcName);
                                            return triggerable.trigger();
                                        }

                                    }, null);
                            if (chatty)
                                listener.getLogger()
                                        .println(
                                                "\nOpenShiftDeployer latest version now "
                                                        + newdc.getLatestVersionNumber()
                                                        + " and was "
                                                        + dc.getLatestVersionNumber());
                            versionBumped = newdc.getLatestVersionNumber() > dc
                                    .getLatestVersionNumber();
                        } catch (Throwable t) {
                            if (chatty)
                                t.printStackTrace(listener.getLogger());
                        }
                    }

                    try {
                        rc = getLatestReplicationController(newdc,
                                getNamespace(overrides), client,
                                chatty ? listener : null);
                        if (chatty)
                            listener.getLogger().println(
                                    "\nOpenShiftDeployer returned rep ctrl "
                                            + rc);
                        if (rc != null) {
                            annotateJobInfoToResource(client, listener, chatty,
                                    overrides, rc);
                            state = this.getReplicationControllerState(rc);
                            if (this.isDeployFinished(state)) {
                                if (state.equalsIgnoreCase(STATE_FAILED)) {
                                    listener.getLogger()
                                            .println(
                                                    String.format(
                                                            MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_BAD,
                                                            DISPLAY_NAME,
                                                            rc.getName(), state));
                                    return false;
                                }
                                if (state.equalsIgnoreCase(STATE_COMPLETE)) {
                                    deployDone = true;
                                }
                            } else {
                                if (chatty)
                                    listener.getLogger().println(
                                            "\nOpenShiftDeploy current phase "
                                                    + state);
                            }
                        } else {
                            if (chatty)
                                listener.getLogger()
                                        .println(
                                                "\nOpenShiftDeploy no rc for latest version yet");
                        }
                    } catch (Throwable t) {
                        if (chatty)
                            t.printStackTrace(listener.getLogger());
                    }

                    if (deployDone) {
                        break;
                    } else {
                        if (chatty)
                            listener.getLogger()
                                    .println(
                                            "\nOpenShiftDeployer wait 10 seconds, then try oc deploy again");
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException e) {
                            // need to throw as this indicates the step as been
                            // cancelled
                            // also attempt to cancel deploy on openshift side
                            OpenShiftDeployCanceller canceller = new OpenShiftDeployCanceller(
                                    getApiURL(overrides), getDepCfg(overrides),
                                    getNamespace(overrides),
                                    getAuthToken(overrides),
                                    getVerbose(overrides));
                            canceller.setAuth(getAuth());
                            canceller.coreLogic(null, listener, overrides);
                            throw e;
                        }
                    }

                }
            }

            if (!deployDone) {
                if (newdc != null)
                    listener.getLogger()
                            .println(
                                    String.format(
                                            MessageConstants.EXIT_DEPLOY_TRIGGER_TIMED_OUT,
                                            DISPLAY_NAME,
                                            (rc != null ? rc.getName()
                                                    : "<deployment not found>"),
                                            state));
                else
                    listener.getLogger()
                            .println(
                                    String.format(
                                            MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_NO_CFG,
                                            DISPLAY_NAME, getDepCfg(overrides)));
                return false;
            }

            listener.getLogger()
                    .println(
                            String.format(
                                    MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_GOOD_REPLICAS_IGNORED,
                                    DISPLAY_NAME, rc.getName()));
            return true;

        } else {
            return false;
        }
    }

}
