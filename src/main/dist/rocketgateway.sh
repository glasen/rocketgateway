#!/bin/bash
CLASSPATH=
for i in ./lib/*.jar
do
  CLASSPATH=${CLASSPATH}:${i}
done

java -Xmx1g -cp "./RocketGateway.jar:${CLASSPATH}" rocketgateway.RocketGateway "$@"
