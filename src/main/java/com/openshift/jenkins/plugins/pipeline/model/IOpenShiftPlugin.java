package com.openshift.jenkins.plugins.pipeline.model;

import com.openshift.internal.restclient.DefaultClient;
import com.openshift.internal.restclient.authorization.AuthorizationContext;
import com.openshift.internal.restclient.model.DeploymentConfig;
import com.openshift.internal.restclient.okhttp.OpenShiftAuthenticator;
import com.openshift.internal.restclient.okhttp.ResponseCodeInterceptor;
import com.openshift.jenkins.plugins.pipeline.Auth;
import com.openshift.jenkins.plugins.pipeline.MessageConstants;
import com.openshift.jenkins.plugins.pipeline.OpenShiftBuildCanceller;
import com.openshift.restclient.ClientBuilder;
import com.openshift.restclient.IClient;
import com.openshift.restclient.OpenShiftException;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.http.IHttpConstants;
import com.openshift.restclient.model.*;
import com.openshift.restclient.model.deploy.IDeploymentImageChangeTrigger;
import com.openshift.restclient.model.deploy.IDeploymentTrigger;
import com.openshift.restclient.model.route.IRoute;
import com.openshift.restclient.utils.SSLUtils;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import io.fabric8.jenkins.openshiftsync.BuildCause;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.jboss.dmr.ModelNode;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;

import jenkins.model.Jenkins;

//import com.openshift.restclient.authorization.TokenAuthorizationStrategy;

public interface IOpenShiftPlugin extends IOpenShiftParameterOverrides {

    // arg1=resource type, arg2=object name
    public static final String ANNOTATION_FAILURE = "\nWARNING: Failed to annotate %s %s with job information.";
    // Jenkins env variable keys
    // This key contains the full url to the jenkins job instance that is
    // currently running.
    public static final String BUILD_URL_ENV_KEY = "BUILD_URL";
    // this key contains the jenkins job name
    public static final String JOB_NAME = "JOB_NAME";
    // this key contains the jenkins job instance number
    public static final String BUILD_NUMBER = "BUILD_NUMBER";
    // Openshift annotation keys
    // This annotation will contain a the full url of the jenkins job instance
    // that lead
    // to this object (e.g. build, deployment) being created.
    public static final String BUILD_URL_ANNOTATION = "openshift.io/jenkins-build-uri";

    public static final String KUBERNETES_SERVICE_HOST_ENV_KEY = "KUBERNETES_SERVICE_HOST";
    public static final String KUBERNETES_SERVICE_PORT_ENV_KEY = "KUBERNETES_SERVICE_PORT";
    public static final String KUBERNETES_MASTER_ENV_KEY = "KUBERNETES_MASTER";

    public static final String NAMESPACE_FILE = "/run/secrets/kubernetes.io/serviceaccount/namespace";

    public static final String NAMESPACE_ENV_VAR = "PROJECT_NAME";

    public static final String NAMESPACE_SYNC_BUILD_CAUSE = "NAMESPACE_SYNC_BUILD_CAUSE";

    // states of note
    public static final String STATE_RUNNING = "Running";
    public static final String STATE_COMPLETE = "Complete";
    public static final String STATE_CANCELLED = "Cancelled";
    public static final String STATE_ERROR = "Error";
    public static final String STATE_FAILED = "Failed";

    String getBaseClassName();

    String getAuthToken();

    default String getAuthToken(Map<String, String> overrides) {
        return getOverride(getAuthToken(), overrides);
    }

    String getVerbose();

    default String getVerbose(Map<String, String> overrides) {
        return getOverride(getVerbose(), overrides);
    }

    Auth getAuth();

    void setAuth(Auth auth);

    // TokenAuthorizationStrategy getToken();

    // void setToken(TokenAuthorizationStrategy token);

    public String getApiURL();

    default String getApiURL(Map<String, String> overrides) {
        String val = getOverride(getApiURL(), overrides);
        if ((val == null || val.length() == 0) && overrides != null
                && overrides.containsKey(KUBERNETES_SERVICE_HOST_ENV_KEY)) {
            val = overrides.get(KUBERNETES_SERVICE_HOST_ENV_KEY);
        }
        if (val != null && val.length() > 0 && !val.startsWith("https://"))
            val = "https://" + val;
        if (val == null || val.length() == 0)
            val = "https://openshift.default.svc.cluster.local";
        return val;
    }

    public String getNamespace();

