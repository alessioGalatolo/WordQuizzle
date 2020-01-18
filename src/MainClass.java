import Client.AutoClient;
import Client.AutoClientTesting;
import Client.HumanClient;
import Server.Consts;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainClass {

    public static void main(String[] args) {
        try(BufferedReader input = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("Welcome to Word Quizzle testing!");
            System.out.println("Please enter 'auto' for a randomized test or 'human' for a human driven testing");
            String response;
            while (true) {
                response = input.readLine();
                if (response.equals("auto")) {
                    System.out.println("In 5 seconds I'm going to launch the server and " + Consts.N_CLIENTS + " instances of clients");
                    System.out.println("Write quit at any moment to terminate the server");

                    for (int i = 0; i < Consts.N_CLIENTS; i++) {
                        int finalI = i;
                        new Thread(() -> {
                            try {
                                //waiting server to start up
                                Thread.sleep(5000);
                                /*
                                    WARNING: with the growth in size of N_CLIENT this trick won't work anymore
                                    It will require, in fact, the launch of server and clients in 2 different times
                                 */
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            AutoClientTesting.main(new String[]{Consts.BASE_USERNAME + finalI, "test"});
                        }).start();
                    }

                    Server.Main.main(new String[0]);
                    //server must be in main thread to allow for console input and termination

                    return;
                } else if (response.equals("human")) {
                    System.out.println("I'm going to launch the server and a client which will emulate human behaviour");
                    Thread server = new Thread(() -> Server.Main.main(new String[]{"test"}));
                    server.start();

                    //waiting server to start up
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    Thread otherClient = new Thread(new AutoClient());
                    otherClient.start();

                    System.out.println("Now I'm going to launch a human client\n");
                    System.out.println("For the interaction with other users please use the name " + Consts.BASE_USERNAME);
                    System.out.println("Ex: sfida <your username> " + Consts.BASE_USERNAME);
                    HumanClient.main(new String[0]);

                    otherClient.interrupt();
                    otherClient.join();
                    server.interrupt();
                    return;
                } else {
                    System.out.println("Not recognized, please try again");
                }
            }



        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

}
