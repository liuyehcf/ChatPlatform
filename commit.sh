#!/bin/bash

description="version_"$(date +%Y)"."$(date +%m)"."$(date +%d)"--"$(date +%H:%M:%S)

git add .
git commit -m $description
git push origin master:master
