# Builds ping-pong.jar
FROM maven

# installs dependencies
ADD pom.xml .
RUN mvn package -DskipTests

# will cache here if src is changed
ADD src src
RUN mvn package -DskipTests

# Install netcat for script that waits until KVStore initialized
# RUN apt-get update && apt-get -y install netcat && apt-get clean
ADD kv-4.3.11/* ./

COPY start.sh .

EXPOSE 8080
EXPOSE 5000

# Set start.sh as executable
RUN chmod +x start.sh

# Executable
CMD ["./start.sh"]
