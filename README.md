### Backend part of the source code of the post [Fullstack Kafka](hhttps://medium.com/@ironmeiner/fullstack-kafka-e735054adcd6)

You can read [Fullstack Kafka](https://medium.com/@ironmeiner/fullstack-kafka-e735054adcd6) as a documentation for this repository, partiulary the section **Building the Microservices**.

You can see a video of the example application in action.

[![demo video](https://img.youtube.com/vi/g06MqWjJaCY/0.jpg)](https://www.youtube.com/watch?v=g06MqWjJaCY)


### Deployment guide

##### Prerequisites

You need to install Kafka Streaming Platform and Cassandra before starting this guide. Please read the section **Setting the infrastructrue** of the [Fullstack Kafka post](https://medium.com/@ironmeiner/fullstack-kafka-e735054adcd6) to setup **Kafka topics**, **Cassandra tables and materialized views**, and **Datastax Sink Connector**. 

##### Deployment

We recommend using **Docker** for deployment.

In this guide, we will put everything in one container. It is not recommended for production.

1 - clone the backend and frontend repositories

```sh
git clone git://github.com/acmoune/product-reviewer.git product-reviewer
git clone git://github.com/acmoune/product-reviewer-client.git product-reviewer-client
```

2 - Create the root folder for your docker image, and add a src folder inside.

```sh
mkdir /path/to/docker-image
mkdir /path/to/docker-image/src
```

3 - build the client, and copy it to the docker-image's src folder

```sh
cp -r product-reviewer-client /path/to/docker-image/src/
```

4 - Build each Microservice and copy it to the docker-image's src folder

```sh
cd product-reviewer
sbt
sbt:product-reviewer> security/stage
sbt:product-reviewer> reviews/stage
sbt:product-reviewer> statistics/stage
sbt:product-reviewer> webserver/universal:packageZipTarball
sbt:product-reviewer> exit

cp -r security/target/univrsal /path/to/docker-image/src/security
cp -r reviews/target/univrsal /path/to/docker-image/src/reviews
cp -r statistics/target/univrsal /path/to/docker-image/src/statistics
cp webserver/target/universal/webserver-1.0.0.tgz /path/to/docker-image/src/
```

Then, from the `/path/to/docker-image/src/` folder, extract the folder from the archive `webserver-1.0.0.tgz` and rename it to `webserver`

By the end of this step, the `/path/to/docker-image/src/` folder most container five directories: `product-reviewer-client`, `security`, `reviews`, `statistics`, and `webserver`.

5 - Create a Dockerfile in the `/path/to/docker-image/` folder and put this content inside:

```sh
FROM ubuntu:bionic
LABEL maintainer="ac.moune@gmail.com"

# Init
# Use bash as replacement for sh
RUN rm /bin/sh && ln -s /bin/bash /bin/sh
RUN apt-get update && \
    apt-get install -y apt-utils curl software-properties-common build-essential

# Install nodejs and npm
RUN curl -sL https://deb.nodesource.com/setup_12.x | bash - && \
    apt-get install -y nodejs && \
    echo NodeJS version && node -v && \
    echo Npm version && npm -v
    
# Install Java
RUN mkdir -p /var/cache/oracle-jdk11-installer-local/
COPY java/jdk-11.0.7_linux-x64_bin.tar.gz /var/cache/oracle-jdk11-installer-local/
RUN add-apt-repository -y ppa:linuxuprising/java && \
    apt-get update && \
    echo oracle-java11-installer shared/accepted-oracle-license-v1-2 select true | /usr/bin/debconf-set-selections && \
    apt-get install -y oracle-java11-installer-local && \
    apt-get install -y oracle-java11-set-default-local && java -version

RUN source /etc/profile
COPY src /src/
WORKDIR /src/product-reviewer-client
RUN npm install
RUN npm build
EXPOSE 9000 3000
WORKDIR /

# Kafka
ENV KAFKA_BOOTSTRAP_SERVERS 172.17.0.1:9092
ENV SCHEMA_REGISTRY_URL http://172.17.0.1:8081

# Topics
# ENV SECURITY_EVENTSOURCE_TOPIC_NAME security-eventsource
# ENV REVIEWS_EVENTSOURCE_TOPIC_NAME reviews-eventsource
# ENV REVIEWS_CHANGELOG_TOPIC_NAME reviews-changelog
# ENV TASKS_CHANGELOG_TOPIC_NAME tasks-changelog
# ENV STATS_CHANGELOG_TOPIC_NAME tasks-changelog

# Cassandra
ENV CONTACT_POINTS 172.17.0.1:9042
# ENV LOAD_BALANCING_LOCAL_DATACENTER datacenter1
# ENV SESSION_KEYSPACE proreviewer

ENTRYPOINT /src/security/stage/bin/security& /src/reviews/stage/bin/reviews& /src/statistics/stage/bin/statistics& /src/webserver/bin/webserver -Dhttp.port=9000& cd /src/product-reviewer-client && npm start& wait
```

You can adjust the **Env** variables to match your environment. You can see the default values on those commented.

6 - As you can see from the Dockerfile, you must create a java folder in `/path/to/docker-image/` and put JDK inside. Make sur you download the exact same version.

```
/path/to/docker-image/java/jdk-11.0.7_linux-x64_bin.tar.gz
```

By the end of this step, your `docker-image` folder most contain two directories: `java` and `src`, and the `Dockerfile`. 

7 - Build the docker image

```sh
cd /path/to/docker-image/
docker image build -t proreviewer:beta .
```

8 - Run the application

Again, make sure you went throug the section **Setting the infrastructrue** of the [Fullstack Kafka post](https://medium.com/@ironmeiner/fullstack-kafka-e735054adcd6) to setup **Kafka topics**, **Cassandra tables and materialized views**, and **Datastax Sink Connector** before running your container.

```sh
docker container run -d --name proreviewer -p 3000:3000 -p 9000:9000 proreviewer:beta
```

That is it! If everything went well and all the configurations are corrects, you should access the application on **http://localhost:3000**

Do not hesitate to open an issue if needed.

Good luck!

