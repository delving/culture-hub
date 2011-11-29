#!/bin/sh

echo Importing Geonames into Mongo...

mongorestore --db geonames test/geonames.bson