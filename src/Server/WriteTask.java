package Server;

import Commons.WQRegisterInterface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Vector;

public class WriteTask implements Runnable {

    private SelectionKey selectionKey;

    public WriteTask(SelectionKey currentSelectionKey) {
        selectionKey = currentSelectionKey;
    }

    @Override
    public void run() {
        //client is ready to receive data
        //executing requested command then sending response

        try {

            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

            //will contain the response to be written to the client at the end of the request
            ByteBuffer byteBuffer;

            if (selectionKey.attachment() instanceof ByteBuffer) {
                //previous write was incomplete
                byteBuffer = (ByteBuffer) selectionKey.attachment();
//                message = new String((byteBuffer).array(), 0, byteBuffer.limit(), StandardCharsets.UTF_8); //TODO: limit?


            } else if (selectionKey.attachment() instanceof byte[]) {
                //previous operation was a read, serving request then sending response
                String message = new String((byte[]) selectionKey.attachment(), StandardCharsets.UTF_8);
                String[] messageFragments = message.split(" ");

                String response;

                switch (messageFragments[0].toLowerCase()) {
                    case Consts.REQUEST_LOGIN:
                        try {
                            UserDB.logUser(messageFragments[1], messageFragments[2]);
                            response = Consts.RESPONSE_OK;
                        } catch (UserDB.UserNotFoundException e) {
                            response = Consts.RESPONSE_USER_NOT_FOUND;
                        } catch (WQRegisterInterface.InvalidPasswordException e) {
                            response = Consts.RESPONSE_WRONG_PASSWORD;
                        } catch (UserDB.AlreadyLoggedException e) {
                            response = Consts.RESPONSE_ALREADY_LOGGED;
                        }
                        break;

                    case Consts.REQUEST_LOGOUT:
                        try {
                            UserDB.logoutUser(messageFragments[1]);
                            response = Consts.RESPONSE_OK;
                        } catch (UserDB.UserNotFoundException e) {
                            response = Consts.RESPONSE_USER_NOT_FOUND;
                        } catch (UserDB.NotLoggedException e) {
                            response = Consts.RESPONSE_NOT_LOGGED;
                        }
                        break;

                    case Consts.REQUEST_ADD_FRIEND:
                        try {
                            UserDB.addFriendship(messageFragments[1], messageFragments[2]);
                            response = Consts.RESPONSE_OK;
                        } catch (UserDB.UserNotFoundException e) {
                            response = Consts.RESPONSE_USER_NOT_FOUND; //TODO: check if user1 or user2
                        } catch (UserDB.AlreadyFriendsException e) {
                            response = Consts.RESPONSE_ALREADY_FRIENDS;
                        }

                        break;

                    case Consts.REQUEST_FRIEND_LIST:
                        try {
                            response = UserDB.getFriends(messageFragments[1]);
                        } catch (UserDB.UserNotFoundException e) {
                            response = Consts.RESPONSE_USER_NOT_FOUND;
                        }

                        break;

                    case Consts.REQUEST_CHALLENGE:

                        try {
                            UserDB.challengeFriend(messageFragments[1], messageFragments[2]);
                            response = Consts.RESPONSE_OK;
                        } catch (UserDB.UserNotFoundException e) {
                            response = Consts.RESPONSE_USER_NOT_FOUND;
                        } catch (UserDB.NotFriendsException e) {
                            response = Consts.RESPONSE_NOT_FRIENDS;
                        }

                        break;

                    case Consts.REQUEST_SCORE:
                        response = String.valueOf(UserDB.getScore(messageFragments[1]));
                        break;

                    case Consts.REQUEST_RANKINGS:
                        response = UserDB.getRanking(messageFragments[1]);
                        break;

                    default:
                        response = Consts.RESPONSE_UNKNOWN_REQUEST;
                        break;
                }

                byteBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));


                //TODO: should not assume complete write
                //sending first the size of the buffer to be allocated
                ByteBuffer intBuffer = ByteBuffer.allocate(Consts.INT_SIZE);
                intBuffer.putInt(byteBuffer.remaining());
                intBuffer.flip();
                socketChannel.write(intBuffer);

            } else {
                System.err.println("The attachment of the selection key in not a know instance, terminating task");
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
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
