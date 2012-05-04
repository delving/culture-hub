#!/bin/sh

echo
echo
echo ==========================================
echo Starting the CultureHub in production mode
echo ==========================================
echo
echo
export _JAVA_OPTIONS="-Dconfig.file=`pwd`/conf/production.conf -Dlogger.resource=`pwd`/prod-logger.xml -Xms256M -Xmx1024M"
../play-2.0/play start &

echo
echo "Starting Play. Please wait..."
echo

sleep 30
echo
echo Killing SBT
echo
ps -ef | grep "/play-2.0/framework/sbt/sbt-launch.jar start" | grep -v grep | awk '{print $2}' | xargs kill -9

echo
echo "Script done, but server likely not up yet. Check logs/application.log to see startup progress"
echo