    default String getNamespace(Map<String, String> overrides) {
        String val = getOverride(getNamespace(), overrides);
        if (val.length() == 0) {
            if (overrides != null
                    && overrides.containsKey(NAMESPACE_SYNC_BUILD_CAUSE))
                val = overrides.get(NAMESPACE_SYNC_BUILD_CAUSE);
            else if (overrides != null
                    && overrides.containsKey(NAMESPACE_ENV_VAR))
                val = overrides.get(NAMESPACE_ENV_VAR);
            else {
                File f = new File(NAMESPACE_FILE);
                if (f.exists())
                    val = Auth.pullTokenFromFile(f, null);
            }
        }
        return val;
    }

    boolean coreLogic(Launcher launcher, TaskListener listener,
            Map<String, String> overrides) throws InterruptedException;

    default IClient getClient(TaskListener listener, String displayName,
            Map<String, String> overrides) {
        return this.getClient(listener, displayName, overrides, Auth
                .deriveBearerToken(getAuthToken(overrides), listener,
                        Boolean.getBoolean(getVerbose(overrides)), overrides));
    }

    default IClient getClient(TaskListener listener, String displayName,
            Map<String, String> overrides, String token) {
        Auth auth = getAuth();
        ClientBuilder cb = new ClientBuilder(getApiURL(overrides))
                .sslCertificateCallback(auth)
                .usingToken(token)
                .sslCertificateCollection(getApiURL(overrides), auth.getCerts());
        if (auth.useCert())
            cb.sslCertCallbackWithDefaultHostnameVerifier(true);
        IClient client = cb.build();
        if (client == null) {
            listener.getLogger().println(
                    String.format(MessageConstants.CANNOT_GET_CLIENT,
                            displayName, getApiURL(overrides)));
        }
        return new RetryIClient(client, listener);
    }

    // TODO move to openshift-restclient-java IReplicationController
    default String getReplicationControllerState(IReplicationController rc) {
        return rc.getAnnotation("openshift.io/deployment.phase");
    }

    // TODO move to openshift-restclient-java IReplicationController
    default boolean isReplicationControllerScaledAppropriately(
            IReplicationController rc, boolean checkCount, int count) {
        boolean scaledAppropriately = false;
        if (rc == null)
            return scaledAppropriately;
        // check state, and if needed then check replica count
        if (getReplicationControllerState(rc).equalsIgnoreCase(STATE_COMPLETE)) {
            if (!checkCount) {
                scaledAppropriately = true;
            } else if (rc.getCurrentReplicaCount() == count) {
                scaledAppropriately = true;
            }
        }
        return scaledAppropriately;
    }

    default Map<String, String> consolidateEnvVars(TaskListener listener,
            EnvVars env, Run<?, ?> run, AbstractBuild<?, ?> build,
            Launcher launcher, boolean chatty) {
        // EnvVars extends TreeMap
        TreeMap<String, String> overrides = new TreeMap<String, String>();
        // merge from all potential sources
        if (run != null) {
            try {
                EnvVars runEnv = run.getEnvironment(listener);
                if (chatty)
                    listener.getLogger().println("run env vars:  " + runEnv);
                overrides.putAll(runEnv);
            } catch (IOException | InterruptedException e) {
                if (chatty)
                    e.printStackTrace(listener.getLogger());
            }
        }

        if (build != null) {
            try {
                EnvVars buildEnv = build.getEnvironment(listener);
                if (chatty)
                    listener.getLogger()
                            .println("build env vars:  " + buildEnv);
                overrides.putAll(buildEnv);
            } catch (IOException | InterruptedException e) {
                if (chatty)
                    e.printStackTrace(listener.getLogger());
            }
        }

        try {
            EnvVars computerEnv = null;
            Computer computer = Computer.currentComputer();
            if (computer != null) {
                computerEnv = computer.getEnvironment();
            } else {
                computer = launcher.getComputer();
                if (computer != null) {
                    computerEnv = computer.getEnvironment();
                }
            }
            if (chatty)
                listener.getLogger().println(
                        "computer env vars:  " + computerEnv);
            if (computerEnv != null)
                overrides.putAll(computerEnv);
        } catch (IOException | InterruptedException e2) {
            if (chatty)
                e2.printStackTrace(listener.getLogger());
        }

        if (env != null) {
            if (chatty)
                listener.getLogger().println("DSL injected env vars: " + env);
            overrides.putAll(env);
        }

        return overrides;
    }

