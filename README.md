# Internal Leaderboard Application
Leaderboard application (backend) made with Helidon, using NoSQL, Docker and Kubernetes.

# Dependencies
Before starting, make sure you have the [requirements](https://helidon.io/docs/latest/#/about/03_prerequisites) of Helidon installed.

# Get Started
Terminal A: Clone the Project repository and change directory into the project:
```bash
git clone https://github.com/namanshenoy/oracle-pingpong-backend.git && cd oracle-pingpong-backend/
```
\
**On another terminal,** Terminal B: Download [Oracle NoSQL Database Community Edition](https://download.oracle.com/otn-pub/otn_software/nosql-database/kv-ce-18.1.19.zip), unpackage and change directories to where *kv* is stored. Run the store:
```bash
# inside kv-ce-x.y.z/kv-x.y.z/
cd lib && java -jar kvstore.jar kvlite -secure-config disable
```
\
Terminal A: Install dependencies and build project
```bash
mvn package && java -jar target/ping-pong.jar
# open browser and visit localhost:8080/
```

# Encountering Problems and Development Tips
Development:
* Make small commits on separate branches. Never push to master.
* Push in short increments.
* Keep pull requests small for manageable merges and reviews.

Solutions to common problems:
1. Make sure you have localhost port 8080 available for use.
2. You are not using VPN for mvn install.