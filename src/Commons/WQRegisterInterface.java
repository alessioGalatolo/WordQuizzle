package Commons;

import Server.WQRegister;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface WQRegisterInterface extends Remote {

    /**
     * Registers the user to the program database
     * @return The outcome of the operation
     * @throws WQRegister.UserAlreadyRegisteredException If the given username is already taken
     * @throws WQRegister.InvalidPasswordException If the password is blank or null
     */
    boolean registerUser(String username, String/*TODO: add some kind of encryption*/ password) throws RemoteException, WQRegister.UserAlreadyRegisteredException, WQRegister.InvalidPasswordException;


    class UserAlreadyRegisteredException extends Exception {}

    class InvalidPasswordException extends Exception {}
}
