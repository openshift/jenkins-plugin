# openshift-jenkins-buildutils
As set of Jenkins plugins that operate on OpenShift.

The set includes these Jenkins "build steps", selectable from the `Add build step` pull down available on any project's configure page:

1) "Perform builds in OpenShift": performs the equivalent of an `oc start-build` command invocation, where the build logs are echoed to the Jenkins plugin in real time; in addition to confirming whether the build succeeded or not, the plugin will look to see if a deployment config specifying a non-zero replica count existed, and if so, confirm whether the deployment occurred

2) "Scale deployments in OpenShift":  performs the equivalent of an `oc scale` command invocation; the number of replicas is specified as a parameter to this build step, and the plugin will confirm whether the desired number of replicas was launched in a timely manner

3) "Start a deployment in OpenShift":  performs the equivalent of an `oc deploy` command invocation; the plugin will confirm whether the desired number of replicas was launched in a timely manner

4) "Verify a service is up in OpenShift": finds the ip and port for the specified OpenShift service, and attempts to make a HTTP connection to that ip/port combination to confirm the service is up

5) "Tag an image in OpenShift": performs the equivalent of an `oc tag` command invocation in order to manipulate tags for images in OpenShift ImageStream's

6) "Verify deployments in OpenShift":  determines whether the expected set of DeploymentConfig's, ReplicationController's, and active replicas are present based on prior use of the scaler (2) and deployer (3) steps

7) "Get latest OpenShift build status":  performs the equivalent of an 'oc get builds` command invocation for the provided buildConfig key provided; once the list of builds are obtained, the state of the latest build is inspected for up to a minute to see if it has completed successfully; this build step is intended to allow for monitoring of builds either generated internally or externally from the Jenkins Job configuration housing the build step

8) "Monitor OpenShift ImageStreams": rather than a Build step extension plugin, this is an extension of the Jenkis SCM plugin, where this baked-in polling mechanism provided by Jenkins is leveraged by exposing some of the common semantics between OpenShift ImageStreams (which are abstractions of Docker repositories) and SCMs - versions / commit IDs of related artifacts (images vs. programmatics files); when the specific tags configured changes (as reflected by updated commit IDs) are reported to Jenkins through the SCM plugin contract, Jenkins will initiate a build for the Job configuration in question. Note, there are no extractions of source code to workspaces provided by Jenkins to the SCMs.  It is expected that the build steps in the associated job configuration will initiate any OpenShift related activities that were dependent on the ImageStream resource being monitored.

For each required parameter, a default value is provided.  Optional parameters can be left blank.  And each parameter field has help text available via clicking the help icon located just right of the parameter input field.

For each of the plugins, if an auth token is not specified, the plugin will pull the auth token from the known file location of the OpenShift Jenkins Docker image (http://github.com/openshift/jenkins), which allows authorized access to the OpenShift master associated with the running OpenShift Jenkins image.
