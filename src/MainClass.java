import Client.AutoClient;
import Server.Consts;

public class MainClass {

    public static void main(String[] args) {
        for(int i = 0; i < Consts.N_CLIENTS; i++){
            int finalI = i;
            new Thread(() -> AutoClient.main(new String[]{Consts.BASE_USERNAME + finalI})).start();
        }
    }

}
