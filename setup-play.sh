#!/bin/sh

## uncomment the lines below to install the default play version. For now we need a patched version

#echo ================================
#echo Installing play in ../play-1.2.2
#echo ================================
#echo
#echo

#cd ..
#wget http://download.playframework.org/releases/play-1.2.2.zip
#unzip play-1.2.2.zip
#cd play-1.2.2

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

# http://play.lighthouseapp.com/projects/57987/tickets/970-binder-does-not-pass-suffix-to-custom-typebinder-s-when-binding-a-bean
git apply ../play-services/binder-patch.patch

# http://play.lighthouseapp.com/projects/74274/tickets/32-all-case-classes-get-an-additional-default-empty-constructor-preventing-json-deserializer-from-working
git apply ../play-services/constructor-patch.patch

git apply ../play-services/error-display-patch.patch

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
cd ../../play-services

echo
echo
echo ========================
echo Installing play-scala...
echo ========================
echo
echo

# until we have a new release we use the latest version
# ../play/play install scala-0.9.1

mkdir modules
cd modules
git clone git://github.com/playframework/play-scala.git --depth 1
cd play-scala
ant -Dplay.path=../../../play

echo
echo
echo ======================
echo Resolving dependencies
echo ======================
echo
echo

../play/play dependencies --sync

echo =========================================
echo Done! Run the application with "../play/play run" or when in your path just "play run"
echo =========================================

