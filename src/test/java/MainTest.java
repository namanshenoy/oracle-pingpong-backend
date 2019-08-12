import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.OutputStream;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.net.URL;
import java.net.HttpURLConnection;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;

import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class MainTest {

    private static WebServer webServer;
    private static final JsonReaderFactory JSON = Json.createReaderFactory(Collections.emptyMap());

    @BeforeAll
    public static void startTheServer() throws Exception {
        webServer = Main.startServer();

        
        long timeout = 2000; // 2 seconds should be enough to start the server
        long now = System.currentTimeMillis();

        while (!webServer.isRunning()) {
            Thread.sleep(100);
            if ((System.currentTimeMillis() - now) > timeout) {
                Assertions.fail("Failed to start webserver");
            }
        }
    }

    @AfterAll
    public static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    private JsonObject makeGetRequestGetJson(String endpoint) {
        try {
            HttpURLConnection conn = getURLConnection("GET", endpoint);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            JsonReader reader = JSON.createReader(conn.getInputStream());
            return reader.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;

    }

    private HttpURLConnection makeAddRequest(String name, String email, String password, String endpoint){
        try {
            HttpURLConnection conn = getURLConnection("POST", endpoint);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            String content = "{\"player\" : \"" + name + "\"" + ", \"email\" : \"" + email + "\"" + ", \"password\" : \"" + password + "\"}";
            System.out.println("making request with content " + content);
            OutputStream os = conn.getOutputStream();
            os.write(content.getBytes());
            os.close();
            System.out.println("Status " + conn.getResponseCode());
            return conn;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private HttpURLConnection makePostRequestGetConn(String key, String value, String endpoint) {
        try {
            HttpURLConnection conn = getURLConnection("POST", endpoint);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            String content = "{\"" + key + "\" : \"" + value + "\"}";
            System.out.println("making request with content " + content);
            OutputStream os = conn.getOutputStream();
            os.write(content.getBytes());
            os.close();
            System.out.println("Status " + conn.getResponseCode());
            return conn;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Test
    public void testAddPlayer() throws Exception {
        // add player bob
        HttpURLConnection conn = makeAddRequest("bob","bob@oracle.com", "bob", "/addPlayer");
        Assertions.assertEquals(202, conn.getResponseCode(), "HTTP response1");

        // check that bob is in players array
        conn = getURLConnection("GET", "/getPlayers");
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response2");
        JsonObject jsonObject = makeGetRequestGetJson("/getPlayers");
        assert (jsonObject.getJsonArray("players").size() == 1);

        conn = makeAddRequest("joe","joe@oracle.com", "joe", "/addPlayer");
        Assertions.assertEquals(202, conn.getResponseCode(), "HTTP response1");

        // check that bob and joe in array
        conn = getURLConnection("GET", "/getPlayers");
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response2");
        jsonObject = makeGetRequestGetJson("/getPlayers");
        assert (jsonObject.getJsonArray("players").size() == 2);
        assertEquals(2, jsonObject.getJsonArray("players").size());

        // error when player is already in array
        conn = makeAddRequest("joe","joe@oracle.com", "joe", "/addPlayer");
        Assertions.assertEquals(400, conn.getResponseCode(), "AddPlayer Error");

        
        conn = makePostRequestGetConn("email", "bob@oracle.com", "/deletePlayer");
        Assertions.assertEquals(202, conn.getResponseCode(), "AddPlayer Error");
        conn = makePostRequestGetConn("email", "joe@oracle.com", "/deletePlayer");
        Assertions.assertEquals(202, conn.getResponseCode(), "AddPlayer Error");
        assert(makeGetRequestGetJson("/getPlayers").getJsonArray("players").isEmpty());

        System.out.println("AddPlayer PASSED !!");


    }

    @Test
    public void testDeletePlayer() throws Exception {
        System.out.println("Starting Test Delete Player");
        // add bob/steve/joe
        HttpURLConnection conn = makeAddRequest("bob","bob@oracle.com", "bob", "/addPlayer");
        conn = makeAddRequest("steve","steve@oracle.com", "steve", "/addPlayer");
        conn = makeAddRequest("joe","joe@oracle.com", "joe", "/addPlayer");
        JsonObject jsonObject = makeGetRequestGetJson("/getPlayers");
        assert(jsonObject.getJsonArray("players").size() == 3);

        // delete steve
        conn = makePostRequestGetConn("email", "steve@oracle.com", "/deletePlayer");
        Assertions.assertEquals(202, conn.getResponseCode(), "HTTP deleteSteve");

        // assert only bob/joe
        jsonObject = makeGetRequestGetJson("/getPlayers");
        assert(jsonObject.getJsonArray("players").size() == 2);

        // add steve/tom
        conn = makeAddRequest("steve","steve@oracle.com", "steve", "/addPlayer");
        conn = makeAddRequest("tom","tom@oracle.com", "tom", "/addPlayer");
        jsonObject = makeGetRequestGetJson("/getPlayers");
        assert(jsonObject.getJsonArray("players").size() == 4);

        // [bob, joe, steve, tom] 
        // players in match, if challenger deleted, challenged available
        // joe challenges bob, delete joe, steve can challenge bob
        conn = makePostRequestGetConn("email", "joe@oracle.com", "/challengePlayer");
        assertEquals(400, makePostRequestGetConn("email", "steve@oracle.com", "/challengePlayer").getResponseCode());
        conn = makePostRequestGetConn("email", "joe@oracle.com", "/deletePlayer");
        jsonObject = makeGetRequestGetJson("/getPlayers");
        assert(jsonObject.getJsonArray("players").size() == 3);
        assertEquals(202, makePostRequestGetConn("email", "steve@oracle.com", "/challengePlayer").getResponseCode());
        assertEquals(400, makePostRequestGetConn("email", "tom@oracle.com", "/challengePlayer").getResponseCode());
        assertEquals(202, makePostRequestGetConn("email", "bob@oracle.com", "/concludeMatch").getResponseCode());

        // players in match, if challenged deleted, challenger available
        // [bob, steve, tom]
        // steve challenges bob, bob deleted, steve can be challenged
        conn = makePostRequestGetConn("email", "steve@oracle.com", "/challengePlayer");
        conn = makePostRequestGetConn("email", "bob@oracle.com", "/deletePlayer");
        jsonObject = makeGetRequestGetJson("/getPlayers");
        assert(jsonObject.getJsonArray("players").size() == 2);
        conn = makePostRequestGetConn("email", "tom@oracle.com", "/challengePlayer");

        
        makePostRequestGetConn("email", "steve@oracle.com", "/deletePlayer");
        makePostRequestGetConn("email", "tom@oracle.com", "/deletePlayer");
        assert(makeGetRequestGetJson("/getPlayers").getJsonArray("players").isEmpty());

        System.out.println("DeletePlayer PASSED !!");

    }

    @Test
    public void testConcludeMatch() throws Exception {
        // add bob/steve/joe/tom
        HttpURLConnection conn = makeAddRequest("bob","bob@oracle.com", "bob", "/addPlayer");
        conn = makeAddRequest("steve","steve@oracle.com", "steve", "/addPlayer");
        conn = makeAddRequest("joe","joe@oracle.com", "joe", "/addPlayer");
        conn = makeAddRequest("tom","tom@oracle.com", "tom", "/addPlayer");
        
        JsonObject jsonObject = makeGetRequestGetJson("/getPlayers");
        assert(jsonObject.getJsonArray("players").size() == 4);

        printPlayers(makeGetRequestGetJson("/getPlayers").getJsonArray("players"));

        // [bob, steve, joe, tom] -> [steve, bob, joe, tom]
        conn = makePostRequestGetConn("email", "steve@oracle.com", "/challengePlayer");
        assertEquals(202, conn.getResponseCode());
        conn = makePostRequestGetConn("email", "steve@oracle.com", "/concludeMatch");
        assertEquals(202, conn.getResponseCode());
        JsonArray playerArray = makeGetRequestGetJson("/getPlayers").getJsonArray("players");
        assert(playerArray.size() == 4);

        printPlayers(playerArray);

        // check correct order and that players freed up for new match
        String steve = playerArray.get(0).asJsonObject().getString("name");
        int steveRank = playerArray.get(0).asJsonObject().getInt("rank");
        String bob = playerArray.get(1).asJsonObject().getString("name");
        int bobRank = playerArray.get(1).asJsonObject().getInt("rank");
        String joe = playerArray.get(2).asJsonObject().getString("name");
        int joeRank = playerArray.get(2).asJsonObject().getInt("rank");
        String tom = playerArray.get(3).asJsonObject().getString("name");
        int tomRank = playerArray.get(3).asJsonObject().getInt("rank");
        
        assertEquals("steve", steve); assert(steveRank == 1);
        assertEquals("bob", bob); assert(bobRank == 2);
        assertEquals("joe", joe); assert (joeRank == 3);
        assertEquals("tom", tom); assert(tomRank == 4);
        boolean stevebool = playerArray.get(0).asJsonObject().getBoolean("inMatch");
        boolean bobbool = playerArray.get(1).asJsonObject().getBoolean("inMatch");
        boolean joebool = playerArray.get(2).asJsonObject().getBoolean("inMatch");
        boolean tombool = playerArray.get(3).asJsonObject().getBoolean("inMatch");
        assert(!(stevebool || bobbool || joebool || tombool));


        assertEquals(202, makePostRequestGetConn("email", "joe@oracle.com", "/challengePlayer").getResponseCode());
        
        // [steve, bob, joe, tom] -> [steve, joe, bob, tom]
        conn = makePostRequestGetConn("email", "joe@oracle.com", "/challengePlayer");
        conn = makePostRequestGetConn("email", "joe@oracle.com", "/concludeMatch");
        assertEquals(202, conn.getResponseCode());
        playerArray = makeGetRequestGetJson("/getPlayers").getJsonArray("players");
        assert(playerArray.size() == 4);

        // check correct order and that players freed up for new match
        steve = playerArray.get(0).asJsonObject().getString("name");
        steveRank = playerArray.get(0).asJsonObject().getInt("rank");
        joe = playerArray.get(1).asJsonObject().getString("name");
        joeRank = playerArray.get(1).asJsonObject().getInt("rank");
        bob = playerArray.get(2).asJsonObject().getString("name");
        bobRank = playerArray.get(2).asJsonObject().getInt("rank");
        tom = playerArray.get(3).asJsonObject().getString("name");
        tomRank = playerArray.get(3).asJsonObject().getInt("rank");
        assertEquals("steve", steve); assert(steveRank == 1);
        assertEquals("bob", bob); assert(joeRank == 2);
        assertEquals("joe", joe); assert (bobRank == 3);
        assertEquals("tom", tom); assert(tomRank == 4);
        stevebool = playerArray.get(0).asJsonObject().getBoolean("inMatch");
        joebool = playerArray.get(1).asJsonObject().getBoolean("inMatch");
        bobbool = playerArray.get(2).asJsonObject().getBoolean("inMatch");
        tombool = playerArray.get(3).asJsonObject().getBoolean("inMatch");
        assert(!(stevebool || bobbool || joebool || tombool));

        // [steve, joe, bob, tom] -> [steve, joe, tom, bob]
        conn = makePostRequestGetConn("email", "tom@oracle.com", "/challengePlayer");
        conn = makePostRequestGetConn("email", "tom@oracle.com", "/concludeMatch");
        assertEquals(202, conn.getResponseCode());
        playerArray = makeGetRequestGetJson("/getPlayers").getJsonArray("players");
        assert(playerArray.size() == 4);

        // check correct order and that players freed up for new match
        steve = playerArray.get(0).asJsonObject().getString("name");
        steveRank = playerArray.get(0).asJsonObject().getInt("rank");
        joe = playerArray.get(1).asJsonObject().getString("name");
        joeRank = playerArray.get(1).asJsonObject().getInt("rank");
        tom = playerArray.get(2).asJsonObject().getString("name");
        tomRank = playerArray.get(2).asJsonObject().getInt("rank");
        bob = playerArray.get(3).asJsonObject().getString("name");
        bobRank = playerArray.get(3).asJsonObject().getInt("rank");
        assertEquals("steve", steve); assert(steveRank == 1);
        assertEquals("bob", bob); assert(joeRank == 2);
        assertEquals("joe", joe); assert (tomRank == 3);
        assertEquals("tom", tom); assert(bobRank == 4);

        stevebool = playerArray.get(0).asJsonObject().getBoolean("inMatch");
        joebool = playerArray.get(1).asJsonObject().getBoolean("inMatch");
        tombool = playerArray.get(2).asJsonObject().getBoolean("inMatch");
        bobbool = playerArray.get(3).asJsonObject().getBoolean("inMatch");
        assert(!(stevebool || bobbool || joebool || tombool));

        // [steve, joe, tom, bob] -> [steve, joe, tom, bob] order doesnt change, test bob won as challenged
        conn = makePostRequestGetConn("email", "tom@oracle.com", "/challengePlayer");
        conn = makePostRequestGetConn("email", "joe@oracle.com", "/concludeMatch");
        assertEquals(202, conn.getResponseCode());
        playerArray = makeGetRequestGetJson("/getPlayers").getJsonArray("players");
        assert(playerArray.size() == 4);

        // check correct order and that players freed up for new match
        steve = playerArray.get(0).asJsonObject().getString("name");
        steveRank = playerArray.get(0).asJsonObject().getInt("rank");
        joe = playerArray.get(1).asJsonObject().getString("name");
        joeRank = playerArray.get(1).asJsonObject().getInt("rank");
        tom = playerArray.get(2).asJsonObject().getString("name");
        tomRank = playerArray.get(2).asJsonObject().getInt("rank");
        bob = playerArray.get(3).asJsonObject().getString("name");
        bobRank = playerArray.get(3).asJsonObject().getInt("rank");
        assertEquals("steve", steve); assert(steveRank == 1);
        assertEquals("bob", bob); assert(joeRank == 2);
        assertEquals("joe", joe); assert (tomRank == 3);
        assertEquals("tom", tom); assert(bobRank == 4);
        stevebool = playerArray.get(0).asJsonObject().getBoolean("inMatch");
        joebool = playerArray.get(1).asJsonObject().getBoolean("inMatch");
        tombool = playerArray.get(2).asJsonObject().getBoolean("inMatch");
        bobbool = playerArray.get(3).asJsonObject().getBoolean("inMatch");
        assert(!(stevebool || bobbool || joebool || tombool));

        // checks that players are freed up for challenges
        assertEquals(202, makePostRequestGetConn("email", "joe@oracle.com", "/challengePlayer").getResponseCode());
        assertEquals(202, makePostRequestGetConn("email", "bob@oracle.com", "/challengePlayer").getResponseCode());


        makePostRequestGetConn("email", "steve@oracle.com", "/deletePlayer");
        makePostRequestGetConn("email", "joe@oracle.com", "/deletePlayer");
        makePostRequestGetConn("email", "bob@oracle.com", "/deletePlayer");
        makePostRequestGetConn("email", "tom@oracle.com", "/deletePlayer");
        assert(makeGetRequestGetJson("/getPlayers").getJsonArray("players").isEmpty());

        // bob, steve -> steve, bob -> bob, steve
        conn = makeAddRequest("bob","bob@oracle.com", "bob", "/addPlayer");
        conn = makeAddRequest("steve","steve@oracle.com", "steve", "/addPlayer");
        assertEquals(202, makePostRequestGetConn("email", "steve@oracle.com", "/challengePlayer").getResponseCode());
        conn = makePostRequestGetConn("email", "steve@oracle.com", "/concludeMatch");

        playerArray = makeGetRequestGetJson("/getPlayers").getJsonArray("players");
        assert(playerArray.size() == 2);
        steve = playerArray.get(0).asJsonObject().getString("name");
        steveRank = playerArray.get(0).asJsonObject().getInt("rank");
        stevebool = playerArray.get(0).asJsonObject().getBoolean("inMatch");
        bob = playerArray.get(1).asJsonObject().getString("name");
        bobRank = playerArray.get(1).asJsonObject().getInt("rank");
        bobbool = playerArray.get(1).asJsonObject().getBoolean("inMatch");

        assertEquals("steve", steve); assert(steveRank == 1);
        assertEquals("bob", bob); assert(bobRank == 2);
        assert(!(stevebool || bobbool));

        conn = makePostRequestGetConn("email", "bob@oracle.com", "/challengePlayer");
        conn = makePostRequestGetConn("email", "bob@oracle.com", "/concludeMatch");

        playerArray = makeGetRequestGetJson("/getPlayers").getJsonArray("players");
        assert(playerArray.size() == 2);
        steve = playerArray.get(1).asJsonObject().getString("name");
        steveRank = playerArray.get(1).asJsonObject().getInt("rank");
        stevebool = playerArray.get(1).asJsonObject().getBoolean("inMatch");
        bob = playerArray.get(0).asJsonObject().getString("name");
        bobRank = playerArray.get(0).asJsonObject().getInt("rank");
        bobbool = playerArray.get(0).asJsonObject().getBoolean("inMatch");

        assertEquals("steve", steve); 
        assert(steveRank == 2);
        assertEquals("bob", bob); 
        assert(bobRank == 1);
        assert(!(stevebool || bobbool));

        
        makePostRequestGetConn("email", "steve@oracle.com", "/deletePlayer");
        makePostRequestGetConn("email", "bob@oracle.com", "/deletePlayer");

        System.out.println("SwapPlayer PASSED !!");
    }

    public void printPlayers(JsonArray players) {
        for (int i = 0; i < players.size(); i++) {
            System.out.println(players.get(i).asJsonObject()); 
        }
    }

    @Test
    public void testInMatch() throws Exception {
        // add bob/steve/joe/tom
        HttpURLConnection conn = makeAddRequest("bob","bob@oracle.com", "bob", "/addPlayer");
        conn = makeAddRequest("steve","steve@oracle.com", "steve", "/addPlayer");
        conn = makeAddRequest("joe","joe@oracle.com", "joe", "/addPlayer");
        conn = makeAddRequest("tom","tom@oracle.com", "tom", "/addPlayer");
        
        JsonObject jsonObject = makeGetRequestGetJson("/getPlayers");
        assert(jsonObject.getJsonArray("players").size() == 4);

        assertEquals(202, makePostRequestGetConn("email", "steve@oracle.com", "/inMatch").getResponseCode());
        // [bob, steve, joe, tom] 
        conn = makePostRequestGetConn("email", "steve@oracle.com", "/challengePlayer");
        assertEquals(202, conn.getResponseCode());
        

        assertEquals(202, makePostRequestGetConn("email", "steve@oracle.com", "/inMatch").getResponseCode());

        JsonReader reader = JSON.createReader(makePostRequestGetConn("email", "steve@oracle.com", "/inMatch").getInputStream());
        String player = reader.readObject().asJsonObject().get("player").toString();
        assertEquals("\"bob\"", player);

        reader = JSON.createReader(makePostRequestGetConn("email", "bob@oracle.com", "/inMatch").getInputStream());
        player = reader.readObject().asJsonObject().get("player").toString();
        assertEquals("\"steve\"", player);

        conn = makePostRequestGetConn("email", "steve@oracle.com", "/concludeMatch");
        assertEquals(202, conn.getResponseCode());

        makePostRequestGetConn("email", "steve@oracle.com", "/deletePlayer");
        makePostRequestGetConn("email", "joe@oracle.com", "/deletePlayer");
        makePostRequestGetConn("email", "bob@oracle.com", "/deletePlayer");
        makePostRequestGetConn("email", "tom@oracle.com", "/deletePlayer");
        assert(makeGetRequestGetJson("/getPlayers").getJsonArray("players").isEmpty());
    }

    @Test
    public void testRatios() throws Exception {
         // add bob/steve/joe/tom
        HttpURLConnection conn = makeAddRequest("steve","steve@oracle.com", "steve", "/addPlayer");
        conn = makeAddRequest("bob","bob@oracle.com", "bob", "/addPlayer");
        conn = makeAddRequest("joe","joe@oracle.com", "joe", "/addPlayer");
        conn = makeAddRequest("tom","tom@oracle.com", "tom", "/addPlayer");
        assertEquals(202, conn.getResponseCode());

        // Steve winstreak = 3, wins = 3, losses = 0 ratio = 3
        // Bob winstreak = 0, wins = 0, losses = 3, ratio = 0
        conn = makePostRequestGetConn("email", "bob@oracle.com", "/challengePlayer");
        conn = makePostRequestGetConn("email", "steve@oracle.com", "/concludeMatch");

        conn = makePostRequestGetConn("email", "bob@oracle.com", "/challengePlayer");
        conn = makePostRequestGetConn("email", "steve@oracle.com", "/concludeMatch");

        conn = makePostRequestGetConn("email", "bob@oracle.com", "/challengePlayer");
        conn = makePostRequestGetConn("email", "steve@oracle.com", "/concludeMatch");

        JsonArray playerArray = makeGetRequestGetJson("/getPlayers").getJsonArray("players"); assert(playerArray.size() == 4);
        
        String steve = playerArray.get(0).asJsonObject().getString("name"); assertEquals("steve", steve);
        int steveWins = playerArray.get(0).asJsonObject().getInt("wins"); assertEquals(3, steveWins);
        int steveLosses = playerArray.get(0).asJsonObject().getInt("losses"); assertEquals(0, steveLosses);
        int steveStreak= playerArray.get(0).asJsonObject().getInt("winStreak"); assertEquals(3, steveStreak);
        double steveRatio = playerArray.get(0).asJsonObject().getInt("ratio"); assertEquals(3, steveRatio);

        String bob = playerArray.get(1).asJsonObject().getString("name"); assertEquals("bob", bob);
        int bobWins = playerArray.get(1).asJsonObject().getInt("wins"); assertEquals(0, bobWins);
        int bobLosses = playerArray.get(1).asJsonObject().getInt("losses"); assertEquals(3, bobLosses);
        int bobStreak= playerArray.get(1).asJsonObject().getInt("winStreak"); assertEquals(0, bobStreak);
        double bobRatio = playerArray.get(1).asJsonObject().getInt("ratio"); assertEquals(0, bobRatio);
        
        conn = makePostRequestGetConn("email", "bob@oracle.com", "/challengePlayer");
        conn = makePostRequestGetConn("email", "bob@oracle.com", "/concludeMatch");

        // Steve winstreak = 0, wins = 3, losses = 1 ratio = 3
        // Bob winstreak = 1, wins = 1, losses = 3, ratio = 1/3

        playerArray = makeGetRequestGetJson("/getPlayers").getJsonArray("players"); assert(playerArray.size() == 4);
        
        steve = playerArray.get(1).asJsonObject().getString("name"); assertEquals("steve", steve);
        steveWins = playerArray.get(1).asJsonObject().getInt("wins"); assertEquals(3, steveWins);
        steveLosses = playerArray.get(1).asJsonObject().getInt("losses"); assertEquals(1, steveLosses);
        steveStreak= playerArray.get(1).asJsonObject().getInt("winStreak"); assertEquals(0, steveStreak);
        steveRatio = playerArray.get(1).asJsonObject().getInt("ratio"); assertEquals(3, steveRatio);

        bob = playerArray.get(0).asJsonObject().getString("name"); assertEquals("bob", bob);
        bobWins = playerArray.get(0).asJsonObject().getInt("wins"); assertEquals(1, bobWins);
        bobLosses = playerArray.get(0).asJsonObject().getInt("losses"); assertEquals(3, bobLosses);
        bobStreak= playerArray.get(0).asJsonObject().getInt("winStreak"); assertEquals(1, bobStreak);
        bobRatio = playerArray.get(0).asJsonObject().getInt("ratio"); assertEquals(1/3, bobRatio);
    

        conn = makePostRequestGetConn("email", "steve@oracle.com", "/challengePlayer");
        conn = makePostRequestGetConn("email", "bob@oracle.com", "/concludeMatch");

        // Steve winstreak = 0, wins = 3, losses = 2 ratio = 3/2
        // Bob winstreak = 2, wins = 2, losses = 3, ratio = 2/3

        playerArray = makeGetRequestGetJson("/getPlayers").getJsonArray("players"); assert(playerArray.size() == 4);

        steve = playerArray.get(1).asJsonObject().getString("name"); assertEquals("steve", steve);
        steveWins = playerArray.get(1).asJsonObject().getInt("wins"); assertEquals(3, steveWins);
        steveLosses = playerArray.get(1).asJsonObject().getInt("losses"); assertEquals(2, steveLosses);
        steveStreak= playerArray.get(1).asJsonObject().getInt("winStreak"); assertEquals(0, steveStreak);
        steveRatio = (double)playerArray.get(1).asJsonObject().getInt("ratio"); assertEquals(3/2, steveRatio);

        bob = playerArray.get(0).asJsonObject().getString("name"); assertEquals("bob", bob);
        bobWins = playerArray.get(0).asJsonObject().getInt("wins"); assertEquals(2, bobWins);
        bobLosses = playerArray.get(0).asJsonObject().getInt("losses"); assertEquals(3, bobLosses);
        bobStreak= playerArray.get(0).asJsonObject().getInt("winStreak"); assertEquals(2, bobStreak);
        bobRatio = playerArray.get(0).asJsonObject().getInt("ratio"); assertEquals(2/3, bobRatio);

        conn = makePostRequestGetConn("email", "steve@oracle.com", "/challengePlayer");
        conn = makePostRequestGetConn("email", "bob@oracle.com", "/concludeMatch");
        conn = makePostRequestGetConn("email", "steve@oracle.com", "/challengePlayer");
        conn = makePostRequestGetConn("email", "bob@oracle.com", "/concludeMatch");

        // Steve winstreak = 0, wins = 3, losses = 4 ratio = 3/4
        // Bob winstreak = 4, wins = 4, losses = 3, ratio = 4/3

        conn = makePostRequestGetConn("email", "joe@oracle.com", "/challengePlayer");
        conn = makePostRequestGetConn("email", "joe@oracle.com", "/concludeMatch");

        // Steve winstreak = 0, wins = 3, losses = 5 ratio = 3/5
        // Bob winstreak = 4, wins = 4, losses = 3, ratio = 4/3
        // joe winstreak = 1, wins = 1, losses = 0, ratio = 1

        playerArray = makeGetRequestGetJson("/getPlayers").getJsonArray("players"); assert(playerArray.size() == 4);

        steve = playerArray.get(2).asJsonObject().getString("name"); assertEquals("steve", steve);
        steveWins = playerArray.get(2).asJsonObject().getInt("wins"); assertEquals(3, steveWins);
        steveLosses = playerArray.get(2).asJsonObject().getInt("losses"); assertEquals(5, steveLosses);
        steveStreak= playerArray.get(2).asJsonObject().getInt("winStreak"); assertEquals(0, steveStreak);
        steveRatio = (double)playerArray.get(2).asJsonObject().getInt("ratio"); assertEquals(3/5, steveRatio);

        bob = playerArray.get(0).asJsonObject().getString("name"); assertEquals("bob", bob);
        bobWins = playerArray.get(0).asJsonObject().getInt("wins"); assertEquals(4, bobWins);
        bobLosses = playerArray.get(0).asJsonObject().getInt("losses"); assertEquals(3, bobLosses);
        bobStreak= playerArray.get(0).asJsonObject().getInt("winStreak"); assertEquals(4, bobStreak);
        bobRatio = playerArray.get(0).asJsonObject().getInt("ratio"); assertEquals(4/3, bobRatio);

        String joe = playerArray.get(1).asJsonObject().getString("name"); assertEquals("joe", joe);
        int joeWins = playerArray.get(1).asJsonObject().getInt("wins"); assertEquals(1, joeWins);
        int joeLosses = playerArray.get(1).asJsonObject().getInt("losses"); assertEquals(0, joeLosses);
        int joeStreak= playerArray.get(1).asJsonObject().getInt("winStreak"); assertEquals(1, joeStreak);
        double joeRatio = playerArray.get(1).asJsonObject().getInt("ratio"); assertEquals(1, joeRatio);


        makePostRequestGetConn("email", "steve@oracle.com", "/deletePlayer");
        makePostRequestGetConn("email", "joe@oracle.com", "/deletePlayer");
        makePostRequestGetConn("email", "bob@oracle.com", "/deletePlayer");
        makePostRequestGetConn("email", "tom@oracle.com", "/deletePlayer");
        assert(makeGetRequestGetJson("/getPlayers").getJsonArray("players").isEmpty());

    }

    private HttpURLConnection getURLConnection(String method, String path) throws Exception {
        URL url = new URL("http://localhost:" + webServer.port() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        System.out.println("Connecting: " + method + " " + url);
        return conn;
    }
}
