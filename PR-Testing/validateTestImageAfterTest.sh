#!/bin/bash
# assumes that it is run from the PR-Testing directory from a clone of https://github.com/openshift/jenkins-plugin
# and that the image id for openshift/jenkins-plugin-snapshot-test before the OpenShift extended test is run is 
# stored in the file IMAGE_ID_BEFORE and that the image id is captured again and stored in IMAGE_ID_AFTER;
# this script then compares the contents and reacts/informs accordingly;  basically, creating this script 
# is a workaround for the shortcomings of vagrant ssh -c 

diff IMAGE_ID_BEFORE IMAGE_ID_AFTER

if [ $? -eq 0 ]; 
then 
	echo The test image remained unchanged during the test
	exit 0
else 
	echo The test image was changed, check for openshift/jenkins-plugin-snapshot-test on Dockerhub
	exit 1 
fi
