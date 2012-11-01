#!/bin/sh

echo Importing Geonames into Mongo...

gunzip geonames.bson.gz
mongorestore --db geonames geonames.bson
gzip geonames.bson
