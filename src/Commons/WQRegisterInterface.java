package Commons;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface WQRegisterInterface extends Remote {

    /**
     * Registers the user to the program database
     * @throws UserAlreadyRegisteredException If the given username is already taken
     * @throws InvalidPasswordException If the password is blank or null
     */
    void registerUser(String username, String password) throws RemoteException, UserAlreadyRegisteredException, InvalidPasswordException;


    class UserAlreadyRegisteredException extends Exception {}

    class InvalidPasswordException extends Exception {}
}
