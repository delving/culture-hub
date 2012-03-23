#!/bin/sh

echo
echo
echo ========================
echo Shutting down CultureHub
echo ========================
echo
echo
kill -9 `cat RUNNING_PID` && rm RUNNING_PID