    default Map<String, String> constructBuildUrl(TaskListener listener,
            Map<String, String> overrides, boolean chatty) {
        // for our pipeline strategy / build annotaion logic, we will construct
        // the BUILD_URL env var
        // if it is not set (which can occur with freestyle jobs when the
        // openshift-sync plugin is not
        // leveraged
        String jobName = overrides.get(JOB_NAME);
        String buildNum = overrides.get(BUILD_NUMBER);
        // we check for null but these should always be there from a jenkins api
        // guarantee perspective
        if (jobName != null && buildNum != null) {
            jobName = jobName.replace(" ", "%20");
            IClient client = getClient(listener, getDisplayName(), overrides);
            List<IRoute> routes = new ArrayList<IRoute>();
            try {
                routes = client.list(ResourceKind.ROUTE,
                        getNamespace(overrides));
            } catch (Throwable t) {
                if (chatty)
                    t.printStackTrace(listener.getLogger());
            }
            for (IRoute route : routes) {
                if (route.getServiceName().equals("jenkins")) {
                    String base = route.getURL();
                    if (!base.endsWith("/"))
                        base = base + "/";
                    overrides.put(BUILD_URL_ENV_KEY, base + "job/" + jobName
                            + "/" + buildNum + "/");
                    break;
                }
            }

            if (!overrides.containsKey(BUILD_URL_ENV_KEY))
                overrides
                        .put(BUILD_URL_ENV_KEY, Jenkins.getInstance()
                                .getRootUrl()
                                + "job/"
                                + jobName
                                + "/"
                                + buildNum + "/");
        } else {
            if (chatty)
                listener.getLogger()
                        .printf("\n missing jenkins job/build info: job %s build %s \n",
                                jobName, buildNum);
        }

        return overrides;
    }

    // TODO leaving EnvVars env param for now ... in DSL scenario, curious of
    // the injected contents vs. the run/build/computer contents
    default boolean doItCore(TaskListener listener, EnvVars env, Run<?, ?> run,
            AbstractBuild<?, ?> build, Launcher launcher)
            throws InterruptedException {
        boolean chatty = Boolean.parseBoolean(getVerbose());
        
        listener.getLogger().println("NOTE: steps like this one from the OpenShift Pipeline Plugin" +
        " will not be supported against OpenShift API Servers later than v3.11");

        if (run == null && build == null)
            throw new RuntimeException(
                    "Either the run or build parameter must be set");

        // if the openshift sync plugin is in play and has set the build cause,
        // we can infer the namespace to use
        // from there, so let's store in the the env map; we could parse the
        // short description string and not depend
        // on openshift-sync, but for now let's add the dependency to call the
        // namespace getter. If size becomes a concern or circular
        // dependencies arise, we can remove the explicit dependency and parse
        // the string
        BuildCause cause = run != null ? run.getCause(BuildCause.class) : null;
        if (cause != null) {
            if (chatty)
                listener.getLogger().println(
                        "build cause namespace from sync plugin is "
                                + cause.getNamespace());
            env.put(NAMESPACE_SYNC_BUILD_CAUSE, cause.getNamespace());
        }

        Map<String, String> overrides = consolidateEnvVars(listener, env, run,
                build, launcher, chatty);

        setAuth(Auth.createInstance(chatty ? listener : null,
                getApiURL(overrides), overrides));
        // setToken(new TokenAuthorizationStrategy(Auth.deriveBearerToken(build
        // != null ? build : run, getAuthToken(overrides), listener, chatty)));

        // this needs to follow the auth/token set up since a rest client
        // instance is set up if we need
        // to set up the BUILD_URL
        if (!overrides.containsKey(BUILD_URL_ENV_KEY)) {
            overrides = constructBuildUrl(listener, overrides, chatty);
        }

        if (chatty)
            listener.getLogger().println(
                    "\n\nOpenShift Pipeline Plugin: env vars for this job:  "
                            + overrides);

        try {
            boolean rc = coreLogic(launcher, listener, overrides);
            return rc;
        } catch (Throwable t) {
            t.printStackTrace(listener.getLogger());
            return false;
        }
    }

    public String getDisplayName();

    default void doIt(Run<?, ?> run, FilePath workspace, Launcher launcher,
            TaskListener listener) throws InterruptedException, IOException {
        boolean successful = this.doItCore(listener, null, run, null, launcher);
        if (!successful)
            throw new AbortException("\"" + getDisplayName() + "\" failed");
    }

