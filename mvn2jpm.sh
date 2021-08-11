#!/usr/bin/bash

mvn package

java -jar target/mvn2jpm-1.0.0-SNAPSHOT.jar $@
