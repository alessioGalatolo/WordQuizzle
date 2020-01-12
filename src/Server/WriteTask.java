package Server;

import Commons.WQRegisterInterface;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * Class that does all the possible writing work
 */
class WriteTask implements Runnable {

    private SelectionKey selectionKey;

    WriteTask(SelectionKey currentSelectionKey) {
        selectionKey = currentSelectionKey;
    }

    @Override
    public void run() {
        //client is ready to receive data
        //executing requested command then sending response

        try {

            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
            //socketChannel.getRemoteAddress();

            //will contain the response to be written to the client at the end of the request
            ByteBuffer byteBuffer;

            if (selectionKey.attachment() != null) {
                if (selectionKey.attachment() instanceof ByteBuffer) {
                    //previous write was incomplete
                    byteBuffer = (ByteBuffer) selectionKey.attachment();

                } else if (selectionKey.attachment() instanceof byte[]) {
                    //previous operation was a read, serving request then sending response
                    String message = new String((byte[]) selectionKey.attachment(), StandardCharsets.UTF_8);
                    System.out.println("TCP received: " + message);
                    String[] messageFragments = message.split(" ");

                    //variables to be used inside switch statement
                    String response = "";
                    int matchId = 0;

                    //TODO: move actions taken from write task to read task
                    try {
                        switch (messageFragments[0]) {
                            case Consts.REQUEST_LOGIN:
                                if (messageFragments.length > 3)
                                    UserDB.instance.logUser(messageFragments[1], messageFragments[2],
                                            ((InetSocketAddress) socketChannel.getRemoteAddress()).getAddress(),
                                            Integer.parseInt(messageFragments[3]));
                                else
                                    UserDB.instance.logUser(messageFragments[1], messageFragments[2], ((InetSocketAddress) socketChannel.getRemoteAddress()).getAddress());

                                response = Consts.RESPONSE_OK;
                                break;

                            case Consts.REQUEST_LOGOUT:
                                UserDB.instance.logoutUser(messageFragments[1]);
                                response = Consts.RESPONSE_OK;
                                break;

                            case Consts.REQUEST_ADD_FRIEND:
                                UserDB.instance.addFriendship(messageFragments[1], messageFragments[2]);
                                response = Consts.RESPONSE_OK;
//                                response = Consts.RESPONSE_USER_NOT_FOUND; //TODO: check if user1 or user2

                                break;

                            case Consts.REQUEST_FRIEND_LIST:
                                response = UserDB.instance.getFriends(messageFragments[1]);
                                break;

                            case Consts.REQUEST_CHALLENGE:
                                response = Consts.RESPONSE_ILLEGAL_REQUEST; //no challenge request allowed via TCP
                                break;

                            case Consts.REQUEST_SCORE:
                                response = String.valueOf(UserDB.instance.getScore(messageFragments[1]));
                                break;

                            case Consts.REQUEST_RANKINGS:
                                response = UserDB.instance.getRanking(messageFragments[1]);
                                break;

                            /*
                             * User already received first word so he sends the translation of the last word
                             * and wants the next word
                             */
                            case Consts.REQUEST_NEXT_WORD:
                                //Check correctness of translated word, then send new word
                                matchId = Integer.parseInt(messageFragments[1]);
                                String username = messageFragments[2];
                                String originalWord = messageFragments[3];
                                String translatedWord = messageFragments[4];
                                String wellTranslatedWord = ChallengeHandler.instance.checkTranslation(matchId, username, originalWord, translatedWord);
                                boolean outcome = wellTranslatedWord != null && wellTranslatedWord.toLowerCase().equals(translatedWord.toLowerCase());
                                response = Consts.getTranslationResponseServer(matchId, translatedWord, wellTranslatedWord, outcome);

                                response += "\n";

                            case Consts.REQUEST_READY_FOR_CHALLENGE:
                                //client is ready for a match
                                matchId = Integer.parseInt(messageFragments[1]);
                                String user = messageFragments[2];
                                String nextWord = ChallengeHandler.instance.getNextWord(matchId, user);
                                response += Consts.getNextWordResponse(matchId, nextWord);

                                break;

                            default:
                                System.out.println("TCP, unknown command: " + messageFragments[0]);
                                response = Consts.RESPONSE_UNKNOWN_REQUEST;
                                break;
                        }

                        //set error response in case of exception
                    } catch (UserDB.UserNotFoundException e) {
                        response = Consts.RESPONSE_USER_NOT_FOUND;
                    } catch (WQRegisterInterface.InvalidPasswordException e) {
                        response = Consts.RESPONSE_WRONG_PASSWORD;
                    } catch (UserDB.AlreadyLoggedException e) {
                        response = Consts.RESPONSE_ALREADY_LOGGED;
                    } catch (UserDB.AlreadyFriendsException e) {
                        response = Consts.RESPONSE_ALREADY_FRIENDS;
                    } catch (UserDB.NotLoggedException e) {
                        response = Consts.RESPONSE_NOT_LOGGED;
                    } catch (UserDB.SameUserException e) {
                        response = Consts.RESPONSE_SAME_USER;
                    } catch (ChallengeHandler.Challenge.GameTimeoutException e) {
                        response = Consts.RESPONSE_CHALLENGE_TIMEOUT + "\n";
                        response += ChallengeHandler.instance.getRecap(matchId);
                    } catch (ChallengeHandler.Challenge.EndOfMatchException e) {
                        response += ChallengeHandler.instance.getRecap(matchId);
                    } catch (ChallengeHandler.Challenge.UnknownUsernameException e) {
                        response = Consts.RESPONSE_UNKNOWN_USERNAME;
                    }

                    catch (IndexOutOfBoundsException e){
                        //client sent a message without proper format
                        response = Consts.RESPONSE_WRONG_FORMAT;
                    }

                    byteBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
                    System.out.println("Writing: " + response);

                    //TODO: should not assume complete write
                    //sending first the size of the buffer to be allocated
                    ByteBuffer intBuffer = ByteBuffer.allocate(Consts.INT_SIZE);
                    intBuffer.putInt(byteBuffer.remaining());
                    intBuffer.flip();
                    socketChannel.write(intBuffer);

                } else {
                    System.err.println("The attachment of the selection key in not a known instance, terminating task");
                    return;
                }


                //writing buffer
                socketChannel.write(byteBuffer);
                if (byteBuffer.hasRemaining()) {
                    selectionKey.interestOps(SelectionKey.OP_WRITE); //expecting a write from the server as the new operation
                    selectionKey.attach(byteBuffer);
                } else {
                    selectionKey.interestOps(SelectionKey.OP_READ); //expecting a write from the client as the new operation
                    selectionKey.attach(null); //nothing to be kept
                }
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        UserDB.instance.storeToFile();
    }



}
