!/bin/bash

echo #####################################################################
echo Updating the server...
echo This will:
echo - fetch the latest version of master
echo - download the latest sip creator 
echo - clean and restart in prod mode
echo It does NOT apply play deps so you need to do this by hand if needed!
echo #####################################################################
echo

play stop
git pull
ant downloadSipCreator
play clean
play start --%prod

echo
echo ######################################################################
echo Update done. Check the logs via 'plog' to see if everything goes right
echo ######################################################################
