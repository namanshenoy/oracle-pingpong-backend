FROM oracle/nosql

COPY . .

ENV VERSION="4.3.11" \
    KVHOME=/ \
    PACKAGE="kv-ce" \
    EXTENSION="zip" \
    BASE_URL="http://download.oracle.com/otn-pub/otn_software/nosql-database/" \
    _JAVA_OPTIONS="-Djava.security.egd=file:/dev/./urandom"


RUN nohup java -jar lib/kvstore.jar kvlite -secure-config disable -root /kvroot &
RUN  java -jar lib/kvstore.jar ping -host kvlite -port 5000
# CMD ["java", "-jar", "lib/kvstore.jar", "kvlite", "-secure-config", "disable", "-root", "/kvroot"]