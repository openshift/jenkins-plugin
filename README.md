# OpenShift V3 Plugin for Jenkins
This project provides a series Jenkins plugin implementations that operate on [Kubernetes based OpenShift](https://docs.openshift.org/latest/welcome/index.html).  In summary
they are a series of REST clients that interface with the OpenShift server via the [exposed API](https://docs.openshift.org/latest/rest_api/overview.html).
They minimally mimic the REST flows of common uses of the `oc` [CLI command](https://docs.openshift.org/latest/cli_reference/basic_cli_operations.html), but in several
instances additional REST flows have been added to provide validation of the operations being performed.

Their ultimate intent is to provide easy to use building blocks that simplify the construction of the jobs, workflows, and pipelines in Jenkins that operate against an OpenShift deployments.

NOTE:  This plugin requires JDK 1.8, based on its maven dependency openshift-restclient-java.

## Jenkins "build steps"

A series of Jenkins "build step" implementations are provided, which you can select from the `Add build step` pull down available on any project's configure page:

1. "Perform builds in OpenShift": performs the equivalent of an `oc start-build` command invocation, where the build logs can be echoed to the Jenkins plugin screen output in real time; in addition to confirming whether the build succeeded or not, the plugin will look to see if any deployment configs have image change triggers for the image produced by the build config; if found, those deployments will be analyzed to see if they were triggered by an image change.  Additionally, confirmation that the image actually change is performed, comparing the image used by the currently running replication controller with the image used by its immediate predecessor.

2. "Scale deployments in OpenShift":  performs the equivalent of an `oc scale` command invocation; the number of desired replicas is specified as a parameter to this build step, and the plugin will confirm whether the desired number of replicas was launched in a timely manner; if no integer is provided, it will assume 0 replica pods are desired.

3. "Trigger a deployment in OpenShift":  performs the equivalent of an `oc deploy --latest` command invocation; the plugin will confirm whether the desired number of replicas specified in the deployment config was launched in a timely manner.

4. "Verify a service is up in OpenShift": finds the ip and port for the specified OpenShift service, and attempts to make a HTTP connection to that ip/port combination to confirm the service is up.

5. "Tag an image in OpenShift": performs the equivalent of an `oc tag` command invocation in order to manipulate tags for images in OpenShift ImageStream's.

6. "Check deployment success in OpenShift":  determines whether the expected set of DeploymentConfig's, ReplicationController's, and active replicas are present based on prior use of either the "Scale deployments in OpenShift" (2) or "Trigger a deployment in OpenShift" (3) steps; its activities specifically include:

   - it first confirms the specified deployment config exists.
   - it then gets the list of all replication controllers for that DC, and finds the latest incarnation.
   - and then sees if within a reasonable time the current replica count is at least equal to the desired replica count.


7. "Get latest OpenShift build status":  performs the equivalent of an 'oc get builds` command invocation for the provided build config key provided; once the list of builds are obtained, the state of the latest build is inspected to see if it has completed successfully within a reasonable time period; it will also employ the same deployment triggering on image change verification done in the "Perform builds in OpenShift" build step; this build step allows for monitoring of builds either generated internally or externally from the Jenkins Job configuration

8. "Create resource(s) in OpenShift":  performs the equivalent of an `oc create` command invocation; this build step takes in the provided JSON or YAML text, and if it conforms to OpenShift schema, creates whichever OpenShift resources are specified.

## Jenkins "SCM"

An implementation of Jenkins SCM extension point is also provided that takes advantage of Jenkins' built in background polling and version management capabilities, but within the context of OpenShift Image Streams (we have taken the liberty of broadening the scope of what is considered "source"):

1. "Monitor OpenShift ImageStreams": the aforementioned baked-in polling mechanism provided by Jenkins is leveraged, exposing the common semantics between OpenShift ImageStreams (which are abstractions of Docker repositories) and SCMs; image IDs are maintained and treated like commit IDs for the requisite artifacts (images under the ImageStream instead of programmatic source files); when the image IDs for specific tags provided change (as reflected by updated "commit IDs" reported to Jenkins through the SCM plugin contract), Jenkins will initiate a build for the Job configuration in question; note, there are no "extractions" of any sort which leverage the workspaces provided by Jenkins to the SCMs.  It is expected that the build steps in the associated job configuration will initiate any OpenShift related activities that were dependent on the ImageStream resource being monitored.

## Jenkins "post-build actions"

A few Jenkins "post-build action" implementations are also provided, which you can select from the `Add post-build action` pull down available on any project's configure page:

1. "Cancel builds in OpenShift":  this action is intended to provide cleanup for a Jenkins job which failed because a build is hung (instead of terminating with a failure code); this step will allow you to perform the equivalent of a `oc cancel-build` for the provided build config; any builds under that build config which are not previously terminated (either successfully or unsuccessfully) or cancelled will be cancelled.

2. "Cancel deployments in OpenShift": this action is intended to provide cleanup for any OpenShift deployments left running when the Job completes;  this step will allow you to perform the equivalent of a `oc deploy --cancel` for the provided deployment config.

## Jenkins Workflow 

Each of the Jenkins "build steps" can also be used as steps in a Jenkins Workflow plugin Groovy script, as they implement `jenkins.tasks.SimpleBuildStep` and `java.io.Serializable`.  From your Groovy script, instantiate the associated Java object, and then leverage the workflow plugin `step` keyword to call out to the object with the necessary workflow contexts.  [Here](https://github.com/jenkinsci/workflow-plugin/blob/master/TUTORIAL.md) is a useful reference on constructing Jenkins Workflow Groovy scripts.

As a point of reference, here are the Java classes for each of the Jenkins "build steps":
1.  "Perform builds in OpenShift":  com.openshift.jenkins.plugins.pipeline.OpenShiftBuilder(String apiURL, String bldCfg, String namespace, String authToken, String verbose, String commitID, String buildName, String showBuildLogs)

2.  "Scale deployments in OpenShift":  com.openshift.jenkins.plugins.pipeline.OpenShiftScaler(String apiURL, String depCfg, String namespace, String replicaCount, String authToken, String verbose)

3.  "Trigger a deployment in OpenShift":  com.openshift.jenkins.plugins.pipeline.OpenShiftDeployer(String apiURL, String depCfg, String namespace, String authToken, String verbose)

4.  "Verify a service is up in OpenShift":  com.openshift.jenkins.plugins.pipeline.OpenShiftServiceVerifier(String apiURL, String svcName, String namespace, String authToken, String verbose)

5.  "Tag an image in OpenShift":  com.openshift.jenkins.plugins.pipeline.OpenShiftImageTagger(String apiURL, String testTag, String prodTag, String namespace, String authToken, String verbose)

6.  "Check deployment success in OpenShift":  com.openshift.jenkins.plugins.pipeline.OpenShiftDeploymentVerifier(String apiURL, String depCfg, String namespace, String replicaCount, String authToken, String verbose)

7.  "Get latest OpenShift build status":  com.openshift.jenkins.plugins.pipeline.OpenShiftBuildVerifier(String apiURL, String bldCfg, String namespace, String authToken, String verbose)

8.  "Create resource(s) in OpenShift":  com.openshift.jenkins.plugins.pipeline.OpenShiftCreator(String apiURL, String namespace, String authToken, String verbose, String jsonyaml)

## Common aspects across the REST based functions (build steps, SCM, post-build actions)

For all of these, with each required parameter, a default value is provided where it makes sense.  Optional parameters can be left blank.  And each parameter field has help text available via clicking the help icon located just right of the parameter input field.

Also, when processing any provided values for any of the parameters, the plugin will first see if that value, when used as as key, retrieves a non-null, non-empty value from the Jenkins build environment parameters.  If so, the plugin will substitute that value retrieved from the Jenkins build environment parameters in place of what was provided in the input field.

Here are a couple of screen shots to help illustrate.  First, the definition of the parameter:

<p align="center">
<img width="840" src="EnvVar-1.png"/>
</p>

Then how a build step could consume the parameter:

<p align="center">
<img width="840" src="EnvVar-2.png"/>
</p>

And then how you would run the job, specifying a valid value for the parameter:
<p align="center">
<img width="840" src="EnvVars-3.png"/>
</p>


Next, the bearer authentication token can be provided by the user via the following:
	- the field for the token in the specific build step
	- if a string parameter with the key of `AUTH_TOKEN` is set in the Jenkins Job panel, where the value is the token
	- if a global property with the key of `AUTH_TOKEN` is set in the `Manage Jenkins` panel, where the value is the token

Otherwise, the plugin will assume you are running off of the OpenShift Jenkins Docker image (http://github.com/openshift/jenkins), and will read in the token from a well known location in the image that allows authorized access to the OpenShift master associated with the running OpenShift Jenkins image.

The CA cert is currently pulled from a well known location ("/run/secrets/kubernetes.io/serviceaccount/ca.crt").

For "Monitor OpenShift ImageStreams", only specifying the token in the plugin configuration or leveraging the token embedded in the OpenShift Jenkins image is supported.
