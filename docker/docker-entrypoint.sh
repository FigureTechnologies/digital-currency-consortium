#!/bin/bash
set -e

if [[ -z "${CERT_PATH}" || -z "${CERT_ALIAS}" ]]; then
	echo "\"CERT_PATH\" and \"CERT_ALIAS\" are not set..skipping keystore loading"
else
	keytool -import -alias ${CERT_ALIAS} -keystore ${JAVA_HOME}/lib/security/cacerts -trustcacerts -file ${CERT_PATH} --storepass changeit -noprompt
fi

if [[ -z "${SSL_SECLEVEL}" ]]; then
	echo "\"SSL_SECLEVEL\" is not set"
else
	echo "Setting SSL CipherString = DEFAULT@SECLEVEL=${SSL_SECLEVEL}"
	sed -i 's/CipherString = DEFAULT@SECLEVEL=./CipherString = DEFAULT@SECLEVEL='$SSL_SECLEVEL'/g' /etc/ssl/openssl.cnf
fi

java -jar $1
