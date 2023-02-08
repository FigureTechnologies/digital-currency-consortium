FROM us-east1-docker.pkg.dev/figure-shared-services/figure-shared-services-docker/java-jre:11-curl-latest

ADD build/libs/digital-currency-consortium-1.0-SNAPSHOT.jar /service-dcc.jar

ADD docker/configure /configure

EXPOSE 8080

ENTRYPOINT ["/configure"]
CMD ["java", "$JAVA_OPTS", "-server", "-jar", "/service-dcc.jar"]
