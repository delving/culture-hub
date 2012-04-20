#!/bin/sh

echo
echo
echo ==========================================
echo Starting the CultureHub in production mode
echo ==========================================
echo
echo
export _JAVA_OPTIONS="-Dconfig.file=`pwd`/conf/production.conf -Xms256M -Xmx1024M"
../play-2.0/play start &
