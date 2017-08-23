package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.api.capabilities.IScalable;
import com.openshift.restclient.apis.autoscaling.models.IScale;
import com.openshift.restclient.capability.CapabilityVisitor;
import com.openshift.restclient.model.IDeploymentConfig;
import com.openshift.restclient.model.IReplicationController;

import hudson.Launcher;
import hudson.model.TaskListener;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public interface IOpenShiftScaler extends ITimedOpenShiftPlugin {
    String DISPLAY_NAME = "Scale OpenShift Deployment";

    default String getDisplayName() {
        return DISPLAY_NAME;
    }

    String getDepCfg();

    String getReplicaCount();

    String getVerifyReplicaCount();

    default long getGlobalTimeoutConfiguration() {
        return GlobalConfig.getScalerWait();
    }

    default String getDepCfg(Map<String, String> overrides) {
        return getOverride(getDepCfg(), overrides);
    }

    default String getReplicaCount(Map<String, String> overrides) {
        return getOverride(getReplicaCount(), overrides);
    }

    default String getVerifyReplicaCount(Map<String, String> overrides) {
        return getOverride(getVerifyReplicaCount(), overrides);
    }

    default boolean coreLogic(Launcher launcher, TaskListener listener,
            Map<String, String> overrides) throws InterruptedException {
        boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
        boolean checkCount = Boolean
                .parseBoolean(getVerifyReplicaCount(overrides));
        listener.getLogger().println(
                String.format(MessageConstants.START_DEPLOY_RELATED_PLUGINS,
                        DISPLAY_NAME, getDepCfg(overrides),
                        getNamespace(overrides)));

        // get oc client
        IClient client = this.getClient(listener, DISPLAY_NAME, overrides);

        if (client != null) {
            IReplicationController rc = null;
            IDeploymentConfig dc = null;
            long currTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
            // in testing with the jenkins-ci sample, the initial deploy after
            // a build is kinda slow ... gotta wait more than one minute

            if (!checkCount)
                listener.getLogger().println(
                        String.format(MessageConstants.SCALING,
                                getReplicaCount(overrides)));
            else
                listener.getLogger().println(
                        String.format(
                                MessageConstants.SCALING_PLUS_REPLICA_CHECK,
                                getReplicaCount(overrides)));

            // do the oc scale ... may need to retry
            boolean scaleDone = false;
            long wait = getTimeout(listener, chatty, overrides);
            while (TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) < (currTime + wait)) {
                dc = client.get(ResourceKind.DEPLOYMENT_CONFIG,
                        getDepCfg(overrides), getNamespace(overrides));
                if (dc == null) {
                    listener.getLogger()
                            .println(
                                    String.format(
                                            MessageConstants.EXIT_DEPLOY_RELATED_PLUGINS_NO_CFG,
                                            DISPLAY_NAME, getDepCfg(overrides)));
                    return false;
                }

                if (dc.getLatestVersionNumber() > 0) {
                    rc = getLatestReplicationController(dc,
                            getNamespace(overrides), client, chatty ? listener
                                    : null);

                    final int count = Integer
                            .decode(getReplicaCount(overrides));
                    scaleDone = rc.getCurrentReplicaCount() == count;

                    if (chatty)
                        listener.getLogger().println(
                                "\nOpenShiftScaler setting desired replica count of "
                                        + getReplicaCount(overrides) + " on "
                                        + rc + " scaleDone " + scaleDone);

                    if (!scaleDone) {
                        IScale result = dc.accept(
                                new CapabilityVisitor<IScalable, IScale>() {

                                    @Override
                                    public IScale visit(IScalable capability) {
                                        return capability.scaleTo(count);
                                    }
                                }, null);
                        if (chatty)
                            listener.getLogger().println(
                                    "\nOpenShiftScaler scale result " + result);
                        rc = getLatestReplicationController(dc,
                                getNamespace(overrides), client,
                                chatty ? listener : null);
                        scaleDone = this
                                .isReplicationControllerScaledAppropriately(rc,
                                        checkCount, count);
                    }
                } else {
                    // TODO if not found, and we are scaling down to zero, don't
                    // consider an error - this may be safety
                    // measure to scale down if exits ... perhaps we make this
                    // behavior configurable over time, but for now.
                    // we refrain from adding yet 1 more config option
                    if (getReplicaCount(overrides).equals("0")) {
                        listener.getLogger().println(
                                String.format(
                                        MessageConstants.EXIT_SCALING_NOOP,
                                        getDepCfg(overrides)));
                        return true;
                    }
                }

                if (scaleDone) {
                    break;
                } else {
                    if (chatty)
                        listener.getLogger()
                                .println(
                                        "\nOpenShiftScaler will wait 10 seconds, then try to scale again");
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        // need to throw as this indicates the step as been
                        // cancelled
                        throw e;
                    }
                }
            }

            if (!scaleDone) {
                if (!checkCount) {
                    listener.getLogger().println(
                            String.format(MessageConstants.EXIT_SCALING_BAD,
                                    getApiURL(overrides)));
                } else {
                    listener.getLogger().println(
                            String.format(
                                    MessageConstants.EXIT_SCALING_TIMED_OUT,
                                    (rc != null ? rc.getName()
                                            : "<deployment not found>"),
                                    getReplicaCount(overrides)));
                }
                return false;
            }

            if (!checkCount)
                listener.getLogger().println(
                        String.format(MessageConstants.EXIT_SCALING_GOOD,
                                rc.getName()));
            else
                listener.getLogger()
                        .println(
                                String.format(
                                        MessageConstants.EXIT_SCALING_GOOD_REPLICAS_GOOD,
                                        rc.getName(),
                                        getReplicaCount(overrides)));
            return true;

        } else {
            return false;
        }
    }
}
