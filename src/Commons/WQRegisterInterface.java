package Commons;

import Server.WQRegister;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface WQRegisterInterface extends Remote {

    //returns the outcome of the request
    boolean registerUser(String username, String/*TODO: add some kind of encryption*/ password) throws RemoteException, WQRegister.UserAlreadyRegisteredException, WQRegister.InvalidPasswordException;


    class UserAlreadyRegisteredException extends Exception {}

    class InvalidPasswordException extends Exception {}
}
