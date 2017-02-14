package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.jenkins.plugins.pipeline.NameValuePair;
import com.openshift.jenkins.plugins.pipeline.OpenShiftBuildCanceller;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.capability.CapabilityVisitor;
import com.openshift.restclient.capability.IStoppable;
import com.openshift.restclient.capability.resources.IBuildTriggerable;
import com.openshift.restclient.capability.resources.IPodLogRetrievalAsync;
import com.openshift.restclient.model.IBuild;
import com.openshift.restclient.model.IBuildConfig;
import com.openshift.restclient.model.IPod;

import hudson.Launcher;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public interface IOpenShiftBuilder extends ITimedOpenShiftPlugin {

    String DISPLAY_NAME = "Trigger OpenShift Build";

    default String getDisplayName() {
        return DISPLAY_NAME;
    }

    String getCommitID();

    String getBuildName();

    String getShowBuildLogs();

    String getBldCfg();

    String getCheckForTriggeredDeployments();

    List<NameValuePair> getEnv();

    default String getCommitID(Map<String, String> overrides) {
        return getOverride(getCommitID(), overrides);
    }

    default String getBuildName(Map<String, String> overrides) {
        return getOverride(getBuildName(), overrides);
    }

    default String getShowBuildLogs(Map<String, String> overrides) {
        return getOverride(getShowBuildLogs(), overrides);
    }

    default String getBldCfg(Map<String, String> overrides) {
        return getOverride(getBldCfg(), overrides);
    }

    default String getCheckForTriggeredDeployments(Map<String, String> overrides) {
        return getOverride(getCheckForTriggeredDeployments(), overrides);
    }

    default long getGlobalTimeoutConfiguration() {
        return GlobalConfig.getBuildWait();
    }

    default void applyEnvVars(IBuildTriggerable bt, Map<String, String> overrides, TaskListener listener, boolean chatty) {
        if (getEnv() != null) {
            for (NameValuePair p : getEnv()) {
                String name = p.getName().trim();
                if (!name.isEmpty()) {
                    if (chatty)
                        listener.getLogger().println("applyEnvVars env name " + name + " raw value " + p.getValue() + " override value " + getOverride(p.getValue(), overrides));
                    bt.setEnvironmentVariable(p.getName(), getOverride(p.getValue(), overrides));
                }
            }
        }
    }

    default IBuild startBuild(IBuildConfig bc, IBuild prevBld, Map<String, String> overrides, boolean chatty, TaskListener listener, IClient client) {
        IBuild bld = null;
        if (bc != null) {
            if (getCommitID(overrides) != null && getCommitID(overrides).length() > 0) {
                final String cid = getCommitID(overrides);
                bld = bc.accept(new CapabilityVisitor<IBuildTriggerable, IBuild>() {
                    public IBuild visit(IBuildTriggerable triggerable) {
                        triggerable.setCommitId(cid);
                        triggerable.addBuildCause("Jenkins job: " + overrides.get(IOpenShiftPlugin.BUILD_URL_ENV_KEY));
                        applyEnvVars(triggerable, overrides, listener, chatty);
                        return triggerable.trigger();
                    }
                }, null);
            } else {
                bld = bc.accept(new CapabilityVisitor<IBuildTriggerable, IBuild>() {
                    public IBuild visit(IBuildTriggerable triggerable) {
                        triggerable.addBuildCause("Jenkins job: " + overrides.get(IOpenShiftPlugin.BUILD_URL_ENV_KEY));
                        applyEnvVars(triggerable, overrides, listener, chatty);
                        return triggerable.trigger();
                    }
                }, null);
            }
        } else if (prevBld != null) {
            bld = prevBld.accept(new CapabilityVisitor<IBuildTriggerable, IBuild>() {
                public IBuild visit(IBuildTriggerable triggerable) {
                    triggerable.addBuildCause("Jenkins job: " + overrides.get(IOpenShiftPlugin.BUILD_URL_ENV_KEY));
                    applyEnvVars(triggerable, overrides, listener, chatty);
                    return triggerable.trigger();
                }
            }, null);
        }
        return bld;
    }
    
    default IStoppable getBuildPodLogs(IClient client, String bldId, Map<String, String> overrides, boolean chatty, TaskListener listener, AtomicBoolean needToFollow) {
        IStoppable stop = null;
        List<IPod> pods = client.list(ResourceKind.POD, getNamespace(overrides));
        for (IPod pod : pods) {
            if (chatty)
                listener.getLogger().println("\nOpenShiftBuilder found pod " + pod.getName());

            // build pod starts with build id
            if (pod.getName().startsWith(bldId)) {
                if (chatty)
                    listener.getLogger().println("\nOpenShiftBuilder going with build pod " + pod);
                final String container = pod.getContainers().iterator().next().getName();

                stop = pod.accept(new CapabilityVisitor<IPodLogRetrievalAsync, IStoppable>() {
                    @Override
                    public IStoppable visit(IPodLogRetrievalAsync capability) {
                        return capability.start(new IPodLogRetrievalAsync.IPodLogListener() {
                            @Override
                            public void onOpen() {
                            }

                            @Override
                            public void onMessage(String message) {
                                listener.getLogger().print(message);
                            }

                            @Override
                            public void onClose(int code, String reason) {
                            }

                            @Override
                            public void onFailure(IOException e) {
                                // If follow fails, try to restart it on the next loop.
                                needToFollow.compareAndSet(false, true);
                            }
                        }, new IPodLogRetrievalAsync.Options()
                                .follow()
                                .container(container));
                    }
                }, null);
                break;
            }
        }
        return stop;
    }

    default void waitOnBuild(IClient client, long startTime, String bldId, TaskListener listener, Map<String, String> overrides, long wait, boolean follow, boolean chatty) throws InterruptedException {
        IBuild bld = null;
        String bldState = null;

        // get internal OS Java REST Client error if access pod logs while bld is in Pending state
        // instead of Running, Complete, or Failed

        IStoppable stop = null;

        final AtomicBoolean needToFollow = new AtomicBoolean(follow);

        while (TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) < (startTime + wait)) {
            bld = client.get(ResourceKind.BUILD, bldId, getNamespace(overrides));
            bldState = bld.getStatus();
            if (Boolean.parseBoolean(getVerbose(overrides)))
                listener.getLogger().println("\nOpenShiftBuilder bld state:  " + bldState);

            if (isBuildRunning(bldState) && needToFollow.compareAndSet(true, false)) {
                stop = this.getBuildPodLogs(client, bldId, overrides, chatty, listener, needToFollow);
            }

            if (isBuildFinished(bldState)) {
                if (follow && stop == null)
                    stop = this.getBuildPodLogs(client, bldId, overrides, chatty, listener, needToFollow);
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // need to throw as this indicates the step as been cancelled
                // also attempt to cancel build on openshift side
                OpenShiftBuildCanceller canceller = new OpenShiftBuildCanceller(getApiURL(overrides), getNamespace(overrides), getAuthToken(overrides), getVerbose(overrides), getBldCfg(overrides));
                canceller.setAuth(getAuth());
                canceller.coreLogic(null, listener, overrides);
                throw e;
            }
        }
        if (stop != null)
            stop.stop();

    }

    default boolean coreLogic(Launcher launcher, TaskListener listener, Map<String, String> overrides) throws InterruptedException {
        boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
        boolean checkDeps = Boolean.parseBoolean(getCheckForTriggeredDeployments(overrides));
        listener.getLogger().println(String.format(MessageConstants.START_BUILD_RELATED_PLUGINS, DISPLAY_NAME, getBldCfg(overrides), getNamespace(overrides)));

        boolean follow = Boolean.parseBoolean(getShowBuildLogs(overrides));
        if (chatty)
            listener.getLogger().println("\nOpenShiftBuilder logger follow " + follow);

        // get oc client
        IClient client = this.getClient(listener, DISPLAY_NAME, overrides);

        if (client != null) {
            long startTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
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
                
                // don't follow if a jenkinsfile strategy
                //TODO until we get the restclient updated, getBuildStrategy returns null if jenkins strategy, non-null for the traditional 3
                boolean jenkinsfileBC = false;
                if (bc != null)
                    jenkinsfileBC = bc.getBuildStrategy() == null;
                else {
                    IBuildConfig tmpBC = client.get(ResourceKind.BUILD_CONFIG, getBldCfg(overrides), getNamespace(overrides));
                    if (tmpBC != null)
                        jenkinsfileBC = tmpBC.getBuildStrategy() == null;
                }
                if (chatty)
                    listener.getLogger().println("\nOpenShiftBuilder bc strategy == jenkinsfile " + jenkinsfileBC);
                
                if (follow)
                    follow = !jenkinsfileBC;

                // Trigger / start build
                IBuild bld = this.startBuild(bc, prevBld, overrides, chatty, listener, client);


                if (bld == null) {
                    listener.getLogger().println(MessageConstants.EXIT_BUILD_NO_BUILD_OBJ);
                    return false;
                } else {
                    annotateJobInfoToResource(client, listener, chatty, overrides, bld);

                    String bldId = bld.getName();
                    if (!checkDeps)
                        listener.getLogger().println(String.format(MessageConstants.WAITING_ON_BUILD, bldId));
                    else
                        listener.getLogger().println(String.format(MessageConstants.WAITING_ON_BUILD_PLUS_DEPLOY, bldId));

                    startTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());

                    long wait = getTimeout(listener, chatty, overrides);

                    if (chatty)
                        listener.getLogger().println("\n OpenShiftBuilder looking for build " + bldId);


                    waitOnBuild(client, startTime, bldId, listener, overrides, wait, follow, chatty);

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
