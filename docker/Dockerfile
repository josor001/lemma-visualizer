FROM maven:3.6.3-openjdk-11-slim
ARG LEMMA_VERSION

COPY build/libs/de.fhdo.lemma.model_processing.visualizer-$LEMMA_VERSION-standalone.jar \
    /home/visualizer.jar

ENTRYPOINT ["java", "-jar", "/home/visualizer.jar"]