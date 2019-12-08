package Server;

import Commons.WQRegisterInterface;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

public class WQRegister extends RemoteServer implements WQRegisterInterface {

    private HashMap<String, String> users = new HashMap<>();
    private ReentrantLock usersLock = new ReentrantLock();

    @Override
    public boolean registerUser(String username, String password) throws RemoteException, WQRegisterInterface.UserAlreadyRegisteredException, WQRegisterInterface.InvalidPasswordException {
        //TODO: make thread safe
        usersLock.lock();
        if(users.containsKey(username)) {
            usersLock.unlock();
            throw new WQRegisterInterface.UserAlreadyRegisteredException();
        }
        if(password == null || password.isBlank()){
            usersLock.unlock();
            throw new WQRegisterInterface.InvalidPasswordException();
        }
        users.put(username, password);
        usersLock.unlock();
        return true;
    }
}
