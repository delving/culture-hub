#!/bin/sh

#echo ================================
#echo Installing play in ../play-1.2.4
#echo ================================
#echo
#echo

cd ..
wget http://download.playframework.org/releases/play-1.2.4.zip
unzip play-1.2.4.zip
mv play-1.2.4 play

echo
echo
echo ================
echo Patching play...
echo ================
echo
echo

cd play

git apply ../culture-hub/error-display-patch.patch

cd ..

## Symbolic link so that IDEA can find play.jar

cd play/framework
ant
ln -s play-1.2.*.jar play.jar
cd ../../culture-hub

echo
echo
echo ======================
echo Resolving dependencies
echo ======================
echo
echo

../play/play dependencies

echo =========================================
echo Done! Run the application with "../play/play run" or when in your path just "play run"
echo =========================================

