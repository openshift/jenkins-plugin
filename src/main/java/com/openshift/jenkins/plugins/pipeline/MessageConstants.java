package com.openshift.jenkins.plugins.pipeline;

public class MessageConstants {

/*
 * Generally speaking (exceptions will be specifically noted), we are going to use 
 * %s String.format substitution 
 * - the plugin name, when the message is shared across multiple plugins; otherwise, the name will baked into the message
 * - state returned from the API server (i.e. a state of the deployment), unless we check for that explicit state, in which case it is baked into the message
 * - the id for a build or deployment
 * - when a specific input parameter is displayed	
 */
	
// shared across all the plugins
public static final String CANNOT_GET_CLIENT = "\n\nExiting \"%s\" unsuccessfully; a client connection to \"%s\" could not be obtained.";

/*
 * These messages are shared between "Trigger OpenShift Build" jenkins build step implemented by OpenShiftBuilder,
 * "Verify OpenShift Builds" implemented by OpenShiftBuildVerifier, and "Cancel OpenShift Build(s)"
 * 
 */
public static final String EXIT_BUILD_BAD = "\n\nExiting \"%s\" unsuccessfully; build \"%s\" has completed with status:  [%s].";
public static final String EXIT_BUILD_GOOD_DEPLOY_BAD = "\n\nExiting \"%s\" unsuccessfully; build \"%s\" has completed with status:  [Complete]. However, not all deployments with ImageChange triggers based on this build's output triggered off of the new image.";
public static final String EXIT_BUILD_GOOD_DEPLOY_IGNORED = "\n\nExiting \"%s\" successfully; build \"%s\" has completed with status:  [Complete].";
public static final String EXIT_BUILD_GOOD_DEPLOY_GOOD = EXIT_BUILD_GOOD_DEPLOY_IGNORED + "  All deployments with ImageChange triggers based on this build's output triggered off of the new image.";
public static final String START_BUILD_RELATED_PLUGINS = "\n\nStarting the \"%s\" step with build config \"%s\" from the project \"%s\".";

/*
 * These messages are for the "Trigger OpenShift Build" jenkins build step implemented by OpenShiftBuilder
 */
public static final String WAITING_ON_BUILD = "  Started build \"%s\" and waiting for build completion ...";
public static final String WAITING_ON_BUILD_PLUS_DEPLOY = "  Started build \"%s\" and waiting for build completion followed by a new deployment ...";
public static final String EXIT_BUILD_NO_BUILD_OBJ = "\n\nExiting \"" + OpenShiftBuilder.DISPLAY_NAME + "\" unsuccessfully; could not retrieve the associated Build object from the start build command.";
public static final String EXIT_BUILD_NO_POD_OBJ = "\n\nExiting \"" + OpenShiftBuilder.DISPLAY_NAME + "\" unsuccessfully; the build pod for build \"%s\" was not found in time.";
public static final String EXIT_BUILD_NO_BUILD_CONFIG_OBJ = "\n\nExiting \"" + OpenShiftBuilder.DISPLAY_NAME + "\" unsuccessfully; the build config \"%s\" could not be read.";

/*
 * These messages are for the "Verify OpenShift Builds" jenkins build step implemented by OpenShiftBuildVerifier
 * Reminder - "Verify OpenShift Builds" looks at builds started externally from the Jenkins project this step is in 
 */
public static final String WAITING_ON_BUILD_STARTED_ELSEWHERE = "  Waiting on build \"%s\" to complete ...";
public static final String WAITING_ON_BUILD_STARTED_ELSEWHERE_PLUS_DEPLOY = "  Waiting on build \"%s\" to complete followed by a new deployment ...";


/*
 * These messages are for the "Cancel OpenShift Builds" jenkins post-build action implemented by OpenShiftBuildCanceller
 */
public static final String CANCELLED_BUILD = "  Cancelled build \"%s\".";
public static final String EXIT_BUILD_CANCEL = "\n\nExiting \"%s\" successfully with %d builds cancelled.";

/*
 * These messages are for the "Create OpenShift Resource(s)" jenkins build step implemented by OpenShiftCreator
 */
public static final String START_CREATE_OBJS = "\n\nStarting \"" + OpenShiftCreator.DISPLAY_NAME + "\" with the project \"%s\".";
public static final String CREATED_OBJ = "  Created a \"%s\"";
public static final String FAILED_OBJ = "  Failed to create a \"%s\"";
public static final String EXIT_CREATE_BAD = "\n\nExiting \"" + OpenShiftCreator.DISPLAY_NAME + "\" unsuccessfully, with %d resource(s) created and %d failed attempt(s).";
public static final String EXIT_CREATE_GOOD = "\n\nExiting \"" + OpenShiftCreator.DISPLAY_NAME + "\" successfully, with %d resource(s) created.";
public static final String TYPE_NOT_SUPPORTED = "  The API resource \"%s\" is not currently supported by this step.";

/*
 * These messages are shared between "Cancel OpenShift Deployment", "Scale OpenShift Deployment", "Verify OpenShift Deployment", and 
 * "Trigger OpenShift Deployment", implemented respectively by OpenShiftDeployCanceller, OpenShiftScaler, OpenShiftDeploymentVerifier, and OpenShiftDeployer
 * 
 */
public static final String EXIT_DEPLOY_RELATED_PLUGINS_NO_CFG = "\n\nExiting \"%s\" unsuccessfully; the deployment config \"%s\" could not be read.";
public static final String START_DEPLOY_RELATED_PLUGINS = "\n\nStarting \"%s\" with deployment config \"%s\" from the project \"%s\".";

/*
 * These messages are shared between "Verify OpenShift Deployment", and 
 * "Trigger OpenShift Deployment", implemented respectively by OpenShiftDeploymentVerifier, and OpenShiftDeployer
 * 
 */
public static final String EXIT_DEPLOY_RELATED_PLUGINS_BAD = "\n\nExiting \"%s\" unsuccessfully; deployment \"%s\" has completed with status:  [%s].";
public static final String EXIT_DEPLOY_RELATED_PLUGINS_GOOD_REPLICAS_IGNORED = "\n\nExiting \"%s\" successfully; deployment \"%s\" has completed with status:  [Complete].";
/*
 * These messages are for the "Cancel OpenShift Deployment" jenkins post-build action implemented by OpenShiftDeployCanceller
 * note we call this an "action" vs. "step" to help distinguish it as a post-build action vs. a build step
 */
public static final String EXIT_DEPLOY_CANCEL_GOOD_NOOP = "\n\nExiting \"" + OpenShiftDeployCanceller.DISPLAY_NAME + "\" successfully; the deployment \"%s\" is not in-progress; its status is:  [%s].";
public static final String EXIT_DEPLOY_CANCEL_GOOD_DIDIT = "\n\nExiting \"" + OpenShiftDeployCanceller.DISPLAY_NAME + "\" successfully; the deployment \"%s\" has been cancelled.";
public static final String EXIT_DEPLOY_CANCEL_BAD_NO_REPCTR = "\n\nExiting \"" + OpenShiftDeployCanceller.DISPLAY_NAME + "\" unsuccessfully; the latest deployment for \"%s\" could not be retrieved.";

/*
 * These messages are for the "Trigger OpenShift Deployment" jenkins build step implemented by OpenShiftDeployer
 */
public static final String EXIT_DEPLOY_TRIGGER_TIMED_OUT = "\n\nExiting \"" + OpenShiftDeployCanceller.DISPLAY_NAME + "\" unsuccessfully; gave up on deployment \"%s\" with status:  [%s].";

/*
 * These messages are for the "Verify OpenShift Deployment" jenkins build step implemented by OpenShiftDeploymenVerifier
 */
public static final String WAITING_ON_DEPLOY = "  Waiting on the latest deployment for \"%s\" to complete ...";
public static final String WAITING_ON_DEPLOY_PLUS_REPLICAS = "  Waiting on the latest deployment for \"%s\" to complete and scale to \"%s\" replica(s) ...";
public static final String EXIT_DEPLOY_VERIFY_GOOD_REPLICAS_GOOD = EXIT_DEPLOY_RELATED_PLUGINS_GOOD_REPLICAS_IGNORED + "  The deployment reached \"%s\" replica(s).";
public static final String EXIT_DEPLOY_VERIFY_BAD_REPLICAS_BAD = EXIT_DEPLOY_RELATED_PLUGINS_BAD + "  And the replica count was \"%s\".";

/*
 * These messages are for the "Scale OpenShift Deployment" jenkins build step implemented by OpenShiftScaler
 */
public static final String SCALING = "  Scaling to \"%s\" replicas ...";
public static final String SCALING_PLUS_REPLICA_CHECK = " Scaling to \"%s\" replicas and verifying the replica count is reached ...";
public static final String EXIT_SCALING_NOOP = "\n\nExiting \"" + OpenShiftScaler.DISPLAY_NAME + "\" successfully; no deployments for \"%s\" were found, so a replica count of \"0\" already exists.";
public static final String EXIT_SCALING_BAD = "\n\nExiting \"" + OpenShiftScaler.DISPLAY_NAME + "\" unsuccessfully; the call to \"%s\" failed.";
public static final String EXIT_SCALING_TIMED_OUT = "\n\nExiting \"" + OpenShiftScaler.DISPLAY_NAME + "\" unsuccessfully; the deployment \"%s\" did not reach \"%s\" replica(s) in time.";
public static final String EXIT_SCALING_GOOD = "\n\nExiting \"" + OpenShiftScaler.DISPLAY_NAME + "\" successfully for deployment \"%s\".";
public static final String EXIT_SCALING_GOOD_REPLICAS_GOOD = "\n\nExiting \"" + OpenShiftScaler.DISPLAY_NAME + "\" successfully, where the deployment \"%s\" reached \"%s\" replica(s).";

/*
 * These messages are for the "OpenShift ImageStream" source code management plugin
 * Note, there aren't clear cut start and exit points with this one; the SCM has multiple 
 * entry points based on how Jenkins decides to call it
 */
public static final String SCM_CALC = "\n\nThe \"%s\" SCM will return the last revision state stored in Jenkins for the image stream \"%s\" and tag \"%s\" from the project \"%s\".";
public static final String SCM_LAST_REV = "  Last revision:  [%s]";
public static final String SCM_NO_REV = "  No revision state has been retrieved and stored yet.";
public static final String SCM_COMP = "\n\nThe \"%s\" SCM is pulling the lastest revision state from OpenShift for the image stream \"%s\" and tag \"%s\" from the project \"%s\" and storing in Jenkins.";
public static final String SCM_CHANGE = "\n\n A revision change was found this polling cycle.";
public static final String SCM_NO_CHANGE = "\n\n No revision change found this polling cycle.";

/*
 * These messages are for the "Tag OpenShift Image" jenkins build step implemented by OpenShiftImageTagger
 */
public static final String START_TAG = "\n\nStarting \"%s\" with the source [image stream:tag] \"%s:%s\" and destination [image stream:tag] \"%s:%s\" from the project \"%s\".";
public static final String EXIT_OK = "\n\nExiting \"%s\" successfully.";

/*
 * These messages are for the "Verify OpenShift Service" jenkins build step implemented by OpenShiftServiceVerifier
 */
public static final String START_SERVICE_VERIFY = "\n\nStarting \"%s\" for the service \"%s\" from the project \"%s\".";
public static final String SERVICE_CONNECTING = "  Attempting to connect to \"%s\" ...";
public static final String EXIT_SERVICE_VERIFY_GOOD = "\n\nExiting \"%s\" successfully; a connection to \"%s\" was made.";
public static final String EXIT_SERVICE_VERIFY_BAD = "\n\nExiting \"%s\" unsuccessfully; a connection to \"%s\" could not be made.";
public static final String SOCKET_TIMEOUT = " a socket level communication timeout to \"%s\" occurred.";
public static final String HTTP_ERR = " the HTTP level communication error \"%s\" for \"%s\" occurred.";

}
