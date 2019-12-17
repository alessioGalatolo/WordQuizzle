package Server;

import Commons.WQRegisterInterface;

import java.io.IOException;
import java.net.InetSocketAddress;
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
            //socketChannel.getRemoteAddress();

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


                //TODO: move actions taken from write task to read task
                try {
                    switch (messageFragments[0].toLowerCase()) {
                        case Consts.REQUEST_LOGIN:
                            if(messageFragments.length > 3)
                                UserDB.logUser(messageFragments[1], messageFragments[2],
                                        ((InetSocketAddress) socketChannel.getRemoteAddress()).getAddress(),
                                        Integer.parseInt(messageFragments[3]));
                            else
                                UserDB.logUser(messageFragments[1], messageFragments[2], ((InetSocketAddress) socketChannel.getRemoteAddress()).getAddress());

                            response = Consts.RESPONSE_OK;
                            break;

                        case Consts.REQUEST_LOGOUT:
                            UserDB.logoutUser(messageFragments[1]);
                            response = Consts.RESPONSE_OK;
                            break;

                        case Consts.REQUEST_ADD_FRIEND:
                            UserDB.addFriendship(messageFragments[1], messageFragments[2]);
                            response = Consts.RESPONSE_OK;
//                                response = Consts.RESPONSE_USER_NOT_FOUND; //TODO: check if user1 or user2

                            break;

                        case Consts.REQUEST_FRIEND_LIST:
                            response = UserDB.getFriends(messageFragments[1]);
                            break;

                        case Consts.REQUEST_CHALLENGE:
                            response = Consts.RESPONSE_ILLEGAL_REQUEST;
                            break;

                        case Consts.REQUEST_SCORE:
                            response = String.valueOf(UserDB.getScore(messageFragments[1]));
                            break;

                        case Consts.REQUEST_RANKINGS:
                            response = UserDB.getRanking(messageFragments[1]);
                            break;

                        case Consts.REQUEST_READY_FOR_CHALLENGE:
                            //client is ready for a match
                            int matchId = Integer.parseInt(messageFragments[1]);


                            //TODO: check usefulness
                            response = Consts.RESPONSE_OK;
                            break;

                        default:
                            response = Consts.RESPONSE_UNKNOWN_REQUEST;
                            break;
                    }

                //set error response in case of exception
                }catch (UserDB.UserNotFoundException e){
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
