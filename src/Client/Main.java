package Client;

import Commons.WQRegisterInterface;
import Server.Consts;
import Server.WQRegister;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Main {
    public static void main(String[] args) {
        try {
            Registry r = LocateRegistry.getRegistry(Integer.parseInt(args[0]));
            WQRegisterInterface serverObject = (WQRegisterInterface) r.lookup(Consts.WQ_STUB_NAME); //get remote object

            try {
                serverObject.registerUser("User", "Password");
            } catch (WQRegister.UserAlreadyRegisteredException e) {
                e.printStackTrace();
            } catch (WQRegister.InvalidPasswordException e) {
                e.printStackTrace();
            }


        } catch (RemoteException | NotBoundException e) {
            e.printStackTrace();
        }

    }
}
