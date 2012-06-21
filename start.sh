#!/bin/sh

echo
echo
echo ==========================================
echo Starting the CultureHub in production mode
echo ==========================================
echo
echo
. ./params.sh
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
