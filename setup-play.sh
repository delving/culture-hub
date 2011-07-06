#!/bin/sh

echo ================================
echo Installing play in ../play-1.2.2
echo ================================
echo
echo

cd ..
wget http://download.playframework.org/releases/play-1.2.2.zip
unzip play-1.2.2.zip
cd play-1.2.2

echo
echo
echo ========================
echo Installing play-scala...
echo ========================
echo
echo

./play install scala-0.9.1

cd ..

echo =============================================================
echo Done! Add play-1.2.2 to your path in order to run play easily
echo =============================================================
