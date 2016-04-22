# OpenShift V3 Plugin for Jenkins
This project provides a series Jenkins plugin implementations that operate on [Kubernetes based OpenShift](https://docs.openshift.org/latest/welcome/index.html).  In summary
they are a series of REST clients that interface with the OpenShift server via the [exposed API](https://docs.openshift.org/latest/rest_api/overview.html).
They minimally mimic the REST flows of common uses of the `oc` [CLI command](https://docs.openshift.org/latest/cli_reference/basic_cli_operations.html), but in several
instances additional REST flows have been added to provide validation of the operations being performed.

Their ultimate intent is to provide easy to use building blocks that simplify the construction of the projects, workflows, and pipelines in Jenkins that operate against an OpenShift deployments.

NOTE:  This plugin requires JDK 1.8, based on its maven dependency openshift-restclient-java.

This document represents the most technical, detailed description of this plugin.  For reference, the associated wiki page for this plugin on the Jenkins site is [here](https://wiki.jenkins-ci.org/display/JENKINS/OpenShift+Pipeline+Plugin).
The most important piece of information on that page will be the indication of the latest officially released version of this plugin.

## Jenkins "build steps"

A series of Jenkins "build step" implementations are provided, which you can select from the `Add build step` pull down available on any project's configure page:

1. "Trigger OpenShift Build": performs the equivalent of an `oc start-build` command invocation, where the build logs can be echoed to the Jenkins plugin screen output in real time; in addition to confirming whether the build succeeded or not, this build step can optionally look to see if any deployment configs have image change triggers for the image produced by the build config; if any such deployment configs are found, those deployments will be analyzed to see if they were triggered by an image change, comparing the image used by the currently running replication controller with the image used by its immediate predecessor.

2. "Scale OpenShift Deployment":  performs the equivalent of an `oc scale` command invocation; the number of desired replicas is specified as a parameter to this build step, and the plugin can optionally confirm whether the desired number of replicas was launched in a timely manner; if no integer is provided, it will assume 0 replica pods are desired.

3. "Trigger OpenShift Deployment":  performs the equivalent of an `oc deploy --latest` command invocation; it will monitor the resulting ReplicationController's "openshift.io/deployment.phase" annotation to confirm success.

4. "Verify OpenShift Service": finds the ip and port for the specified OpenShift service, and attempts to make a HTTP connection to that ip/port combination to confirm the service is up.

5. "Tag OpenShift Image": performs the equivalent of an `oc tag` command invocation in order to manipulate tags for images in OpenShift ImageStream's.

6. "Verify OpenShift Deployment":  determines whether the expected set of DeploymentConfig's, ReplicationController's, and if desired active replicas are present based on prior use of either the "Scale OpenShift Deployment" (2) or "Trigger OpenShift Deployment" (3) steps; its activities specifically include:

   - it first confirms the specified deployment config exists.
   - it then gets the list of all replication controllers for that DC, and determines which replication controller is the latest generation/incarnation of the deployment.
   - and then sees for the latest replication controller if a) the deployment phase annotation "openshift.io/deployment.phase" is marked as "Complete" within a (configurable) time interval, and then optionally b) if within a configurable time the current replica count is at least equal to the desired replica count.
   - NOTE: success with older incarnations of the replication controllers for a deployment is not sufficient for this Build Step; the state of the latest generation is what is verified.
   - NOTE: overriding of timeouts is detailed below.


7. "Verify OpenShift Build":  performs the equivalent of an 'oc get builds` command invocation for the provided build config key provided; once the list of builds are obtained, the state of the latest build is inspected to see if it has completed successfully within a reasonable time period; it will also employ the same deployment triggering on image change verification done in the "Trigger OpenShift Build" build step; this build step allows for monitoring of builds either generated internally or externally from the Jenkins Project configuration.  NOTE: success or failure of older builds has no bearing; only the state of the latest build is examined.

8. "Create OpenShift Resource(s)":  performs the equivalent of an `oc create` command invocation; this build step takes in the provided JSON or YAML text, and if it conforms to OpenShift schema, creates whichever OpenShift resources are specified.

9. "Delete OpenShift Resource(s)...":  performs the equivalent of an `oc delete` command invocation; there are 3 versions of this build step; one takes in provided JSON or YAML text, and if it conforms to OpenShift schema, deletes whichever OpenShift resources are specified; the next form takes in comma delimited lists of types and keys, and deletes the corresponding entries; the last form takes in a comma separated list of types, along with comma separated lists of keys and values that might appear as labels on the API resources, and then for each of the types, deletes any objects that have labels that match the key/value pair(s) specified. 

## Jenkins "Source Code Management (SCM)"

An implementation of the Jenkins SCM extension point is also provided that takes advantage of Jenkins' built in polling and version management capabilities, but within the context of OpenShift Image Streams (we have taken the liberty of broadening the scope of what is considered "source"):

1. "OpenShift ImageStreams": the aforementioned baked-in polling mechanism provided by Jenkins is leveraged, exposing the common semantics between OpenShift ImageStreams (which are abstractions of Docker repositories) and SCMs; image IDs are maintained and treated like commit IDs for the requisite artifacts (images under the ImageStream instead of programmatic source files); when the image IDs for specific tags provided change (as reflected by updated "commit IDs" reported to Jenkins through the SCM plugin contract), Jenkins will initiate a build for the Project configuration in question; note, there are no "extractions" of any sort which leverage the workspaces provided by Jenkins to the SCMs.  It is expected that the build steps in the associated project configuration will initiate any OpenShift related activities that were dependent on the ImageStream resource being monitored.

## Jenkins "post-build actions"

A few Jenkins "post-build action" implementations are also provided, which you can select from the `Add post-build action` pull down available on any project's configure page:

1. "Cancel OpenShift Builds":  this action is intended to provide cleanup for a Jenkins project which failed because a build is hung (instead of terminating with a failure code); this step will allow you to perform the equivalent of a `oc cancel-build` for any builds found for the provided build config which are not previously terminated (either successfully or unsuccessfully) or cancelled; those builds will be cancelled.

2. "Cancel OpenShift Deployment": this action is intended to cleanup any OpenShift deployments which still in-progress after the Build completes;  this step will allow you to perform the equivalent of a `oc deploy --cancel` for the provided deployment config.

## Jenkins Workflow 

Each of the Jenkins "build steps" can also be used as steps in a Jenkins Workflow plugin Groovy script, as they implement `jenkins.tasks.SimpleBuildStep` and `java.io.Serializable`.  From your Groovy script, instantiate the associated Java object, and then leverage the workflow plugin `step` keyword to call out to the object with the necessary workflow contexts.  [Here](https://github.com/jenkinsci/workflow-plugin/blob/master/TUTORIAL.md) is a useful reference on constructing Jenkins Workflow Groovy scripts.

As a point of reference, here are the Java classes for each of the Jenkins "build steps":
1.  "Trigger OpenShift Build":  com.openshift.jenkins.plugins.pipeline.OpenShiftBuilder

2.  "Scale OpenShift Deployment":  com.openshift.jenkins.plugins.pipeline.OpenShiftScaler

3.  "Trigger OpenShift Deployment":  com.openshift.jenkins.plugins.pipeline.OpenShiftDeployer

4.  "Verify OpenShift Service":  com.openshift.jenkins.plugins.pipeline.OpenShiftServiceVerifier

5.  "Tag OpenShift Image":  com.openshift.jenkins.plugins.pipeline.OpenShiftImageTagger

6.  "Verify OpenShift Deployment":  com.openshift.jenkins.plugins.pipeline.OpenShiftDeploymentVerifier

7.  "Verify OpenShift Build":  com.openshift.jenkins.plugins.pipeline.OpenShiftBuildVerifier

8.  "Create OpenShift Resource(s)":  com.openshift.jenkins.plugins.pipeline.OpenShiftCreator

9.  "Delete OpenShift Resource(s)...":   com.openshift.jenkins.plugins.pipeline.OpenShiftDeleterJsonYaml, com.openshift.jenkins.plugins.pipeline.OpenShiftDeleterList, com.openshift.jenkins.plugins.pipeline.OpenShiftDeleterLabels

## Common aspects across the REST based functions (build steps, SCM, post-build actions)

### Authorization

In order for this plugin to operate against OpenShift resources, the OpenShift service account that is used will need to have the necessary role and permissions.  In particular, you will need to add the `edit` role to the `default` service account in the project those resources are located in.  

For example, in the case of the `test` project, the specific command will be:  `oc policy add-role-to-user edit system:serviceaccount:test:default`

If that project is also where Jenkins is running out of, and hence you are using the OpenShift Jenkins image (https://github.com/openshift/jenkins), then the bearer authorization token associated with that service account is already made available to the plugin (mounted into the container Jenkins is running in at "/run/secrets/kubernetes.io/serviceaccount/token").

Next, in the case of "Tag OpenShift Image", you could potentially wish to access two projects (the source and destination image streams can be in different projects with `oc tag`).  If so, the service account and associated token need access to both projects.  This can 
be done in one of two ways:

- Grant permission to the service account token from the project Jenkins is running in (i.e. the default token just described) to the second project; for example, if Jenkins is running in the project `test`, and you want to tag an ImageStream running in the project `test2`, run the command `oc policy add-role-to-user edit system:serviceaccount:test:default -n test2`
- Or supply the token for the `default` service account for project `test2` as the destination token in the step's UI, and give that token permission to access project `test` by running the command `oc policy add-role-to-user edit system:serviceaccount:test2:default -n test`

Finally, outside of the default token mounted into the OpenShift Jenkins image container, the token can be provided by the user via the following:

- the field for the token in the specific build step
- if a string parameter with the key of `AUTH_TOKEN` is set in the Jenkins Project panel, where the value is the token
- if a global property with the key of `AUTH_TOKEN` is set in the `Manage Jenkins` panel, where the value is the token

### Certificates and Encrpytion

For the certificate, when running in the OpenShift Jenkins image, the CA certificate by default is pulled from the well known location ("/run/secrets/kubernetes.io/serviceaccount/ca.crt") where OpenShift mounts it, and then is stored into the Java KeyStore and X.509 TrustManager for subsequent verification against the OpenShift server on all subsequent interactions.  If you wish to override the certificate used:

- For all steps of a given project, set a build parameter (again, of type `Text Parameter`)  named `CA_CERT` to the string needed to construct the certificate.
- Since `Text Parameter` input fields are not available with the global key/value properties, the plug-in does not support defining certificates via a `CA_CERT` property across Jenkins projects.

If you want to skip TLS verification and allow for untrusted certificates, set the named parameter `SKIP_TLS` to any value.  Since this can be done with a Jenkins `String Parameter`, you can use this at either the global or project level. 


### Providing parameter values

For each of the steps and actions provided, with each required parameter, a default value will be inferred whenever possible if the field is left blank (specifics on this are further down in this section).  Optional parameters can be left blank as well.  And each parameter field has help text available via clicking the help icon located just right of the parameter input field.

Also, when processing any provided values for any of the parameters, the plugin will first see if that value, when used as as key, retrieves a non-null, non-empty value from the Jenkins build environment parameters.  If so, the plugin will substitute that value retrieved from the Jenkins build environment parameters in place of what was provided in the input field.

Here are a couple of screen shots to help illustrate.  First, the definition of the parameter:

<p align="center">
<img width="840" src="EnvVar-1.png"/>
</p>

Then how a build step could consume the parameter:

<p align="center">
<img width="840" src="EnvVar-2.png"/>
</p>

And then how you would run the project, specifying a valid value for the parameter:
<p align="center">
<img width="840" src="EnvVars-3.png"/>
</p>

An examination of the configuration fields for each type of the build steps will quickly discover that there are some common configuration parameters across the build steps.  These common parameters included:
- the URL of the OpenShift API Endpoint
- the name of the OpenShift project
- whether to turn on verbose logging (off by default)
- the bearer authentication token

For the API Endpoint and Project name, any value specified for the specific step takes precedence.  That value can be either the actual value needed to connect, or as articulated above, a Jenkins build environment parameter.  But if no value is set, then OpenShift Pipeline plugin will inspect the environment variable available to the OpenShift Jenkins image (where the name of the variable for the endpoint is `KUBERNETES_SERVICE_HOST` and for the project is `PROJECT_NAME`).  In the case of the API Endpoint, if a non-null, non-empty value is still not obtained after checking the environment variable, the plugin will try to communicate with "https://openshift.default.svc.cluster.local".  Note that with the "OpenShift ImageStreams" SCM plug-in, these environment variables will not be available when running in the background polling mode (though they are available when that step runs as part of an explicit project build).

Also, since many are possibly entrenched in specifying environment variables with a preceding `$`, the plug-ins will accept either form.  For example, if the environment variable `GIT_COMMIT` is set, you can specify either `GIT_COMMIT` or `$GIT_COMMIT`.

### Communication Timeouts 

The default timeouts for the various interactions with the OpenShift API endpoint are also configurable for those steps that have to wait on results.  Overriding the timeouts are currently done globally across all instances of a given build step or post-build step.  Go to the "Configure System" panel under "Manage Jenkins" of the Jenkins UI (i.e. http://<host:port>/configure), and then change the "Wait interval" for the item of interest.  Similarly, the OpenShift Service Verification has a retry count for attempts to contact the OpenShift Service successfully.

## Build and Install

Like the Jenkins project itself, this project is a maven based project.  To build this project, after you install maven and java 1.8 or later, and cd to this projects root directory (where the `pom.xml` file is located), run `mvn clean package`.  If built successfully, and `openshift-pipeline.hpi` file will reside in the `target` subdirectory.

Aside from building the plugin locally, there are a few other ways to obtain built version of the plugin:

1.  The Centos and RHEL versions of the OpenShift Jenkins Docker Image, starting officially with V3.2 of OpenShift, will have the plugin installed.  See the [Jenkins Docker Image repository](https://github.com/openshift/jenkins) for details. 

2.  As noted earlier, [wiki page](https://wiki.jenkins-ci.org/display/JENKINS/OpenShift+Pipeline+Plugin) has a link to the latest official version of the plugin.

3.  You could also go to the [openshift-pipeline page](https://updates.jenkins-ci.org/download/plugins/openshift-pipeline/) at the Jenkins Update Center to view the complete list of released plugins.

4.  The Jenkins server used for OpenShift development has a [job](https://ci.openshift.redhat.com/jenkins/job/openshift-pipeline-plugin/) that build the latest commit to the master repo.  The resulting `openshift-pipeline.hpi` file be a saved artifact from that job.

5.  A RHEL RPM is also available as of V3.2 of OpenShift.

Unless you are using a OpenShift Jenkins Docker Image with the plugin preinstalled, follow the Jenkins instructions for installing a plugin either by supplying the `openshift-pipeline.hpi` file, or by pulling it down from the Jenkins Update Center. 
