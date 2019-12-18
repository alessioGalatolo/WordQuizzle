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

//package private class, the class and the member can only be called by the Server components
class UserDB {

    static private ConcurrentHashMap<String, User> usersTable = new ConcurrentHashMap<>();
    static private SimpleGraph relationsGraph = new SimpleGraph();


    //TODO: add save to file

    static void addUser(String username, String password) throws WQRegisterInterface.UserAlreadyRegisteredException, WQRegisterInterface.InvalidPasswordException {
        if(usersTable.containsKey(username))
            throw new WQRegisterInterface.UserAlreadyRegisteredException();
        if(password == null || password.isBlank())//isBlank() requires java 11
            throw new WQRegisterInterface.InvalidPasswordException();

        usersTable.put(username, new User(username, password));

        relationsGraph.addNode();
    }

    static void logUser(String name, String password, InetAddress address, int UDPPort) throws UserNotFoundException, WQRegisterInterface.InvalidPasswordException, AlreadyLoggedException {
        User user = usersTable.get(name);
        if(user == null)
            throw new UserNotFoundException();
        if(user.notMatches(name, password))
            throw new WQRegisterInterface.InvalidPasswordException();

        //might be useless
//        if(user.isLogged())
//            throw new AlreadyLoggedException();

        user.login(address, UDPPort);
    }

    static void logUser(String name, String password, InetAddress address) throws UserNotFoundException, WQRegisterInterface.InvalidPasswordException, AlreadyLoggedException {
        logUser(name, password, address, Consts.UDP_PORT);
    }

    static void logoutUser(String name) throws UserNotFoundException, NotLoggedException {
        User user = usersTable.get(name);
        if(user == null)
            throw new UserNotFoundException();

        //might be useless
//        if(user.isNotLogged())
//            throw new NotLoggedException();

        user.logout();
    }


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

    //TODO: a non logged user should not be able to ask this question
    static String getFriends(String name) throws UserNotFoundException, NotLoggedException {
        User friendlyUser = usersTable.get(name);

        if (friendlyUser == null)
            throw new UserNotFoundException();
        if(friendlyUser.isNotLogged())
            throw new NotLoggedException();


        LinkedList<User> friends = relationsGraph.getLinkedNodes(friendlyUser);
        Gson gson = new Gson();
        return gson.toJson(friends);
    }

    static void challengeFriend(String challengerName, String challengedName, DatagramSocket datagramSocket) throws UserNotFoundException, NotFriendsException, NotLoggedException, SameUserException {
        if(challengedName.equals(challengerName))
            throw new SameUserException();

        User challenger = usersTable.get(challengerName);
        User challenged = usersTable.get(challengedName);

        if(challenger == null || challenged == null)
            throw new UserNotFoundException();

        if(relationsGraph.nodesAreNotLinked(challenger, challenged))
            throw new NotFriendsException();

        if(challenger.isNotLogged() || challenged.isNotLogged())
            throw new NotLoggedException();

        //send challenge request to the other user
        byte[] challengeRequest = (Consts.REQUEST_CHALLENGE + " " + challengerName).getBytes(StandardCharsets.UTF_8); //TODO: check correct spacing
        DatagramPacket packet = new DatagramPacket(challengeRequest, challengeRequest.length, challenged.getAddress(), challenged.getUDPPort());

        try {
            datagramSocket.send(packet);


            //wait for ok response
            try {
                datagramSocket.receive(packet);
                //TODO: check response

                //TODO: assign matchId and send confirmation to both player
                //TODO: create challenge

            }catch (SocketTimeoutException e){
                //no response
                byte[] errorMessage = Consts.RESPONSE_CHALLENGE_REFUSED.getBytes(StandardCharsets.UTF_8);
                packet = new DatagramPacket(errorMessage, errorMessage.length, challenger.getAddress(), challenger.getUDPPort());

                datagramSocket.send(packet);
                //end of communication
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    static int getScore(String name){
        User user = usersTable.get(name);

        return user.getScore();
    }

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


    static class User{
        private String name;
        private String password;
        private InetAddress loginAddress = null;
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

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof User) {
                return this.id == ((User) obj).id;
            }
            return super.equals(obj);
        }
    }


    //a non-oriented graph implemented with an adjacencyList
    //requires that user has an unique id to be used to access array location
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
