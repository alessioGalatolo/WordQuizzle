package Server;

import Commons.WQRegisterInterface;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;

/**
 * Implementation of WQRegisterInterface for RMI
 */
public class WQRegister extends RemoteServer implements WQRegisterInterface {

    @Override
    public boolean registerUser(String username, String password) throws RemoteException, WQRegisterInterface.UserAlreadyRegisteredException, WQRegisterInterface.InvalidPasswordException {
        UserDB.instance.addUser(username, password);
        return true;
    }
}
