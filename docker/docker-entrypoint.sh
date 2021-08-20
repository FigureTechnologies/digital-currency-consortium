#!/bin/bash
set -e

if [[ -z "${CERT_PATH}" || -z "${CERT_ALIAS}" ]]; then
	echo "\"CERT_PATH\" and \"CERT_ALIAS\" are not set..skipping keystore loading"
else
	keytool -import -alias ${CERT_ALIAS} -keystore ${JAVA_HOME}/lib/security/cacerts -trustcacerts -file ${CERT_PATH} --storepass changeit -noprompt
fi

java -jar $1
