#!/bin/sh

# Setup script to perform various operations to prepare the development environment

echo =============================================
echo Copying application.conf from the template...
echo =============================================
echo
echo

cp conf/application.conf.template conf/application.conf

echo
echo
echo ======================
echo Resolving dependencies
echo ======================
echo
echo
play dependencies --sync

echo =========================================
echo Done! Run the application with "play run"
echo =========================================
