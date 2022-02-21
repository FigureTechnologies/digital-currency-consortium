#!/bin/bash

set -ex

gradle clean build -x test

export CODE_VERSION="$(git rev-parse HEAD)-$(date +'%Y%m%d.%H%M%S')"
export GithubBranch=$(git branch|grep '*'|cut -d' ' -f2)

IMAGE_NAME="hosted-node-dcc"
TAG="$GithubBranch_$CODE_VERSION"
DOCKER_USER="fin3technologies"

docker build -f docker/Dockerfile-service --platform linux/x86_64 -t ${DOCKER_USER}/${IMAGE_NAME}:${TAG} .
docker tag ${DOCKER_USER}/${IMAGE_NAME}:${TAG} ${DOCKER_USER}/${IMAGE_NAME}:latest
docker push ${DOCKER_USER}/${IMAGE_NAME}:${TAG}
# docker tag ${DOCKER_USER}/${IMAGE_NAME}:latest ${DOCKER_USER}/${IMAGE_NAME}:${TAG}
docker push ${DOCKER_USER}/${IMAGE_NAME}:latest

set +ex
