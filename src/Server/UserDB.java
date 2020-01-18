package Server;

import Commons.WQRegisterInterface;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static Commons.Constants.*;

/**
 * Class representing and collecting the users and their relations. Handles all the operations involving users
 * The class has only static method so they can be called easily where needed
 * Package private class, the class and the member can only be called by the Server components
 *
 * Thread safety is assured by the use of concurrent hash map
 *
 * Contains 3 inner classes used for data representation
 */
class UserDB {
    static UserDB instance; //stores the only instance of the DB

    private static ConcurrentHashMap<String, User> usersTable = new ConcurrentHashMap<>(); //key -> username, value -> User object
    private static SimpleGraph relationsGraph = new SimpleGraph(); //store all the friend relationships of the user in a graph

    //temporarily stores all the user involved in pending challenges for fast retrieval
    transient private ConcurrentHashMap<SocketAddress, ChallengeInfo> pendingChallenges = new ConcurrentHashMap<>();

    transient private static byte[][] oldJsonFile = new byte[2][0]; //contains the latest copy of this DB stored to file

    /*
      Init of the database, if found restores the users from file
     */
    static {
        try(BufferedReader tableReader = new BufferedReader(new FileReader(Consts.USER_TABLE_FILENAME));
            BufferedReader graphReader = new BufferedReader(new FileReader(Consts.USER_GRAPH_FILENAME))) {
            Gson gson = new Gson();
            Type userTableType = new TypeToken<ConcurrentHashMap<String, User>>() {}.getType();
            usersTable = gson.fromJson(tableReader, userTableType);
            relationsGraph = gson.fromJson(graphReader, SimpleGraph.class);
            instance = new UserDB();
            instance.logoutAll();
        } catch (FileNotFoundException e) {
            //file doesn't exist, yet
            instance = new UserDB();
        } catch (Exception e) {
            e.printStackTrace();
            //some error occurred, keep going
            instance = new UserDB();
        }



        //Daemon thread which writes every interval the db to file
        Thread saveThread = new Thread(() -> {
            try {
                while (true){
                    Thread.sleep(Consts.DB_SAVE_INTERVAL);
                    storeToFile();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        saveThread.setDaemon(true);
        saveThread.start();

    }

    /**
     * Store the database to a file
     * To prevent corrupted copies (es. program ends before write completes)
     * it first writes to a tmp file and then attempts to rename it to the final name
     */
    private static void storeToFile(){
        Gson gson = new Gson();
        Type userTableType = new TypeToken<ConcurrentHashMap<String, User>>() {}.getType();
        byte[] jsonTable = gson.toJson(usersTable, userTableType).getBytes(StandardCharsets.UTF_8);
        byte[] jsonGraph = gson.toJson(relationsGraph).getBytes(StandardCharsets.UTF_8);

        if(jsonTable != oldJsonFile[0] || jsonGraph != oldJsonFile[1]) {

            try (FileChannel jsonTableFile = FileChannel.open(Paths.get(Consts.USER_TABLE_FILENAME_TMP), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
                 FileChannel jsonGraphFile = FileChannel.open(Paths.get(Consts.USER_GRAPH_FILENAME_TMP), StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
                ByteBuffer byteBuffer = ByteBuffer.wrap(jsonTable);

                while (byteBuffer.hasRemaining()) {
                    jsonTableFile.write(byteBuffer);
                }

                //attempts to rename the file, outcome may be implementation specific (works with linux + jdk 12)
                //TODO: check other systems
                Files.move(Paths.get(Consts.USER_TABLE_FILENAME_TMP), Paths.get(Consts.USER_TABLE_FILENAME),
                        StandardCopyOption.ATOMIC_MOVE , StandardCopyOption.REPLACE_EXISTING);

                byteBuffer = ByteBuffer.wrap(jsonGraph);

                while (byteBuffer.hasRemaining()) {
                    jsonGraphFile.write(byteBuffer);
                }

                Files.move(Paths.get(Consts.USER_GRAPH_FILENAME_TMP), Paths.get(Consts.USER_GRAPH_FILENAME),
                        StandardCopyOption.ATOMIC_MOVE , StandardCopyOption.REPLACE_EXISTING);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Add a user the the database
     * @param username The name of the user
     * @param password His password
     * @throws WQRegisterInterface.UserAlreadyRegisteredException When the username is already present in the DB
     * @throws WQRegisterInterface.InvalidPasswordException When the password is blank or null
     */
    void addUser(String username, String password) throws WQRegisterInterface.UserAlreadyRegisteredException, WQRegisterInterface.InvalidPasswordException {
        if(usersTable.containsKey(username))
            throw new WQRegisterInterface.UserAlreadyRegisteredException();
        if(password == null || password.isBlank()) //isBlank() requires java 11
            throw new WQRegisterInterface.InvalidPasswordException();

        usersTable.put(username, new User(username, password));

        relationsGraph.addNode();
    }

    /**
     * Logs in the user to the DB
     * @param username The name of the user
     * @param password His password
     * @param address His current IP address
     * @param TCPPort His current TCP port
     * @param UDPPort His preferred UDP port
     * @throws UserNotFoundException When the username is not to be found in the DB
     * @throws WQRegisterInterface.InvalidPasswordException When the given password doesn't match the original one
     * @throws AlreadyLoggedException When the user is already logged
     */
    void logUser(String username, String password, InetAddress address, int TCPPort, int UDPPort) throws UserNotFoundException, WQRegisterInterface.InvalidPasswordException, AlreadyLoggedException {
        User user = usersTable.get(username);
        if(user == null)
            throw new UserNotFoundException();
        if(user.isLogged())
            throw new AlreadyLoggedException();
        if(user.notMatches(username, password))
            throw new WQRegisterInterface.InvalidPasswordException();

        user.login(address, TCPPort, UDPPort);
    }

    /**
     * Logs in the user to the DB with the default port
     * @param username The name of the user
     * @param password His password
     * @param address His current IP address
     * @param TCPPort His current TCP port
     * @throws UserNotFoundException When the username is not to be found in the DB
     * @throws WQRegisterInterface.InvalidPasswordException When the given password doesn't match the original one
     * @throws AlreadyLoggedException When the user is already logged
     */
    void logUser(String username, String password, InetAddress address, int TCPPort) throws UserNotFoundException, WQRegisterInterface.InvalidPasswordException, AlreadyLoggedException {
        logUser(username, password, address, TCPPort,  UDP_PORT);
    }

    /**
     * Logs out the user
     * @param username The name of the user
     * @throws UserNotFoundException If the user could not be found
     * @throws NotLoggedException If the user is not logged in
     */
    void logoutUser(String username, InetAddress address, int TCPPort) throws UserNotFoundException, NotLoggedException {
        User user = usersTable.get(username);
        if(user == null)
            throw new UserNotFoundException();
        if(!user.isLogged(address, TCPPort))
            throw new NotLoggedException();

        user.logout();
    }

    /**
     * Logs out all the user in the database
     */
    private void logoutAll() {
        usersTable.forEach((s, user) -> user.logout());
    }


    /**
     * Creates a friendship between the given user
     * @param username1 The user who requested the friendship
     * @param username2 The user username1 wants to be friend with
     * @throws UserNotFoundException If one of the user were not found in the DB
     * @throws AlreadyFriendsException If the two users were already friends
     * @throws NotLoggedException If the requesting user is not logged
     * @throws SameUserException If the nick provided are the same
     */
    void addFriendship(String username1, String username2, InetAddress address, int TCPPort) throws UserNotFoundException, AlreadyFriendsException, NotLoggedException, SameUserException {
        if(username1.equals(username2))
            throw new SameUserException();

        User user1 = usersTable.get(username1);
        User user2 = usersTable.get(username2);

        if(user1 == null || user2 == null)
            throw new UserNotFoundException();
        if(!user1.isLogged(address, TCPPort))
            throw new NotLoggedException();
        if(relationsGraph.nodesAreLinked(user1, user2))
            throw new AlreadyFriendsException();

        relationsGraph.addArch(user1, user2);
    }

    /**
     * Retrieves the friend list of the given user
     * @param username The username whose friends to return
     * @param address His current IP address
     * @param TCPPort His current TCP port
     * @return A json array of String of the friends
     * @throws UserNotFoundException If the given user were not found
     * @throws NotLoggedException If the given user is not logged in
     */
    String getFriends(String username, InetAddress address, int TCPPort) throws UserNotFoundException, NotLoggedException {
        User friendlyUser = usersTable.get(username);

        if (friendlyUser == null)
            throw new UserNotFoundException();
        if(!friendlyUser.isLogged(address, TCPPort))
            throw new NotLoggedException();


        LinkedList<User> friends = relationsGraph.getLinkedNodes(friendlyUser);
        JsonArray userNames = new JsonArray();
        for(User user: friends){
            userNames.add(user.getName());
        }
        return userNames.toString();
    }

    /**
     * Retrieves the score of the given user
     * @param name The name of the user
     * @param address His current IP address
     * @param TCPPort His current TCP port
     * @return The score of the given user
     */
    int getScore(String name, InetAddress address, int TCPPort) throws NotLoggedException, UserNotFoundException {
        User user = usersTable.get(name);
        if(user == null)
            throw new UserNotFoundException();
        if(!user.isLogged(address, TCPPort))
            throw new NotLoggedException();

        return user.getScore();
    }

    /**
     * Retrieves the ranking by score of all the friends of the given user
     * @param name The name of the user
     * @param address His current IP address
     * @param TCPPort His current TCP port
     * @return A json object of a string array with name and ranking of each user
     */
    String getRanking(String name, InetAddress address, int TCPPort) throws NotLoggedException, UserNotFoundException {
        User user = usersTable.get(name);
        if(user == null)
            throw new UserNotFoundException();
        if(!user.isLogged(address, TCPPort))
            throw new NotLoggedException();
        LinkedList<User> friends = relationsGraph.getLinkedNodes(user);
        friends.add(user);
        User[] rankingList = friends.toArray(new User[0]); //get array for faster access
        Arrays.sort(rankingList, (o1, o2) -> o2.getScore() - o1.getScore());//sort by the score
        String[] ranking = new String[rankingList.length]; //ranking with name and score

        for(int i = 0; i < ranking.length; i++){
            ranking[i] = rankingList[i].getName() + " " + rankingList[i].getScore();
        }
        Gson gson = new Gson();
        return gson.toJson(ranking);
    }

    /**
     * Updates the user score by the given amount
     */
    void updateScore(String name, int amount) {
        User user = usersTable.get(name);
        if(user == null)
            return;
        user.updateScore(amount);
    }

    /**
     * Creates a pending challenge
     * @throws UserNotFoundException If the user were not found
     * @throws NotFriendsException If the two users are not friends
     * @throws NotLoggedException If the challenger is not logged
     * @throws SameUserException If the two user are the same
     * @return The datagram packet to be sent to the user who got challenged
     */
    DatagramPacket challengeFriend(String challengerName, String challengedName, int challengerPort) throws UserNotFoundException, NotFriendsException, NotLoggedException, SameUserException {
        if(challengedName.equals(challengerName))
            throw new SameUserException();

        User challenger = usersTable.get(challengerName);
        User challenged = usersTable.get(challengedName);

        if(challenger == null || challenged == null) {
            throw new UserNotFoundException();
        }

        if(relationsGraph.nodesAreNotLinked(challenger, challenged))
            throw new NotFriendsException();

        if(!challenger.isLogged(challengerPort) || !challenged.isLogged())
            throw new NotLoggedException();

        //send challenge request to the other user
        String message = (REQUEST_CHALLENGE + " " + challengerName);
        byte[] challengeRequest = message.getBytes(StandardCharsets.UTF_8);

        DatagramPacket requestPacket = new DatagramPacket(challengeRequest, challengeRequest.length, challenged.getAddress(), challenged.getUDPPort());

        pendingChallenges.put(requestPacket.getSocketAddress(), new ChallengeInfo(challengedName, System.currentTimeMillis()));
        pendingChallenges.put(new InetSocketAddress(challenger.getAddress(), challenger.getUDPPort()), new ChallengeInfo(challengerName, System.currentTimeMillis()));

        return requestPacket;
    }

    /**
     * Creates the challenge and returns the confirmation message to be sent
     * back to the users
     * @param challengerAddress The user who started the challenge address
     * @param challengedAddress The user who has been challenged address
     * @return byte array containing the confirmation message and the match ID
     * @throws UserNotFoundException When one of the given address is not bounded to any user
     */
    byte[] getChallengeConfirm(SocketAddress challengerAddress, SocketAddress challengedAddress) throws UserNotFoundException, ChallengeRequestTimeoutException {
        ChallengeInfo challenger = pendingChallenges.get(challengerAddress);
        ChallengeInfo challenged = pendingChallenges.get(challengedAddress);
        pendingChallenges.remove(challengerAddress);
        pendingChallenges.remove(challengedAddress);

        if(challenged == null || challenger == null)
            throw new UserNotFoundException();

        long challengeInitialTime = challenged.timestamp;

        if(System.currentTimeMillis() - challengeInitialTime > Consts.CHALLENGE_REQUEST_TIMEOUT) {
            throw new ChallengeRequestTimeoutException();
        }

        int matchId = ChallengeHandler.instance.createChallenge(challenger.name, challenged.name);

        return (RESPONSE_OK + " " + matchId).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Invalidates the pending challenge related to the two user
     * @param challengerAddress The user who started the challenge address
     * @param challengedAddress The user who has been challenged address
     * @return The datagram packet to be sent to the user who started the challenge
     */
    DatagramPacket discardChallenge(SocketAddress challengerAddress, SocketAddress challengedAddress){
        pendingChallenges.remove(challengerAddress);
        pendingChallenges.remove(challengedAddress);

        byte[] errorMessage = Consts.RESPONSE_CHALLENGE_REFUSED.getBytes(StandardCharsets.UTF_8);
        return new DatagramPacket(errorMessage, errorMessage.length, challengerAddress);
    }


    /**
     * Class representing the user.
     * It keeps all the useful info and provides basic ops for the user
     *
     * Thread-safety is assured by the logic of class usage and of 'synchronized' when needed
     */
    static class User{
        private String name;
        private String password;
        transient private InetAddress loginAddress = null;
        private int TCPPort;
        private int UDPPort;
        private int score = 0;
        private int id;

        //counter is shared between multiple threads and instances
        private static final AtomicInteger idCounter = new AtomicInteger(); //every user has its id assigned at constructor time


        User(String name, String password){
            this.name = name;
            this.password = password;
            id = usersTable.size() + idCounter.getAndIncrement();
        }

        int getId() {
            return id;
        }

        /**
         * Updates the score of the user, retrieving the score of the pending matches
         */
        synchronized void updateScore(int amount){
            score += amount;
            if(score < 0)
                score = 0;
        }


        int getScore() {
            return score;
        }

        String getName() {
            return name;
        }

        InetAddress getAddress() {
            return loginAddress;
        }

        int getUDPPort() {
            return UDPPort;
        }

        boolean matches(String name, String password) {
            return this.name.equals(name) && this.password.equals(password);
        }

        boolean notMatches(String name, String password) {
            return !matches(name, password);
        }

        void login(InetAddress address, int TCPPort, int UDPPort) {
            loginAddress = address;
            this.TCPPort = TCPPort;
            this.UDPPort = UDPPort;
        }

        void logout() {
            loginAddress = null;
            UDPPort = 0;
        }

        boolean isLogged(InetAddress address, int TCPPort) {
            return loginAddress != null && loginAddress.equals(address) && this.TCPPort == TCPPort;
        }


        boolean isLogged(int UDPPort) {
            return loginAddress != null && this.UDPPort == UDPPort;
        }

        boolean isLogged(){
            return loginAddress != null;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof User) {
                return this.id == ((User) obj).id;
            }
            return super.equals(obj);
        }
    }

    /**
     * A non-oriented graph implemented with an adjacencyList
     * requires that user has an unique id to be used to access array location
     *
     * Thread safety assured by the use of vector
     */
    static class SimpleGraph{
        Vector<LinkedList<User>> adjacencyList = new Vector<>(); //i-sm element is the user with i as id

        //add a node to the graph
        void addNode(){
            adjacencyList.add(new LinkedList<>());
        }

        void addArch(User user1, User user2){
            adjacencyList.get(user1.getId()).add(user2);
            adjacencyList.get(user2.getId()).add(user1);
        }

        /**
         * @return true if the two users are linked
         */
        boolean nodesAreLinked(User user1, User user2){
            return adjacencyList.get(user1.getId()).contains(user2);
        }

        /**
         * @return true if the two user are NOT linked
         */
        boolean nodesAreNotLinked(User user1, User user2){
            return !nodesAreLinked(user1, user2);
        }

        /**
         * @return All the nodes linked to user
         */
        LinkedList<User> getLinkedNodes(User user){
            return new LinkedList<>(adjacencyList.get(user.getId()));
        }
    }

    /**
     * data class used to store info of pending challenges
     */
    private static class ChallengeInfo{
        private String name;
        private Long timestamp;

        private ChallengeInfo(String name, Long timestamp) {
            this.name = name;
            this.timestamp = timestamp;
        }
    }

    static class UserNotFoundException extends Exception {
    }

    static class AlreadyLoggedException extends Exception {
    }

    static class NotLoggedException extends Exception {
    }

    static class NotFriendsException extends Exception{
    }

    static class AlreadyFriendsException extends Exception {
    }

    static class SameUserException extends Exception{
    }

    static class ChallengeRequestTimeoutException extends Exception {
    }
}