    default boolean doIt(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) throws IOException, InterruptedException {
        boolean successful = this.doItCore(listener, null, null, build,
                launcher);
        if (!successful)
            throw new AbortException("\"" + getDisplayName() + "\" failed");
        return successful;
    }

    default boolean isBuildRunning(String bldState) {
        if (bldState != null && (bldState.equals(STATE_RUNNING)))
            return true;
        return false;
    }

    default boolean isBuildFinished(String bldState) {
        if (bldState != null
                && (bldState.equals(STATE_COMPLETE)
                        || bldState.equals(STATE_FAILED)
                        || bldState.equals(STATE_ERROR) || bldState
                            .equals(STATE_CANCELLED)))
            return true;
        return false;
    }

    default boolean isDeployFinished(String deployState) {
        if (deployState != null
                && (deployState.equals(STATE_FAILED) || deployState
                        .equals(STATE_COMPLETE)))
            return true;
        return false;
    }

    default boolean verifyBuild(long startTime, long wait, IClient client,
            String bldCfg, String bldId, String namespace, boolean chatty,
            TaskListener listener, String displayName, boolean checkDeps,
            boolean annotateRC, Map<String, String> env)
            throws InterruptedException {
        String bldState = null;
        while (TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) < (startTime + wait)) {
            IBuild bld = client.get(ResourceKind.BUILD, bldId, namespace);
            bldState = bld.getStatus();
            if (chatty)
                listener.getLogger().println(
                        "\nOpenShiftBuilder post bld launch bld state:  "
                                + bldState);
            if (!isBuildFinished(bldState)) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // need to throw as this indicates the step as been
                    // cancelled
                    // also attempt to cancel build on openshift side
                    OpenShiftBuildCanceller canceller = new OpenShiftBuildCanceller(
                            getApiURL(env), getNamespace(env),
                            getAuthToken(env), getVerbose(env), bldCfg);
                    canceller.setAuth(getAuth());
                    canceller.coreLogic(null, listener, env);
                    throw e;
                }
            } else {
                break;
            }
        }
        if (bldState == null || !bldState.equals(STATE_COMPLETE)) {
            String displayState = bldState;
            if (displayState == null)
                displayState = "NotStarted";
            listener.getLogger().println(
                    String.format(MessageConstants.EXIT_BUILD_BAD, displayName,
                            bldId, wait, displayState));
            return false;
        } else {
            if (checkDeps) {
                // calling this will annotate any RC that was created as a
                // result of this build, with the jenkins job info.
                boolean triggerSuccess = didAllImagesChangeIfNeeded(bldCfg,
                        listener, chatty, client, namespace, wait, annotateRC,
                        env);
                if (triggerSuccess) {
                    listener.getLogger()
                            .println(
                                    String.format(
                                            MessageConstants.EXIT_BUILD_GOOD_DEPLOY_GOOD,
                                            displayName, bldId));
                    return true;
                } else {
                    listener.getLogger()
                            .println(
                                    String.format(
                                            MessageConstants.EXIT_BUILD_GOOD_DEPLOY_BAD,
                                            displayName, bldId));
                    return false;
                }
            } else {
                listener.getLogger()
                        .println(
                                String.format(
                                        MessageConstants.EXIT_BUILD_GOOD_DEPLOY_IGNORED,
                                        displayName, bldId));
                return true;
            }
        }
    }

    default boolean doesDCTriggerOnImageTag(IClient client,
            IDeploymentConfig dc, String imageTag, boolean chatty,
            TaskListener listener) {
        if (dc == null || imageTag == null)
            throw new RuntimeException(
                    "needed param null for doesDCTriggerOnImageTag");
        if (listener == null)
            chatty = false;

        Collection<IDeploymentTrigger> triggers = dc.getTriggers();
        for (IDeploymentTrigger trigger : triggers) {
            if (trigger instanceof IDeploymentImageChangeTrigger) {
                IDeploymentImageChangeTrigger ict = (IDeploymentImageChangeTrigger) trigger;
                if (chatty)
                    listener.getLogger().println(
                            "\n\n found ict " + ict.toString() + " with from "
                                    + ict.getFrom().getNameAndTag()
                                    + ", auto: " + ict.isAutomatic());
                if (ict.getFrom().getNameAndTag().contains(imageTag)
                        && ict.isAutomatic()) {
                    if (chatty)
                        listener.getLogger().println(
                                "\n\n ict  triggers off of " + imageTag);
                    return true;
                }
            }
        }

        return false;

    }

    default boolean didICTCauseDeployment(IClient client, IDeploymentConfig dc,
            String imageTag, boolean chatty, TaskListener listener, long wait)
            throws InterruptedException {
        long currTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        while (TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) - (wait / 3) <= currTime) {
            dc = client.get(ResourceKind.DEPLOYMENT_CONFIG, dc.getName(),
                    dc.getNamespace());
            if (!dc.didImageTrigger(imageTag)) {
                if (chatty)
                    listener.getLogger().println("\n ICT did not fire");
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    // need to throw as this indicates the step as been
                    // cancelled
                    throw e;
                }
            } else {
                if (chatty)
                    listener.getLogger().println(
                            "\n ICT fired for deployment " + dc.toJson(false));
                return true;
            }
        }

        if (chatty) {
            listener.getLogger().println(
                    "\n done checking dc " + dc.toJson(false));
        }

        return false;

    }

    default boolean didImageChangeFromPreviousVersion(IClient client,
            int latestVersion, boolean chatty, TaskListener listener,
            String depCfg, String namespace, String latestImageHexID,
            String imageTag) {
        // now get previous RC, fetch image Hex ID, and compare
        int previousVersion = latestVersion - 1;
        if (previousVersion < 1) {
            if (chatty)
                listener.getLogger().println(
                        "\n first version skip image compare");
            return true;
        }
        IReplicationController prevRC = null;
        try {
            prevRC = client.get(ResourceKind.REPLICATION_CONTROLLER, depCfg
                    + "-" + previousVersion, namespace);
        } catch (Throwable t) {
            if (chatty)
                t.printStackTrace(listener.getLogger());
        }

        if (prevRC == null) {
            listener.getLogger().println(
                    "\n\n could not obtain previous replication controller");
            return false;
        }

        // get the dc again from the rc vs. passing the dc in aIClient client,
        // IDeploymentConfig dc, String imageTag, boolean chatty, TaskListener
        // listener, long wait)s a form of cross reference verification
        String dcJson = prevRC
                .getAnnotation("openshift.io/encoded-deployment-config");
        if (dcJson == null || dcJson.length() == 0) {
            listener.getLogger()
                    .println(
                            "\n\n associated DeploymentConfig for previous ReplicationController missing");
            return false;
        }
        ModelNode dcNode = ModelNode.fromJSONString(dcJson);
        IDeploymentConfig dc = new DeploymentConfig(dcNode, client, null);
        String previousImageHexID = dc
                .getImageHexIDForImageNameAndTag(imageTag);

        if (previousImageHexID == null || previousImageHexID.length() == 0) {
            // don't count ill obtained prev image id as successful image id
            // change
            listener.getLogger()
                    .println(
                            "\n\n could not obtain hex image ID for previous deployment");
            return false;
        }

        if (latestImageHexID.equals(previousImageHexID)) {
            if (chatty)
                listener.getLogger().println(
                        "\n images still the same " + latestImageHexID);
            return false;
        } else {
            if (chatty)
                listener.getLogger().println(
                        "\n image did change, new image " + latestImageHexID
                                + " old image " + previousImageHexID);
            return true;
        }
    }

    default boolean didAllImagesChangeIfNeeded(String buildConfig,
            TaskListener listener, boolean chatty, IClient client,
            String namespace, long wait, boolean annotateRC,
            Map<String, String> env) throws InterruptedException {
        if (chatty)
            listener.getLogger().println(
                    "\n checking if the build config " + buildConfig
                            + " got the image changes it needed");
        IBuildConfig bc = client.get(ResourceKind.BUILD_CONFIG, buildConfig,
                namespace);
        if (bc == null) {
            if (chatty)
                listener.getLogger().println("\n bc null for " + buildConfig);
        }

        String imageTag = null;
        try {
            imageTag = bc.getOutputRepositoryName();
            if (chatty)
                listener.getLogger().println(
                        "\n\n build config output image tag " + imageTag);
        } catch (Throwable t) {
        }

        if (imageTag == null) {
            if (chatty)
                listener.getLogger().println(
                        "\n\n build config " + bc.getName()
                                + " does not output an image");
            return true;
        } else {
            if (chatty)
                listener.getLogger().println(
                        "\n\n build config " + bc.getName()
                                + " generates image " + imageTag);
        }

        // find deployment configs with image change triggers
        List<IDeploymentConfig> allDC = client.list(
                ResourceKind.DEPLOYMENT_CONFIG, namespace);
        if (allDC == null || allDC.size() == 0) {
            if (chatty)
                listener.getLogger().println(
                        "\n\n no deployment configs present");
            return true;
        }
        List<IDeploymentConfig> dcsToCheck = new ArrayList<IDeploymentConfig>();
        for (IDeploymentConfig dc : allDC) {
            if (chatty)
                listener.getLogger().println(
                        "\n checking triggers on dc " + dc.getName());
            if (doesDCTriggerOnImageTag(client, dc, imageTag, chatty, listener)) {
                if (chatty)
                    listener.getLogger().println(
                            "\n adding dc to check " + dc.getName());
                dcsToCheck.add(dc);
            }
        }

        // cycle through the DCs triggering, comparing latest and previous RC,
        // see if image changed
        for (IDeploymentConfig dc : dcsToCheck) {
            if (chatty) {
                listener.getLogger().println(
                        "\n looking at image ids for " + dc.getName()
                                + " with json " + dc.toJson(false));
            }

            // if a DC has both a config change trigger and image change
            // trigger, with the first deployment, it is
            // a race condition as to whether the cause gets marked as the CCT
            // or ICT; but in any event, as long as
            // the first deployment fires, we don't care why, so we bypass the
            // looping for ICT change / image change
            // verification
            if (dc.getLatestVersionNumber() == 1) {
                if (annotateRC) {
                    IReplicationController rc = getLatestReplicationController(
                            dc, namespace, client, chatty ? listener : null);
                    annotateJobInfoToResource(client, listener, chatty, env, rc);
                }
                return true;
            }

            if (this.didICTCauseDeployment(client, dc, imageTag, chatty,
                    listener, wait)) {
                String latestImageHexID = dc
                        .getImageHexIDForImageNameAndTag(imageTag);

                if (latestImageHexID == null) {
                    if (chatty)
                        listener.getLogger().println(
                                "\n dc " + dc.getName()
                                        + " did not have a reference to "
                                        + imageTag);
                    continue;
                }

                if (didImageChangeFromPreviousVersion(client,
                        dc.getLatestVersionNumber(), chatty, listener,
                        dc.getName(), namespace, latestImageHexID, imageTag)) {

                    if (annotateRC) {
                        IReplicationController rc = getLatestReplicationController(
                                dc, namespace, client, chatty ? listener : null);
                        annotateJobInfoToResource(client, listener, chatty,
                                env, rc);
                    }
                    if (chatty)
                        listener.getLogger()
                                .println(
                                        "\n dc "
                                                + dc.getName()
                                                + " did trigger based on image change as expected");
                } else {
                    if (chatty)
                        listener.getLogger()
                                .println(
                                        "\n dc "
                                                + dc.getName()
                                                + " did not trigger based on image change as expected");
                    return false;
                }
            } else {
                return false;
            }

        }

        return true;
    }

    default IReplicationController getLatestReplicationController(
            IDeploymentConfig dc, String namespace, IClient client,
            TaskListener listener) {
        int latestVersion = dc.getLatestVersionNumber();
        if (latestVersion == 0)
            return null;
        // start at the latest, but ignore newer replication controllers that
        // are cancelled
        IReplicationController rc = null;
        while (latestVersion > 0) {
            String repId = dc.getName() + "-" + latestVersion;
            try {
                rc = client.get(ResourceKind.REPLICATION_CONTROLLER, repId,
                        namespace);
            } catch (Throwable t) {
                if (listener != null)
                    t.printStackTrace(listener.getLogger());
            }
            if (rc != null) {
                if ("true".equalsIgnoreCase(rc
                        .getAnnotation("openshift.io/deployment.cancelled"))) {
                    rc = null;
                    latestVersion--;
                } else {
                    break;
                }
            }
        }
        return rc;
    }

    // Adds the jenkins job information annotation to the resource object.
    // Will retry up to 3 times, 3 seconds apart, in case of update conflicts.
    default boolean annotateJobInfoToResource(IClient client,
            TaskListener listener, boolean chatty, Map<String, String> env,
            IResource resource) {
        boolean annotated = false;
        String buildURL = env.get(IOpenShiftPlugin.BUILD_URL_ENV_KEY);
        // when not running as a pipeline, and if the plugin's token does not
        // have permission to
        // fetch routes, then the build_url key is not available, so we have to
        // construct the url from pieces. unfortunately the jenkins hostname
        // itself is not available
        // as an env variable in this case.
        if (buildURL == null) {
            buildURL = "job/" + env.get(IOpenShiftPlugin.JOB_NAME) + "/"
                    + env.get(IOpenShiftPlugin.BUILD_NUMBER);
        }
        for (int i = 0; i < 3; i++) {
            // anticipate update conflicts so get the latest object and retry
            // the update 3 times.
            IResource annotatedResource = client.get(resource.getKind(),
                    resource.getName(), resource.getNamespace());
            annotatedResource.setAnnotation(
                    IOpenShiftPlugin.BUILD_URL_ANNOTATION, buildURL);
            try {
                client.update(annotatedResource);
                annotated = true;
                break;
            } catch (OpenShiftException e) {
                if (chatty)
                    e.printStackTrace(listener.getLogger());
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
            }
        }
        if (!annotated) {
            listener.getLogger().println(
                    String.format(ANNOTATION_FAILURE, resource.getKind(),
                            resource.getName()));
        }
        return annotated;
    }

    default String httpGet(boolean chatty, TaskListener listener,
            Map<String, String> overrides, String urlString) {
        URL url = null;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            e.printStackTrace(listener.getLogger());
            return null;
        }
        // our lower level openshift-restclient-java usage here is not agreeable
        // with the TrustManager maintained there,
        // so we set up our own trust manager like we used to do in order to
        // verify the server cert
        try {
            AuthorizationContext authContext = new AuthorizationContext(
                    getAuthToken(overrides), null, null);
            ResponseCodeInterceptor responseCodeInterceptor = new ResponseCodeInterceptor();
            OpenShiftAuthenticator authenticator = new OpenShiftAuthenticator();
            Dispatcher dispatcher = new Dispatcher();
            RetryIClient iclient = (RetryIClient) this.getClient(listener,
                    getDisplayName(), overrides);
            DefaultClient client = iclient.getDefaultClient();
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .addInterceptor(responseCodeInterceptor)
                    .authenticator(authenticator)
                    .dispatcher(dispatcher)
                    .readTimeout(IHttpConstants.DEFAULT_READ_TIMEOUT,
                            TimeUnit.MILLISECONDS)
                    .writeTimeout(IHttpConstants.DEFAULT_READ_TIMEOUT,
                            TimeUnit.MILLISECONDS)
                    .connectTimeout(IHttpConstants.DEFAULT_READ_TIMEOUT,
                            TimeUnit.MILLISECONDS);
            X509TrustManager trustManager = null;
            if (getAuth().useCert()) {
                trustManager = Auth.createLocalTrustStore(getAuth(),
                        getApiURL(overrides));
            } else {
                // so okhttp is a bit of a PITA when it comes to enforcing skip
                // tls behavior
                // (it should just allow you to set a null ssl socket factory
                // given how
                // RealConnection/Address/Route work), but stack overflow came
                // to the rescue
                // (http://stackoverflow.com/questions/25509296/trusting-all-certificates-with-okhttp)
                // Create a trust manager that does not validate certificate
                // chains
                trustManager = new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] chain,
                            String authType) throws CertificateException {
                    }

                    @Override
                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] chain,
                            String authType) throws CertificateException {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[] {};
                    }
                };
            }

            SSLContext sslContext = null;
            try {
                sslContext = SSLUtils.getSSLContext(trustManager);
            } catch (KeyManagementException e) {
                if (chatty)
                    e.printStackTrace(listener.getLogger());
                return null;
            } catch (NoSuchAlgorithmException e) {
                if (chatty)
                    e.printStackTrace(listener.getLogger());
                return null;
            }
            builder.sslSocketFactory(sslContext.getSocketFactory(),
                    trustManager);

            OkHttpClient okClient = builder.build();
            authContext.setClient(client);
            responseCodeInterceptor.setClient(client);
            authenticator.setClient(client);
            authenticator.setOkClient(okClient);
            Request request = client.newRequestBuilderTo(url.toString()).get()
                    .build();
            Response result;
            try {
                result = okClient.newCall(request).execute();
                String response = result.body().string();
                return response;
            } catch (IOException e) {
                if (chatty)
                    e.printStackTrace(listener.getLogger());
            }
            return null;
        } finally {
            Auth.resetLocalTrustStore();
        }
    }

}
