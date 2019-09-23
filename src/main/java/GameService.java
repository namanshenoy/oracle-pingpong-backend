import java.util.Collections;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonException;
import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import oracle.kv.KVStore;
import oracle.kv.KVStoreConfig;
import oracle.kv.KVStoreFactory;
import oracle.kv.table.Table;
import oracle.kv.table.TableAPI;
import oracle.kv.table.PrimaryKey;
import oracle.kv.table.Row;
import oracle.kv.table.TableIterator;
import oracle.kv.FaultException;

import java.net.InetAddress;
import java.net.UnknownHostException;


public class GameService implements Service {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private KVStoreConfig kconfig;
    private KVStore kvstore;
    private TableAPI playersAPI;
    private Table players;
    
    class Player {
        public Player(String name, String email, String password) {
            this.name = name;
            this.email = email;
            this.password = password;
            this.winStreak = 0;
            this.wins = 0;
            this.losses = 0;

            // FIX: inefficient count method for updating rank
            PrimaryKey key = players.createPrimaryKey();
            int rowCount = 0;

            try {
                TableIterator<Row> it = playersAPI.tableIterator(key, null, null);
                
                while (it.hasNext()) {
                    it.next();
                    rowCount++;
                }   
            } catch (Exception e) {
                System.out.println(e.getMessage());
                System.exit(1);
            }
           
            
            this.rank = rowCount + 1;
        }

        public Row toRow() {
            Row row = players.createRow();
            row.put("email", this.email);
            row.put("rank", this.rank);
            row.put("name", this.name);
            row.put("password", this.password);
            row.put("challenged", this.challenged);
            row.put("challenger", this.challenger);
            row.put("winStreak", this.winStreak);
            row.put("wins", this.wins);
            row.put("losses", this.losses);

            return row;
        }

         // email unique identifier
         int rank, winStreak, wins, losses;
         String name = "", email = "", password = "";  
         boolean challenged = false, challenger = false;
 
    }

    // Configure KVstore and table
    GameService(Config config) {
        // if use random error becomes unknown

        // linux hostname
        String host = "";
        try {
            host =  InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        
        kconfig = new KVStoreConfig("kvstore", host + ":5000");
        kvstore = KVStoreFactory.getStore(kconfig);

        // deleteTable();
        createTable();
        playersAPI = kvstore.getTableAPI();
        players = playersAPI.getTable("players");

        // deleteAllRows(kvstore);
    }

    public void createTable() {
        try {
            String statement =
                "CREATE TABLE players (" +
                "email STRING," +
                "rank INTEGER," +
                "winStreak INTEGER," +
                "wins INTEGER," +
                "losses INTEGER," +
                "name STRING," +
                "password STRING," +
                "challenger BOOLEAN," +
                "challenged BOOLEAN," +
                "PRIMARY KEY (email))"; // Required"

                kvstore.executeSync(statement);
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid statement:\n" + e.getMessage());
            } catch (FaultException e) {
                System.out.println
                ("Statement couldn't be executed, please retry: " + e);
            }

    }

    public void deleteTable() {
        try {
            String statement = "DROP TABLE players";

            kvstore.executeSync(statement);

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * A service registers itself by updating the routine rules.
     * 
     * @param rules the routing rules.
     */
    @Override
    public void update(Routing.Rules rules) {
        rules.get("/", this::handleGetPlayers).post("/addPlayer", this::handleAddPlayer).get("/getPlayers", this::handleGetPlayers)
                .post("/challengePlayer", this::handleChallengePlayer) 
                .post("/deletePlayer", this::handleDeletePlayer)
                .post("/concludeMatch", this::handleConcludeMatch)
                .post("/login", this::handleLogin)
                .post("/inMatch", this::handlePlayerInMatch)
                .post("/manageScores", this::manageScores);
    }
    
    private void manageScores(ServerRequest request, ServerResponse response) {
    	request.content().as(JsonObject.class).thenAccept(jo -> handleManageScores(jo, response));
    }


    /* ----------------------       LOGIN            ------------------------------------        */
    /* ----------------------       LOGIN            ------------------------------------        */
    
    private void handleLogin(ServerRequest request, ServerResponse response) {
        request.content().as(JsonObject.class).thenAccept(jo -> handleLoginResponse(jo, response));
    }

    private void handleLoginResponse(JsonObject jo, ServerResponse response) {
        if (!jo.containsKey("email") || !jo.containsKey("password")) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "Please provide player email/password").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }
        String email = jo.getString("email").toLowerCase();
        int playerRank = containsPlayer(email);

        // no player by that name
        if (playerRank == -1) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "No player by that name").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }

