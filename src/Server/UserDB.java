package Server;

import Commons.WQRegisterInterface;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

//package private class, the class and the member can only be called by the Server components
class UserDB {

    static private ConcurrentHashMap<String, User> usersList = new ConcurrentHashMap<>();

    static private SimpleGraph relationsGraph = new SimpleGraph();


    static void addUser(String username, String password) throws WQRegisterInterface.UserAlreadyRegisteredException, WQRegisterInterface.InvalidPasswordException {
        if(usersList.containsKey(username))
            throw new WQRegisterInterface.UserAlreadyRegisteredException();
        if(password == null || password.isBlank())//isBlank() requires java 11
            throw new WQRegisterInterface.InvalidPasswordException();

        usersList.put(username, new User(username, password));

        relationsGraph.addNode();
    }

    static void logUser(String name, String password) throws UserNotFoundException, WQRegisterInterface.InvalidPasswordException, AlreadyLoggedException {
        User user = usersList.get(name);
        if(user == null)
            throw new UserNotFoundException();
        if(user.notMatches(name, password))
            throw new WQRegisterInterface.InvalidPasswordException();

        //might be useless
//        if(user.isLogged())
//            throw new AlreadyLoggedException();

        user.login();
    }

    static void logoutUser(String name, String password) throws WQRegisterInterface.InvalidPasswordException, UserNotFoundException, NotLoggedException {
        User user = usersList.get(name);
        if(user == null)
            throw new UserNotFoundException();
        if(user.notMatches(name, password))
            throw new WQRegisterInterface.InvalidPasswordException();

        //might be useless
//        if(user.isNotLogged())
//            throw new NotLoggedException();

        user.logout();
    }

    static void addFriendship(String nick1, String nick2) throws UserNotFoundException {
        User user1 = usersList.get(nick1);
        User user2 = usersList.get(nick2);

        if(user1 == null || user2 == null)
            throw new UserNotFoundException();

        relationsGraph.addArch(user1, user2);
    }

    static String getFriends(String name) throws UserNotFoundException {
        User friendlyUser = usersList.get(name);

        if (friendlyUser == null)
            throw new UserNotFoundException();


        LinkedList<User> friends = relationsGraph.getLinkedNodes(friendlyUser);
        Gson gson = new Gson();
        return gson.toJson(friends);
    }

    static void challegeFriend(String challengerName, String challengedName) throws UserNotFoundException, NotFriendsException {
        User challenger = usersList.get(challengerName);
        User challenged = usersList.get(challengedName);

        if(challenger == null || challenged == null)
            throw new UserNotFoundException();

        if(relationsGraph.nodesAreNotLinked(challenger, challenged))
            throw new NotFriendsException();

        //TODO: start challenge

    }

    static int getScore(String name){
        User user = usersList.get(name);

        return user.getScore();
    }

    static String getRanking(String name){
        User user = usersList.get(name);
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
        private boolean logged = false;
        private int score = 0;
        private int id;

        //counter is shared between multiple threads and instances
        private static final AtomicInteger count = new AtomicInteger(0); //every user has its id assigned at constructor time

        User(String name, String password){
            this.name = name;
            this.password = password;
            id = count.incrementAndGet();
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

        void login() {
            logged = true;
        }

        void logout() {
            logged = false;
        }

        boolean isLogged() {
            return logged;
        }

        boolean isNotLogged() {
            return !logged;
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

    private static class UserNotFoundException extends Exception {
    }

    private static class AlreadyLoggedException extends Exception {
    }

    private static class NotLoggedException extends Exception {
    }

    private static class NotFriendsException extends Exception{
    }
}
