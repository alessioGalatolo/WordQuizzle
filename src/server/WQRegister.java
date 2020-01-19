package server;

import commons.WQRegisterInterface;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;

/**
 * Implementation of WQRegisterInterface for RMI
 */
class WQRegister extends RemoteServer implements WQRegisterInterface {

    @Override
    public void registerUser(String username, String password) throws RemoteException, WQRegisterInterface.UserAlreadyRegisteredException, WQRegisterInterface.InvalidPasswordException {
        UserDB.instance.addUser(username, password);
    }
}
