#!/bin/sh

SCRIPT_DIRECTORY=`dirname "$0"`

for R in `cat repositories.txt`; do
  sh -x ${SCRIPT_DIRECTORY}/remove-linkage-monitor.sh $R
  if [ "$?" != 0 ]; then
    echo "Failed ${R}"
    exit 1
  fi
  echo "Succeeded ${R}"
  sleep 10
done