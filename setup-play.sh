#!/bin/sh

## uncomment the lines below to install the default play version. For now we need a patched version

#echo ================================
#echo Installing play in ../play-1.2.3
#echo ================================
#echo
#echo

#cd ..
#wget http://download.playframework.org/releases/play-1.2.3.zip
#unzip play-1.2.3.zip
#cd play-1.2.3

# Starting with the patched version

echo =========================
echo Cloning play into ../play
echo =========================
echo
echo

cd ..
git clone git://github.com/playframework/play.git --depth 1
cd play
git checkout 1.2.x
cd ..

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

echo
echo
echo =================
echo Compiling play...
echo =================
echo
echo

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

