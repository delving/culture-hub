#!/bin/sh

echo
echo
echo =============================================
echo Redeploying the CultureHub in production mode
echo =============================================
echo
echo
export _JAVA_OPTIONS="-Dconfig.file=`pwd`/conf/production.conf -Xms256M -Xmx1024M"
git pull
ant downloadSipCreator
../play-2.0/play compile
kill -9 `cat RUNNING_PID` && rm RUNNING_PID
../play-2.0/play start &
