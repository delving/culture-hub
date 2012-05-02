#!/bin/sh

LOGFILE=logs/application.log

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
kill -9 `cat RUNNING_PID` && rm RUNNING_PID
../play-2.0/play update
../play-2.0/play start &
SBT_PID=$!
while ! (tail $LOGFILE | grep -qi Listening); do
  sleep 1
done
kill -9 $SBT_PID
