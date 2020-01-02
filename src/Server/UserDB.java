package Server;

import Commons.WQRegisterInterface;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class representing and collecting the users and their relations. Handles all the operations involving users
 * The class has only static method so they can be called easily where needed
 * Package private class, the class and the member can only be called by the Server components
 */
class UserDB {

    static private ConcurrentHashMap<String, User> usersTable = new ConcurrentHashMap<>();
    static private SimpleGraph relationsGraph = new SimpleGraph();


    //TODO: add save to file

    /**
     * Add a user the the database
     * @param username
     * @param password
     * @throws WQRegisterInterface.UserAlreadyRegisteredException When the username is already present in the DB
     * @throws WQRegisterInterface.InvalidPasswordException When the password is blank or null
     */
    static void addUser(String username, String password) throws WQRegisterInterface.UserAlreadyRegisteredException, WQRegisterInterface.InvalidPasswordException {
        if(usersTable.containsKey(username))
            throw new WQRegisterInterface.UserAlreadyRegisteredException();
        if(password == null || password.isBlank())//isBlank() requires java 11
            throw new WQRegisterInterface.InvalidPasswordException();

        usersTable.put(username, new User(username, password));

        relationsGraph.addNode();
    }

    /**
     * Logs in the user to the DB
     * @param username The name of the user
     * @param password His password
     * @param address His current IP address
     * @param UDPPort His preferred UDP port
     * @throws UserNotFoundException When the username is not to be found in the DB
     * @throws WQRegisterInterface.InvalidPasswordException When the given password doesn't match the original one
     * @throws AlreadyLoggedException When the user is already logged
     */
    static void logUser(String username, String password, InetAddress address, int UDPPort) throws UserNotFoundException, WQRegisterInterface.InvalidPasswordException, AlreadyLoggedException {
        User user = usersTable.get(username);
        if(user == null)
            throw new UserNotFoundException();
        if(user.isLogged())
            throw new AlreadyLoggedException();
        if(user.notMatches(username, password))
            throw new WQRegisterInterface.InvalidPasswordException();

        user.login(address, UDPPort);
    }

    //the above method when using a default udp port
    static void logUser(String name, String password, InetAddress address) throws UserNotFoundException, WQRegisterInterface.InvalidPasswordException, AlreadyLoggedException {
        logUser(name, password, address, Consts.UDP_PORT);
    }

    /**
     * Logs out the user
     * @param username
     * @throws UserNotFoundException If the user could not be found
     * @throws NotLoggedException If the user is not logged in (currently disabled)
     */
    static void logoutUser(String username) throws UserNotFoundException, NotLoggedException {
        User user = usersTable.get(username);
        if(user == null)
            throw new UserNotFoundException();

        //commented as it's only a minor error
//        if(user.isNotLogged())
//            throw new NotLoggedException();

        user.logout();
    }


    /**
     * Creates a friendship between the given user
     * @param nick1 The user who requested the friendship
     * @param nick2 The user nick1 wants to be friend with
     * @throws UserNotFoundException If one of the user were not found in the DB
     * @throws AlreadyFriendsException If the two users were already friends
     * @throws NotLoggedException If the requesting user is not logged
     * @throws SameUserException If the nick provided are the same
     */
    static void addFriendship(String nick1, String nick2) throws UserNotFoundException, AlreadyFriendsException, NotLoggedException, SameUserException {
        if(nick1.equals(nick2))
            throw new SameUserException();

        User user1 = usersTable.get(nick1);
        User user2 = usersTable.get(nick2);

        if(user1 == null || user2 == null)
            throw new UserNotFoundException();
        if(user1.isNotLogged())
            throw new NotLoggedException();
        if(relationsGraph.nodesAreLinked(user1, user2))
            throw new AlreadyFriendsException();

        relationsGraph.addArch(user1, user2);
    }

    /**
     * Retrieves the friend list of the given user
     * @param name The username whose friends to return
     * @return The JSON string of a linkedList of users
     * @throws UserNotFoundException If the given user were not found
     * @throws NotLoggedException If the given user is not logged in
     */
    static String getFriends(String name) throws UserNotFoundException, NotLoggedException {
        User friendlyUser = usersTable.get(name);

        if (friendlyUser == null)
            throw new UserNotFoundException();
        if(friendlyUser.isNotLogged())
            throw new NotLoggedException();


        LinkedList<User> friends = relationsGraph.getLinkedNodes(friendlyUser);
        //TODO: maybe return a JSON array of usernames
        Gson gson = new Gson();
        return gson.toJson(friends);
    }

