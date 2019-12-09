package Server;

import Commons.WQRegisterInterface;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;

public class WQRegister extends RemoteServer implements WQRegisterInterface {

    @Override
    public boolean registerUser(String username, String password) throws RemoteException, WQRegisterInterface.UserAlreadyRegisteredException, WQRegisterInterface.InvalidPasswordException {
        UserDB.addUser(username, password);
        return true;
    }
}
