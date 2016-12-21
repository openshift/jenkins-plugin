# Process for cutting release of this plugin

As noted in this repository's README.md, the documentation and code at [https://github.com/openshift/jenkins-plugin](https://github.com/openshift/jenkins-plugin) always hosts the very latest version, including possibly pre-released versions that are still under test.
The associated repository under the JenkinsCI project, [https://github.com/jenkinsci/openshift-pipeline-plugin](https://github.com/jenkinsci/openshift-pipeline-plugin), is only updated as part of cutting 
official releases of this plugin.

To cut a new release of this plugin, first perform a `git clone` of [https://github.com/jenkinsci/openshift-pipeline-plugin](https://github.com/jenkinsci/openshift-pipeline-plugin), 

## Set up local repository to cut release:

1. From the parent directory you've chosen for you local repository, run `git clone git@github.com:jenkinsci/openshift-pipeline-plugin.git`
1. Change directories into `openshift-pipeline-plugin`, and run `git remote add upstream git://github.com/openshift/jenkins-plugin`
1. Then pull the latest changes from [https://github.com/openshift/jenkins-plugin](https://github.com/openshift/jenkins-plugin) with the following:

	$ git checkout master
	
	$ git fetch upstream
	
	$ git fetch upstream --tags
	
	$ git rebase upstream/master
	
	$ git push origin master
	
	$ git push origin --tags


## Submit the new release to the Jenkins organization

Assumptions: your Git ID has push access to the two repositories for this plugin; your Jenkins ID (https://wiki.jenkins-ci.org/display/JENKINS/User+Account+on+Jenkins) is listed in https://github.com/jenkins-infra/repository-permissions-updater/blob/master/permissions/plugin-openshift-pipeline.yml.  Given these assumptions:

1. Then run `mvn release:prepare release:perform`
1. You'll minimally be prompted for the `release version`, `release tag`, and the `new development version`.  Default choices will be provided for each, and the defaults are typically acceptable, so you can just hit the enter key for all three prompts.  As an example, if we are currently at v1.0.36, it will provide 1.0.37 for the new `release version` and `release tag`.  For the `new development version` it will provide 1.0.38-SNAPSHOT, which is again acceptable.  The only time you *might* have to override the default provided is if we currently depend on a SNAPSHOT version of openshift-restclient-java (e.g. `5.3.0-SNAPSHOT`).  This occurs when we add new features to openshift-restclient-java, but the eclipse team has not cut a new, official release (which will typically look like `5.3.0-FINAL`).  If we are in such a mode, you'll get prompted about moving off the SNAPSHOT version (the default provided would be `5.3.0`), but override this (i.e. type in `5.3.0-SNAPSHOT`).	
1. The `mvn release:prepare release:perform` command will take a few minutes to build the plugin and go through various verifications, followed by a push of the built artifacts up to Jenkins.  This typically works without further involvement but has failed for various reasons in the past.  If so, to retry with the same release version, you'll need to call `git reset` to back of the two commits created as part of publishing the release, as well as use `git tag` to delete both the local and remote version of the corresponding tag.  After deleting the commits and tags, use `git push -f` to update the commits at [https://github.com/jenkinsci/openshift-pipeline-plugin](https://github.com/jenkinsci/openshift-pipeline-plugin). Address whatever issues you have (you might have to solicit help on the Jenkins developer group: https://groups.google.com/forum/#!forum/jenkinsci-dev) and try again.
1. Run `git push https://github.com/openshift/jenkins-plugin.git master` to upload the 2 commits created for cutting the new release to our upstream, development repository, and get the two repositories back in sync.
1. Monitor https://updates.jenkins-ci.org/download/plugins/openshift-pipeline/ for the existence of the new version of the plugin.  Warning: the link for the new version will show up, but does not mean the `openshift-pipeline.hpi` file is available yet.  Click the link to confirm you can download the new version of the plugin.  When you can download the latest `openshift-pipeline.hpi` file, the process is complete, and the new release is available.