    /**
     * Sends a challenge request to the challenged user, instantiates the challenge and send confirmation messages through udp
     * @param challengerName
     * @param challengedName
     * @param datagramSocket The UDP socket of the server
     * @throws UserNotFoundException If the user were not found
     * @throws NotFriendsException If the two users are not friends
     * @throws NotLoggedException If the challenger is not logged
     * @throws SameUserException If the two user are the same
     */
    static void challengeFriend(String challengerName, String challengedName, DatagramSocket datagramSocket) throws UserNotFoundException, NotFriendsException, NotLoggedException, SameUserException {
        if(challengedName.equals(challengerName))
            throw new SameUserException();

        User challenger = usersTable.get(challengerName);
        User challenged = usersTable.get(challengedName);

        if(challenger == null || challenged == null) {
            throw new UserNotFoundException();
        }

        if(relationsGraph.nodesAreNotLinked(challenger, challenged))
            throw new NotFriendsException();

        if(challenger.isNotLogged() || challenged.isNotLogged())
            throw new NotLoggedException();

        //TODO: move outside of database
        //send challenge request to the other user
        byte[] challengeRequest = (Consts.REQUEST_CHALLENGE + " " + challengerName).getBytes(StandardCharsets.UTF_8); //TODO: check correct spacing
        DatagramPacket packet = new DatagramPacket(challengeRequest, challengeRequest.length, challenged.getAddress(), challenged.getUDPPort());

        try {
            datagramSocket.send(packet);


            //wait for ok response
            try {
                datagramSocket.receive(packet);
                String response = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                if(response.toLowerCase().startsWith(Consts.RESPONSE_OK.toLowerCase())){
                    //sending confirmation to both player with match ID
                    int matchId = ChallengeHandler.getInstance().createChallenge(challengerName, challengedName);
                    byte[] confirmationResponse = (Consts.RESPONSE_OK + " " + matchId).getBytes(StandardCharsets.UTF_8);
                    packet = new DatagramPacket(confirmationResponse, confirmationResponse.length, challenger.getAddress(), challenger.getUDPPort());
                    datagramSocket.send(packet);
                    packet = new DatagramPacket(confirmationResponse, confirmationResponse.length, challenged.getAddress(), challenged.getUDPPort());
                    datagramSocket.send(packet);
                    //TODO: add matchId to both player list of challenges

                    usersTable.get(challengerName).addMatch(matchId);
                    usersTable.get(challengedName).addMatch(matchId);

                }else{
                    sendErrorMessage(datagramSocket, challenger);
                    sendErrorMessage(datagramSocket, challenged);
                }

            }catch (SocketTimeoutException e){
                sendErrorMessage(datagramSocket, challenger);
                sendErrorMessage(datagramSocket, challenged);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private static void sendErrorMessage(DatagramSocket datagramSocket, User user) throws IOException {
        //no response
        byte[] errorMessage = Consts.RESPONSE_CHALLENGE_REFUSED.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(errorMessage, errorMessage.length, user.getAddress(), user.getUDPPort());

        datagramSocket.send(packet);
        //end of communication
    }

    /**
     * Retrieves the score of the given user
     * @param name The name of the user
     * @return The score of the given user
     */
    static int getScore(String name){
        User user = usersTable.get(name);

        return user.getScore();
    }

    /**
     * Retrieves the ranking by score of all the friends of the given user
     * @param name The name of the user
     * @return A json object of a string array with name and ranking of each user
     */
    static String getRanking(String name){
        User user = usersTable.get(name);
        User[] friends = relationsGraph.getLinkedNodes(user).toArray(new User[0]); //get array for faster access
        Arrays.sort(friends, Comparator.comparingInt(User::getScore));//sort by the score
        String[] ranking = new String[friends.length]; //ranking with name and score

        for(int i = 0; i < ranking.length; i++){
            ranking[i] = friends[i].getName() + "\t" + friends[i].getScore();
        }
        Gson gson = new Gson();
        return gson.toJson(ranking);
    }


    /**
     * Class representing the user.
     * It keeps all the useful info and provides basic ops for the user
     */
    static class User{
        private String name;
        private String password;
        private InetAddress loginAddress = null;
        private Vector<Integer> matchList = new Vector<>();
        private int UDPPort;
        private int score = 0;
        private int id;

        //counter is shared between multiple threads and instances
        private static final AtomicInteger idCounter = new AtomicInteger(0); //every user has its id assigned at constructor time


        User(String name, String password){
            this.name = name;
            this.password = password;
            id = idCounter.getAndIncrement();
        }

        int getId() {
            return id;
        }


        //TODO: synchronized??
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

        void addToScore(int amount){
            //TODO: can score be negative?
            if(amount > 0)
                score += amount;
        }

        boolean matches(String name, String password) {
            return this.name.equals(name) && this.password.equals(password);
        }

        boolean notMatches(String name, String password) {
            return !matches(name, password);
        }

        void login(InetAddress address, int UDPPort) {
            loginAddress = address;
            this.UDPPort = UDPPort;
        }

        void logout() {
            loginAddress = null;
            UDPPort = 0;
        }

        boolean isLogged() {
            return loginAddress != null;
        }

        boolean isNotLogged() {
            return loginAddress == null;
        }

        void addMatch(int matchId) {
            matchList.add(matchId);
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

        boolean nodesAreLinked(User user1, User user2){
            return adjacencyList.get(user1.getId()).contains(user2);
        }

        boolean nodesAreNotLinked(User user1, User user2){
            return !nodesAreLinked(user1, user2);
        }

        //returns all the nodes linked to user
        LinkedList<User> getLinkedNodes(User user){
            return adjacencyList.get(user.getId());
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
}
