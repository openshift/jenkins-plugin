package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IBuild;

import hudson.Launcher;
import hudson.model.TaskListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;

public interface IOpenShiftBuildVerifier extends ITimedOpenShiftPlugin {

    String DISPLAY_NAME = "Verify OpenShift Build";

    default String getDisplayName() {
        return DISPLAY_NAME;
    }

    String getBldCfg();

    String getCheckForTriggeredDeployments();

    default long getGlobalTimeoutConfiguration() {
        return GlobalConfig.getBuildVerifyWait();
    }

    default String getBldCfg(Map<String, String> overrides) {
        return getOverride(getBldCfg(), overrides);
    }

    default String getCheckForTriggeredDeployments(Map<String, String> overrides) {
        return getOverride(getCheckForTriggeredDeployments(), overrides);
    }

    default String getLatestBuildID(IClient client,
            Map<String, String> overrides) {
        Map<String, String> filter = new HashMap<String, String>();
        filter.put("openshift.io/build-config.name", getBldCfg(overrides));
        List<IBuild> blds = client.list(ResourceKind.BUILD,
                getNamespace(overrides), filter);
        // we'll get a list of startimes that we'll sort on to get the latest;
        // we'll also build a map of startimes to build ids, so that we return
        // the build ID related to the latest starttime
        List<String> starttimes = new ArrayList<String>();
        Map<String, String> timeToID = new HashMap<String, String>();
        /*
         * in case multiple builds have the same start time (parallel builds),
         * use our custom comparator to make sure build-4 comes before build-38;
         * then, if consecutive builds have the same start time, that later id
         * number will be later in this list, and replace earlier entries in the
         * timeToID map
         */
        Collections.sort(blds, new BuildNameComparator());
        for (IBuild bld : blds) {
            starttimes.add(bld.getBuildStatus().getStartTime());
            timeToID.put(bld.getBuildStatus().getStartTime(), bld.getName());
        }
        String starttime = null;
        if (starttimes.size() > 0) {
            Collections.sort(starttimes);
            starttime = starttimes.get(starttimes.size() - 1);
        }
        return timeToID.get(starttime);
    }

    default boolean coreLogic(Launcher launcher, TaskListener listener,
            Map<String, String> overrides) throws InterruptedException {
        boolean chatty = Boolean.parseBoolean(getVerbose(overrides));
        boolean checkDeps = Boolean
                .parseBoolean(getCheckForTriggeredDeployments(overrides));
        listener.getLogger().println(
                String.format(MessageConstants.START_BUILD_RELATED_PLUGINS,
                        DISPLAY_NAME, getBldCfg(overrides),
                        getNamespace(overrides)));

        // get oc client
        IClient client = this.getClient(listener, DISPLAY_NAME, overrides);

        if (client != null) {
            String bldId = getLatestBuildID(client, overrides);

            if (!checkDeps) {
                listener.getLogger()
                        .println(
                                String.format(
                                        MessageConstants.WAITING_ON_BUILD_STARTED_ELSEWHERE,
                                        bldId));
            } else {
                listener.getLogger()
                        .println(
                                String.format(
                                        MessageConstants.WAITING_ON_BUILD_STARTED_ELSEWHERE_PLUS_DEPLOY,
                                        bldId));
            }

            return this
                    .verifyBuild(
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime()),
                            getTimeout(listener, chatty, overrides), client,
                            getBldCfg(overrides), bldId,
                            getNamespace(overrides), chatty, listener,
                            DISPLAY_NAME, checkDeps, false, overrides);

        } else {
            return false;
        }

    }

    public class BuildNameComparator implements Comparator<IBuild> {

        // makes optimizing assumptions based on format of openshift build names
        @Override
        public int compare(IBuild o1, IBuild o2) {
            String id1 = o1.getName();
            String id2 = o2.getName();
            int at = StringUtils.indexOfDifference(id1, id2);
            // no index means strings equal
            if (at == StringUtils.INDEX_NOT_FOUND)
                return 0;
            String rem0 = id1.substring(at);
            String rem1 = id2.substring(at);
            // means id2 is just longer, so id1 < id2, so return -1
            if (StringUtils.isBlank(rem0))
                return -1;
            // means id1 is just longer, so id1 > id2, so return 1
            if (StringUtils.isBlank(rem1))
                return 1;
            if (Integer.valueOf(rem0) < Integer.valueOf(rem1))
                return -1;
            return 1;
        }

    }

}
