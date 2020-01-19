package server;

import commons.WQRegisterInterface;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import static commons.Constants.*;
import static server.UserDBExceptions.*;
import static server.ChallengeExceptions.*;

final class Handler implements Runnable {
    private final SocketChannel socket;
    private final SelectionKey selectionKey;
    private final AtomicBoolean processed = new AtomicBoolean(false);
    private final ByteBuffer input = ByteBuffer.allocate(MAX_MESSAGE_LENGTH);
    private final ByteBuffer preOutput = ByteBuffer.allocate(INT_SIZE);
    private final ByteBuffer output = ByteBuffer.allocate(MAX_MESSAGE_LENGTH);
    private static final ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(Consts.SERVER_THREADS);
    private static final int IO_OPERATION = 0, PROCESSING = 2;
    private int state = IO_OPERATION;

    static void close(){
        if(!threadPool.isTerminated() && !threadPool.isShutdown())
            threadPool.shutdown();
    }

    Handler(Selector sel, SocketChannel c) throws IOException {
        socket = c;
        c.configureBlocking(false);
        // Optionally try first read now
        selectionKey = socket.register(sel, 0);
        selectionKey.attach(this);
        selectionKey.interestOps(SelectionKey.OP_READ);
        sel.wakeup();
    }

    @Override
    public void run() {
        try {
            if(state == IO_OPERATION) {
                if (selectionKey.isValid() && selectionKey.isWritable())
                    send();
                else if (selectionKey.isValid() && selectionKey.isReadable())
                    read();
            }else if(state == PROCESSING && processed.get()){
                state = IO_OPERATION;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void read() throws IOException {
        int read = socket.read(input);
        if(read == -1)
            selectionKey.cancel();
        else if(read == 0) {
            //nothing to read
            selectionKey.interestOps(SelectionKey.OP_WRITE);
        }else{
            selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        }
    }

    private void send() throws IOException {

        if(!processed.get()) {
            state = PROCESSING;
            threadPool.execute(this::process);
        } else if(preOutput.hasRemaining()){
            socket.write(preOutput);
            selectionKey.interestOps(SelectionKey.OP_WRITE);
        } else {
            socket.write(output);
            if (!output.hasRemaining()) {
                selectionKey.interestOps(SelectionKey.OP_READ);
                output.clear();
                input.clear();
                preOutput.clear();
                processed.set(false);
            }
        }
    }

    private void process(){
        input.flip();
        String message = new String(input.array(), input.arrayOffset(), input.remaining(), StandardCharsets.UTF_8);
        String[] messageFragments = message.split(" ");

        //variables to be used inside switch statement
        String response = "";
        int matchId = 0;
        String username;

        try {
            InetAddress clientAddress = ((InetSocketAddress) ((SocketChannel) selectionKey.channel()).getRemoteAddress()).getAddress();
            int clientPort = ((InetSocketAddress) ((SocketChannel) selectionKey.channel()).getRemoteAddress()).getPort();

            switch (messageFragments[0]) {
                case REQUEST_LOGIN:
                    if (messageFragments.length > 3)
                        UserDB.instance.logUser(messageFragments[1], messageFragments[2],
                                clientAddress,
                                clientPort,
                                Integer.parseInt(messageFragments[3]));
                    else
                        UserDB.instance.logUser(messageFragments[1], messageFragments[2],
                                clientAddress,
                                clientPort);

                    response = RESPONSE_OK;
                    break;

                case REQUEST_LOGOUT:
                    UserDB.instance.logoutUser(messageFragments[1], clientAddress, clientPort);
                    response = RESPONSE_OK;
                    break;

                case REQUEST_ADD_FRIEND:
                    UserDB.instance.addFriendship(messageFragments[1], messageFragments[2], clientAddress, clientPort);
                    response = RESPONSE_OK;

                    break;

                case REQUEST_FRIEND_LIST:
                    response = UserDB.instance.getFriends(messageFragments[1], clientAddress, clientPort);
                    break;

                case REQUEST_CHALLENGE:
                    response = Consts.RESPONSE_ILLEGAL_REQUEST; //no challenge request allowed via TCP
                    break;

                case REQUEST_SCORE:
                    response = String.valueOf(UserDB.instance.getScore(messageFragments[1], clientAddress, clientPort));
                    break;

                case REQUEST_RANKINGS:
                    response = UserDB.instance.getRanking(messageFragments[1], clientAddress, clientPort);
                    break;

                /*
                 * User already received first word so he sends the translation of the last word
                 * and wants the next word
                 */
                case REQUEST_NEXT_WORD:
                    //Check correctness of translated word, then send new word
                    matchId = Integer.parseInt(messageFragments[1]);
                    username = messageFragments[2];
                    String translatedWord = messageFragments[3];
                    String wellTranslatedWord = ChallengeHandler.instance.checkTranslation(matchId, username, translatedWord);
                    boolean outcome = wellTranslatedWord != null && wellTranslatedWord.toLowerCase().equals(translatedWord.toLowerCase());
                    response = Consts.getResponseTranslationServer(matchId, translatedWord, wellTranslatedWord, outcome);

                    response += "\n";

                case REQUEST_READY_FOR_CHALLENGE:
                    //client is ready for a match
                    matchId = Integer.parseInt(messageFragments[1]);
                    username = messageFragments[2];
                    String nextWord = ChallengeHandler.instance.getNextWord(matchId, username);
                    response += Consts.getResponseNextWord(matchId, nextWord);

                    response += "\n" + Consts.getResponseTimeRemaining(ChallengeHandler.instance.getTime(matchId));
                    break;
                case REQUEST_CHALLENGE_RECAP:
                    matchId = Integer.parseInt(messageFragments[1]);
                    output.clear();
                    threadPool.execute(new RecapTask(matchId, output, preOutput, processed, threadPool));
                    return;


                default:
                    System.err.println("TCP, unknown command: " + messageFragments[0]);
                    response = Consts.RESPONSE_UNKNOWN_REQUEST;
                    break;
            }

            //set error response in case of exception
        } catch (UserNotFoundException e) {
            response = Consts.RESPONSE_USER_NOT_FOUND;
        } catch (WQRegisterInterface.InvalidPasswordException e) {
            response = Consts.RESPONSE_WRONG_PASSWORD;
        } catch (AlreadyLoggedException e) {
            response = Consts.RESPONSE_ALREADY_LOGGED;
        } catch (AlreadyFriendsException e) {
            response = Consts.RESPONSE_ALREADY_FRIENDS;
        } catch (NotLoggedException e) {
            response = Consts.RESPONSE_NOT_LOGGED;
        } catch (SameUserException e) {
            response = Consts.RESPONSE_SAME_USER;
        } catch (UnknownUsernameException e) {
            response = Consts.RESPONSE_UNKNOWN_USERNAME;
        } catch (GameTimeoutException e) {
                response = RESPONSE_CHALLENGE_TIMEOUT + "\n";
        } catch (EndOfMatchException e) {
            response += RESPONSE_WAITING_OTHER_USER;
        } catch (IndexOutOfBoundsException e){
            //client sent a message without proper format
            response = Consts.RESPONSE_WRONG_FORMAT + ": " + message;
        } catch (IOException e) {
            e.printStackTrace();
        }

        output.clear();
        output.put(response.getBytes(StandardCharsets.UTF_8));
        output.flip();

        //sending first the size of the buffer to be allocated
        preOutput.putInt(output.remaining());
        preOutput.flip();
        processed.set(true);

    }

}
