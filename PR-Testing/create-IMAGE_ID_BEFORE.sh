#!/bin/bash
# assumes that it is run from the PR-Testing directory from a clone of https://github.com/openshift/jenkins-plugin
# the result of this script is used by validateTestImageAfterTest.sh

docker images | grep jenkins-plugin-snapshot | awk '{print $3}' > IMAGE_ID_BEFORE
