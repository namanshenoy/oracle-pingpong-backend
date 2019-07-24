java -jar kvstore.jar kvlite -secure-config disable &
echo "Start"
echo "Waiting for NoSQL to launch on 5000..."
sleep 20s
echo "Sleep Done!"


# while ! nc -z $(hostname) 5000; do   
#   sleep 0.1 # wait for 1/10 of the second before check again
# done
# until nc -z 5000; do sleep 1s; done
# until echo "Initializing: " | grep -m 1 "Created new kvlite store"; do : ; done

echo "NoSQL launched"
java -jar target/ping-pong.jar