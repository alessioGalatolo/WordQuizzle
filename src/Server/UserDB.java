package Server;

import Commons.WQRegisterInterface;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

//package private class, the class and the member can only be called by the Server components
class UserDB {

    static private HashMap<String, User> usersList = new HashMap<>();
    static private ReentrantLock usersLock = new ReentrantLock();

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
    }

    static void logUser(String name, String password) throws UserNotFoundException, WQRegisterInterface.InvalidPasswordException {
        usersLock.lock();
        User user = usersList.get(name);
        usersLock.unlock();
        if(user == null)
            throw new UserNotFoundException();
        if(user.notMatches(name, password))
            throw new WQRegisterInterface.InvalidPasswordException();

        user.login();
    }

    static class User{
        private String name;
        private String password;
        private boolean logged = false;

        User(String name, String password){
            this.name = name;
            this.password = password;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof User) {
                return name.equals(((User) obj).name) && password.equals(((User) obj).password);
            }
            return super.equals(obj);
        }

        boolean matches(String name, String password) {
            return this.name.equals(name) && this.password.equals(password);
        }

        public boolean notMatches(String name, String password) {
            return !(this.name.equals(name) && this.password.equals(password));
        }

        public void login() {
            logged = true;
        }
    }

    private static class UserNotFoundException extends Throwable {
    }
}
