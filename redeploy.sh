#!/bin/sh

echo
echo
echo =============================================
echo Redeploying the CultureHub in production mode
echo =============================================
echo
echo
export _JAVA_OPTIONS="-Dconfig.file=`pwd`/conf/production.conf -Dlogger.resource=prod-logger.xml -Xms256M -Xmx1024M"
kill -9 `cat RUNNING_PID` && rm RUNNING_PID
git pull
ant downloadSipCreator
../play-2.0/play clean
../play-2.0/play compile
../play-2.0/play start &

echo
echo "Starting Play. Please wait..."
echo

while ! (tail logs/application.log | grep -qi Listening); do
  sleep 1
done

echo
echo Killing SBT
echo
ps -ef | grep "/play-2.0/framework/sbt/sbt-launch.jar start" | grep -v grep | awk '{print $2}' | xargs kill -9

echo
echo "Startup done. Check logs/application.log to see startup progress"
echo
