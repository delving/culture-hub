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
kill -9 `cat RUNNING_PID` && rm RUNNING_PID
git pull
ant downloadSipCreator
../play-2.0/play clean
../play-2.0/play compile
../play-2.0/play start & $!>SBT_PID
while ! (tail $LOGFILE | grep -qi Listening); do
  sleep 1
done
kill -9 `cat SBT_PID` && rm SBT_PID