        String password = jo.getString("password").toString();
        PrimaryKey key = players.createPrimaryKey();
        key.put("email", email);
        Row row = playersAPI.get(key, null);

        if (!password.equals(row.get("password").asString().get())) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "Incorrect Password").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }

        boolean inMatch = row.get("challenged").asBoolean().get() || row.get("challenger").asBoolean().get();
        JsonObject loginSuccess = JSON.createObjectBuilder().add("success", "Player Logged In").add("inMatch", inMatch).build();
        response.status(Http.Status.ACCEPTED_202).send(loginSuccess);
        return;

    }

    /* ---------------------- ADD PLAYER ------------------------------------ */
    /* ---------------------- ADD PLAYER ------------------------------------ */

    private void handleAddPlayer(ServerRequest request, ServerResponse response) {
        request.content().as(JsonObject.class).thenAccept(jo -> handleAddPlayerResponse(jo, response));
    }

    private void handleAddPlayerResponse(JsonObject jo, ServerResponse response) {
        if (!(jo.containsKey("player") && jo.containsKey("email") && jo.containsKey("password"))) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "Please provide player/email/password").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        } else if (containsPlayer(jo.getString("email").toLowerCase()) != -1) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder()
                    .add("error", "Player Email Already Exists : " + jo.getString("email").toLowerCase()).build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }
        String name = jo.getString("player").toString();
        String email = jo.getString("email").toLowerCase();
        String password = jo.getString("password").toString();
        
        // email must be Oracle email
        if (!email.contains("@oracle.com")) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "Email must be Oracle email").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }

        
        addPlayer(name, email, password);
        displayTable(kvstore);
        JsonObject jsonSuccessObject = JSON.createObjectBuilder().add("success", "Added player: " + name).build();
        response.status(Http.Status.ACCEPTED_202).send(jsonSuccessObject);
    }
    
    private void handleManageScores(JsonObject jo, ServerResponse response) {
    	if (!jo.containsKey("email")) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "Please provide email").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }
    	
    	int wins = jo.getInt("wins");
    	int losses = jo.getInt("losses");
    	String email = jo.getString("email").toLowerCase();
    	
    	updateRowWinLoss(wins, losses, email);
    	displayTable(kvstore);
    	JsonObject jsonSuccessObject = JSON.createObjectBuilder().add("success", "Player scores updated successfully").build();
        response.status(Http.Status.ACCEPTED_202).send(jsonSuccessObject);
    }
    
    
    private void updateRowWinLoss(int updated_wins, int updated_losses, String email) {
    	// find player's row
        PrimaryKey player = players.createPrimaryKey();
        player.put("email", email);
        Row playerRow = playersAPI.get(player, null);
        
        playerRow.put("wins", updated_wins).put("losses", updated_losses);
        
    }
    
    
    

    private int containsPlayer(String emailIn) {
        PrimaryKey key = players.createPrimaryKey();
        key.put("email", emailIn);

        try {
            TableIterator<Row> it = playersAPI.tableIterator(key, null, null);
            if (it.hasNext()) {
                return it.next().get("rank").asInteger().get();
            }
            return -1;

        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(1);
            return -1;
        }
      
    }

    private void addPlayer(String name, String email, String password) {
        Player newPlayer = new Player(name, email, password);
        playersAPI.put(newPlayer.toRow(), null, null);
        return;
    }

    // public void clearDatabase() {
    //     players.deleteMany(new BasicDBObject());
    // }
    /* ---------------------- GET PLAYER ------------------------------------ */
    /* ---------------------- GET PLAYER ------------------------------------ */

    private void handleGetPlayers(ServerRequest request, ServerResponse response) {
        PrimaryKey key = players.createPrimaryKey();
        TableIterator<Row> rowIter = playersAPI.tableIterator(key, null, null);

        List<JsonObject> players = new ArrayList<JsonObject>();

        // table sorted
        while (rowIter.hasNext()) {
            Row player = rowIter.next();

            String playerName = player.get("name").toString();
            String playerEmail = player.get("email").toString();
            int playerRank = player.get("rank").asInteger().get();
            int wins = player.get("wins").asInteger().get();
            int losses = player.get("losses").asInteger().get();
            int winStreak = player.get("winStreak").asInteger().get();
            double ratio = (losses == 0) ? wins : (double)wins / (double)losses;
            boolean inMatch = player.get("challenged").asBoolean().get() || player.get("challenger").asBoolean().get();

            players.add(Json.createObjectBuilder().add("name", playerName)
                                                  .add("email", playerEmail).add("rank", playerRank)
                                                  .add("inMatch", inMatch)
                                                  .add("wins", wins).add("losses", losses)
                                                  .add("winStreak", winStreak).add("ratio", ratio).build());
        }

        rowIter.close();
        
        Collections.sort(players, new Comparator<JsonObject>() {
        
        @Override
        public int compare(JsonObject a, JsonObject b) {
            String valA = new String();
            String valB = new String();

            try {
                valA = (String) a.get("rank").toString();
                valB = (String) b.get("rank").toString();
            } 
            catch (JsonException e) {
                System.out.println(e.getMessage());
            }

            return valA.compareTo(valB);
            }
            
        });

        JsonArrayBuilder builder = Json.createArrayBuilder();

        for (int i = 0; i < players.size(); i++) {
            builder.add(players.get(i));
        }
        
        JsonArray playersSorted = builder.build();

        JsonObject returnObject = JSON.createObjectBuilder().add("players", playersSorted).build();
        response.send(returnObject);    
    }

    public int compare(JsonObject a, JsonObject b) {
        String valA = new String();
        String valB = new String();

        try {
            valA = (String) a.get("rank").toString();
            valB = (String) b.get("rank").toString();
        } 
        catch (JsonException e) {
            System.out.println(e.getMessage());
        }

        return valA.compareTo(valB);
    }

    // NOT USED OUTSIDE OF TESTING
    // private ArrayList<Player> getPlayers() {
    //     ArrayList<Player> playerList = new ArrayList<Player>();
    //     try(MongoCursor<Document> cursor = players.find().sort(new BasicDBObject("rank", 1)).iterator()) {
    //         while(cursor.hasNext()) {
    //             Document player = cursor.next();
    //             String playerName = player.get("name", String.class);
    //             String email = player.get("email", String.class);
    //             String password = player.get("password", String.class);

    //             Player newPlayer = new Player(playerName, email, password);
    //             newPlayer.rank = (int)player.get("rank", Integer.class);
    //             playerList.add(newPlayer);
    //         }
    //     };
    //     return playerList;
    // }   

    /* ---------------------- CHALLENGE PLAYER -----------------------------------  */
    /* ---------------------- CHALLENGE PLAYER ------------------------------------ */

    private void handleChallengePlayer(ServerRequest request, ServerResponse response) {
        request.content().as(JsonObject.class).thenAccept(jo -> handleChallengePlayerResponse(jo, response));
    }

    // Person must select own name if ready to challenge player above them
    private void handleChallengePlayerResponse(JsonObject jo, ServerResponse response) {
        if (!jo.containsKey("email")) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "Please provide challenger name and email").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        } else if (containsPlayer(jo.getString("email").toLowerCase()) == -1) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "No player by that name").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }

        String challengerEmail = jo.getString("email").toLowerCase();
        int rankChallenger = containsPlayer(challengerEmail);

       
        // check if player in battle already
        // if not, set challenged = true
        if (inBattle(rankChallenger)) {
            // player already in Battle, cannot challenge
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "You are already in match").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }

         // top player can't challenge anyone above
        if (rankChallenger == 1) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "Top Player Cannot Challenge Anyone").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }

        // check player above
        if (inBattle(rankChallenger - 1)) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "Player above you already in match. Please wait until concluded").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }

        // find row with challenger rank
        PrimaryKey challenger = players.createPrimaryKey();
        challenger.put("email", findEmail(rankChallenger));
        Row challengerPlayer = playersAPI.get(challenger, null);

        // update row then table
        challengerPlayer.put("challenger", true).put("challenged", false);
        playersAPI.put(challengerPlayer, null, null);

        // find row tie challenged rank
        PrimaryKey challenged = players.createPrimaryKey();
        challenged.put("email", findEmail(rankChallenger - 1));
        Row challengedPlayer = playersAPI.get(challenged, null);

        // update row then table
        challengedPlayer.put("challenged", true).put("challenger", false);
        playersAPI.put(challengedPlayer, null, null);


        JsonObject jsonSuccessObject = JSON.createObjectBuilder().add("success", "Challenge Initiated").build();
        response.status(Http.Status.ACCEPTED_202).send(jsonSuccessObject);

    }

    private Boolean inBattle(int playerRank) {  
        PrimaryKey key = players.createPrimaryKey();
        key.put("email", findEmail(playerRank));
        Row player = playersAPI.get(key, null);

        if (player.get("challenger").asBoolean().get() || player.get("challenged").asBoolean().get())
            return true;
        return false;
    }

    /* -------------------------- CONCLUDE MATCH  -------------------------------------  */
    /* -------------------------- CONCLUDE MATCH  -------------------------------------  */

    private void handleConcludeMatch(ServerRequest request, ServerResponse response) {
        request.content().as(JsonObject.class).thenAccept(jo -> handleConcludeMatchResponse(jo, response));
    }

    // Winning person must select
    private void handleConcludeMatchResponse(JsonObject jo, ServerResponse response) {
        if (!jo.containsKey("email")) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "Please provide player email").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        } else if (containsPlayer(jo.getString("email").toLowerCase()) == -1) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "No player by that name").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }

        String winnerEmail = jo.getString("email").toLowerCase();

        PrimaryKey key = players.createPrimaryKey();
        key.put("email", winnerEmail);
        TableIterator<Row> it = playersAPI.tableIterator(key, null, null);
        Row winningPlayer = it.next();


        // if bottom won call swap player else just finish match without swap
        if (!winningPlayer.get("challenger").asBoolean().get() && !winningPlayer.get("challenged").asBoolean().get()) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "Player not in match").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        } else if (winningPlayer.get("challenger").asBoolean().get()) {
            handleSwapPlayerResponse(jo, response);
        } else {
            int winningRank = winningPlayer.get("rank").asInteger().get();        
            int losingRank = winningRank + 1;

            // Update Winner
            int wins = winningPlayer.get("wins").asInteger().get();
            int winStreak = winningPlayer.get("winStreak").asInteger().get();

            // update row then table
            winningPlayer.put("challenger", false).put("challenged", false).put("wins", wins + 1).put("winStreak", winStreak + 1);
            playersAPI.put(winningPlayer, null, null);

            // Update Loser
            PrimaryKey losingP = players.createPrimaryKey();
            losingP.put("email", findEmail(losingRank));
            Row losingPlayer = playersAPI.get(losingP, null);

            int losses = losingPlayer.get("losses").asInteger().get();

            losingPlayer.put("challenger", false).put("challenged", false).put("winStreak", 0).put("losses", losses + 1);
            playersAPI.put(losingPlayer, null, null);

        }

        JsonObject jsonSuccessObject = JSON.createObjectBuilder().add("success", "Match Concluded").build();
        response.status(Http.Status.ACCEPTED_202).send(jsonSuccessObject);
        return;
    }

    /* -------------------------- SWAP PLAYER ------------------------------------ */
    /* -------------------------- SWAP PLAYER ------------------------------------ */

    // Requires: at least two players exist,
    private void handleSwapPlayerResponse(JsonObject jo, ServerResponse response) {

        int playerRank = containsPlayer(jo.getString("email").toLowerCase());
    
        if (playerRank == -1) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder()
                    .add("error", "No player named " + jo.getString("player")).build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }

        swapPlayer(playerRank, response);

        JsonObject jsonSuccessObject = JSON.createObjectBuilder()
                  .add("success", "Players swapped ranks").build();
        response.status(Http.Status.ACCEPTED_202).send(jsonSuccessObject);
        return;

    }

    // Requires: player provided is challenging player
    // only called if challenging player wins
    // winningRank indicates rank of winning challenger, will be swapped
    private void swapPlayer(int winningRank, ServerResponse response) {
        // swap should only be called when challenging player wins
        PrimaryKey winner = players.createPrimaryKey();
        winner.put("email", findEmail(winningRank));
        Row winningPlayer = playersAPI.get(winner, null);

        if (!winningPlayer.get("challenger").asBoolean().get() || winningPlayer.get("challenged").asBoolean().get() || winningRank == 1) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("ERROR", "Swap player called incorrectly").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        } 

        // Update Winner
        int wins = winningPlayer.get("wins").asInteger().get();
        int winStreak = winningPlayer.get("winStreak").asInteger().get();

        // update player
        winningPlayer.put("challenger", false).put("challenged", false).put("wins", wins + 1).put("winStreak", winStreak + 1).put("rank", winningRank - 1);
        

        // Update Loser
        PrimaryKey losingP = players.createPrimaryKey();
        losingP.put("email", findEmail(winningRank - 1));
        Row losingPlayer = playersAPI.get(losingP, null);

        int losses = losingPlayer.get("losses").asInteger().get();

        losingPlayer.put("challenger", false).put("challenged", false).put("winStreak", 0).put("losses", losses + 1).put("rank", winningRank);
       
        // Update Table
        playersAPI.put(losingPlayer, null, null);
        playersAPI.put(winningPlayer, null, null);

    }

    /* -------------------------- DELETE PLAYER ------------------------------------ */
    /* -------------------------- DELETE PLAYER ------------------------------------ */

    private void handleDeletePlayer(ServerRequest request, ServerResponse response) {
        request.content().as(JsonObject.class).thenAccept(jo -> handleDeletePlayerResponse(jo, response));
    }

    private void handleDeletePlayerResponse(JsonObject jo, ServerResponse response) {
        if (!jo.containsKey("email")) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "Provide player email").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        } else if (containsPlayer(jo.getString("email").toLowerCase()) == -1) {
            JsonObject jsonErrorObject = JSON.createObjectBuilder().add("error", "No such player exists").build();
            response.status(Http.Status.BAD_REQUEST_400).send(jsonErrorObject);
            return;
        }

        String email = jo.getString("email").toLowerCase();
        deletePlayer(email);
        JsonObject jsonSuccess = JSON.createObjectBuilder().add("SUCCESS", "Deleted Player with email: " + email).build();
        response.status(Http.Status.ACCEPTED_202).send(jsonSuccess);

    }

    private void deletePlayer(String emailIn) {
        PrimaryKey key = players.createPrimaryKey();
        key.put("email", emailIn);
        TableIterator<Row> it = playersAPI.tableIterator(key, null, null);

        Row player = it.next();

        if (player.get("challenged").asBoolean().get()) {
            // player below no longer in match
            PrimaryKey below = players.createPrimaryKey();
            below.put("email", findEmail(player.get("rank").asInteger().get() + 1));
            Row belowPlayer = playersAPI.get(below, null);

            belowPlayer.put("challenger", false).put("challenged", false);
            playersAPI.put(belowPlayer, null, null);

        } else if (player.get("challenger").asBoolean().get()) {
             // player above no longer in match
             PrimaryKey above = players.createPrimaryKey();
             above.put("email", findEmail(player.get("rank").asInteger().get() - 1));
             Row abovePlayer = playersAPI.get(above, null);
 
             abovePlayer.put("challenger", false).put("challenged", false);
             playersAPI.put(abovePlayer, null, null);
        }
        
        int rankToDelete = player.get("rank").asInteger().get();
        key.put("email", findEmail(rankToDelete));
        playersAPI.delete(key, null, null);
        updateRanks(rankToDelete);
        
        return;
    }

    // updates all ranks after the one deleted to be one less than previous
    private void updateRanks(int rankDeleted) {
        PrimaryKey key = players.createPrimaryKey();
        TableIterator<Row> it = playersAPI.tableIterator(key, null, null);
        
        while (it.hasNext()) {
            Row player = it.next();
            int rank = player.get("rank").asInteger().get();

            if (rank > rankDeleted) {
                player.put("rank", rank - 1);
                playersAPI.put(player, null, null);
            }
        }
    }

    /* -------------------------- inMatch ------------------------------------ */
    /* -------------------------- inMatch ------------------------------------ */

    private void handlePlayerInMatch(ServerRequest request, ServerResponse response) {
        request.content().as(JsonObject.class).thenAccept(jo -> handlePlayerInMatchResponse(jo, response));
    }

    private void handlePlayerInMatchResponse(JsonObject jo, ServerResponse response) {
        if (!jo.containsKey("email")) {
            JsonObject error = Json.createObjectBuilder().add("error", "Please provide player email").build();
            response.status(Http.Status.BAD_REQUEST_400).send(error);
            return;
        }
        
        PrimaryKey key = players.createPrimaryKey();
        key.put("email", jo.getString("email"));
        Row player = playersAPI.get(key, null);

        if (!player.isNull()) {
            boolean inMatch = false; 
            String playerName = "NULL";
            String playerEmail = "NULL";

            if (player.get("challenger").asBoolean().get()) {
                PrimaryKey challengedKey = players.createPrimaryKey();
                challengedKey.put("email", findEmail(player.get("rank").asInteger().get() - 1));
                Row challengedPlayer = playersAPI.get(challengedKey, null);

                playerName = challengedPlayer.get("name").asString().get();
                playerEmail = challengedPlayer.get("email").asString().get();
                inMatch = true;
            } else if (player.get("challenged").asBoolean().get()) {
                PrimaryKey challengerKey = players.createPrimaryKey();
                challengerKey.put("email", findEmail(player.get("rank").asInteger().get() + 1));
                Row challengerPlayer = playersAPI.get(challengerKey, null);

                playerName = challengerPlayer.get("name").asString().get();
                playerEmail = challengerPlayer.get("email").asString().get();
                inMatch = true;
            }

            JsonObject returnBool = Json.createObjectBuilder().add("inMatch", inMatch).add("player", playerName).add("email", playerEmail).build();
            response.status(Http.Status.ACCEPTED_202).send(returnBool);
            return;

        } else {
            JsonObject error = Json.createObjectBuilder().add("error", "No player with that email").build();
            response.status(Http.Status.BAD_REQUEST_400).send(error);
            return;
        }
    }

    private void displayTable(KVStore kvstore) {
        TableAPI tableH = kvstore.getTableAPI();
        Table myTable = tableH.getTable("players");

        PrimaryKey pkey = myTable.createPrimaryKey();
        TableIterator<Row> iter = tableH.tableIterator(pkey, null,
                null);
        try {
            while (iter.hasNext()) {
                Row row = iter.next();
                System.out.println("Player: " + row.get("email").asString().get() +
                                   "Rank: " + row.get("rank").asInteger().toString());
            }
        } catch (Exception e) {
                System.out.println(e.getMessage());
                System.exit(1);
            }
        }


    private void deleteAllRows(KVStore kvstore) {
        TableAPI tableH = kvstore.getTableAPI();
        Table myTable = tableH.getTable("players");

        PrimaryKey pkey = myTable.createPrimaryKey();
        TableIterator<Row> iter = tableH.tableIterator(pkey, null,
                null);
        try {
            while (iter.hasNext()) {
                Row row = iter.next();
                String email = row.get("email").asString().get();
                PrimaryKey key = myTable.createPrimaryKey();
                key.put("email", email);
                tableH.delete(key, null, null);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }


    private String findEmail(int rank) {
        PrimaryKey key = players.createPrimaryKey();
        TableIterator<Row> it = playersAPI.tableIterator(key, null, null);

        while (it.hasNext()) {
            Row player = it.next();
            if (player.get("rank").asInteger().get() == rank) return player.get("email").asString().get();
        }

        return "NULL EMAIL FROM FIND EMAIL";
    }
}

