package Server;

import Commons.WQRegisterInterface;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

//package private class, the class and the member can only be called by the Server components
class UserDB {

    static private HashMap<String, User> usersList = new HashMap<>(); //TODO: replace with concurrentHashMap
    static private ReentrantLock usersLock = new ReentrantLock();

    static private SimpleGraph relationsGraph = new SimpleGraph();


    static void addUser(String username, String password) throws WQRegisterInterface.UserAlreadyRegisteredException, WQRegisterInterface.InvalidPasswordException {
        usersLock.lock();
        if(usersList.containsKey(username)) {
            usersLock.unlock();
            throw new WQRegisterInterface.UserAlreadyRegisteredException();
        }
        if(password == null || password.isBlank()){
            usersLock.unlock();
            throw new WQRegisterInterface.InvalidPasswordException();
        }
        usersList.put(username, new User(username, password));
        usersLock.unlock();

        relationsGraph.addNode();
    }

    static void logUser(String name, String password) throws UserNotFoundException, WQRegisterInterface.InvalidPasswordException, AlreadyLoggedException {
        usersLock.lock();
        User user = usersList.get(name);
        usersLock.unlock();
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
        usersLock.lock();
        User user = usersList.get(name);
        usersLock.unlock();
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
        usersLock.lock();
        User user1 = usersList.get(nick1);
        User user2 = usersList.get(nick2);
        usersLock.unlock();

        if(user1 == null || user2 == null)
            throw new UserNotFoundException();

        relationsGraph.addArch(user1.getId(), user2.getId());
    }

    static String getFriends(String name) throws UserNotFoundException {
        usersLock.lock();
        User friendlyUser = usersList.get(name);
        usersLock.unlock();

        if (friendlyUser == null)
            throw new UserNotFoundException();

        //very very bad, O(n) algorithm where n is user counter
        //TODO: improve time consumption
        int[] friends = relationsGraph.getLinkedNodes(friendlyUser.getId());
        int i = 0;
        //TODO: will not work until friends keep having some empty parts
        LinkedList<String> friendList = new LinkedList<>();
        for(User user: usersList.values()){
            if(user.getId() == friends[i]){
                friendList.add(user.name);
                i++;
            }
        }
        Gson gson = new Gson();
        return gson.toJson(friendList);
    }

    static void challegeFriend(String challengerName, String challengedName) throws UserNotFoundException, NotFriendsException {
        usersLock.lock();
        User challenger = usersList.get(challengerName);
        User challenged = usersList.get(challengedName);
        usersLock.unlock();

        if(challenger == null || challenged == null)
            throw new UserNotFoundException();

        if(relationsGraph.nodesAreNotLinked(challenger.getId(), challenged.getId()))
            throw new NotFriendsException();

        //TODO: start challenge

    }

    static int getScore(String name){
        usersLock.lock();
        User user = usersList.get(name);
        usersLock.unlock();

        return user.getScore();
    }

    static String getRanking(String name){
        usersLock.lock();
        User user = usersList.get(name);
        usersLock.unlock();

        //TODO: return ranking
        return null;
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

    static class SimpleGraph{
        private int currentNodes = 0;
        private int maxNodes = 10;
        private boolean[][] adjacencyMatrix = new boolean[maxNodes][maxNodes];

        SimpleGraph(){
            for(int i = 0; i < maxNodes; i++)
                Arrays.fill(adjacencyMatrix[i], false);
        }

        //needs to be thread-safe
        synchronized void addNode(){
            currentNodes++;

            //if short in size, expand array size
            if(currentNodes > maxNodes){
                maxNodes *= 2;
                boolean[][] oldMatrix = adjacencyMatrix;
                adjacencyMatrix = new boolean[maxNodes][maxNodes];
                for(int i = 0; i < maxNodes; i++){
                    for(int j = 0; j < maxNodes; j++){
                        if(i < maxNodes / 2 && j < maxNodes / 2){
                            adjacencyMatrix[i][j] = oldMatrix[i][j];
                        }else {
                            adjacencyMatrix[i][j] = false;
                        }
                    }
                }
            }
        }

        synchronized void addArch(int i, int j){
            adjacencyMatrix[i][j] = true;
            adjacencyMatrix[j][i] = true;
        }

        synchronized int[] getLinkedNodes(int id) {
            int[] nodes = new int[currentNodes];
            int j = 0;
            for(int i = 0; i < currentNodes; i++){
                if(adjacencyMatrix[id][i]){
                    nodes[j] = i;
                    j++;
                }
            }
            return nodes;
            //TODO: reduce size of nodes
        }

        synchronized boolean nodesAreLinked(int i, int j){
            return adjacencyMatrix[i][j];
        }

        //doesn't need to be synchronized
        boolean nodesAreNotLinked(int i, int j) {
            return !nodesAreLinked(i, j);
        }

        @Override
        public String toString() {
            StringBuilder output = new StringBuilder();
            for(int i = 0; i < maxNodes; i++){
                for (int j = 0; j < maxNodes; j++){
                    output.append(adjacencyMatrix[i][j]);
                    output.append("\t");
                }
                output.append("\n");
            }
            return output.toString();
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
