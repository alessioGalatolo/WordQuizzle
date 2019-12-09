package Server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class WriteTask implements Runnable {

    private SelectionKey selectionKey;

    public WriteTask(SelectionKey currentSelectionKey) {
        selectionKey = currentSelectionKey;
    }

    @Override
    public void run() {
//        try {

            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

            String message;

            /*if (selectionKey.attachment() instanceof ByteBuffer) {
                ByteBuffer byteBuffer = (ByteBuffer) selectionKey.attachment();
                message = new String((byteBuffer).array(), 0, byteBuffer.limit(), StandardCharsets.UTF_8); //TODO: limit?
            } else*/if(selectionKey.attachment() instanceof byte[]) {
                message = new String((byte[]) selectionKey.attachment(), StandardCharsets.UTF_8);
            }else{
                System.err.println("The attachment of the selection key in not a know instance, terminating task");
                return;
            }



            //sending first the size of the buffer to be allocated
//            ByteBuffer intBuffer = ByteBuffer.allocate(Consts.INT_SIZE);
//            intBuffer.putInt(byteBuffer.remaining());
//            intBuffer.flip();
//            socketChannel.write(intBuffer);
//
//            //writing buffer
//            socketChannel.write(byteBuffer);
//            if (byteBuffer.hasRemaining()) {
//                selectionKey.interestOps(SelectionKey.OP_WRITE); //expecting a write from the server as the new operation
//                selectionKey.attach(byteBuffer);
//            } else {
//                selectionKey.interestOps(SelectionKey.OP_READ); //expecting a write from the client as the new operation
//                selectionKey.attach(null); //nothing to be kept
//            }
//        }catch (IOException e){
//            e.printStackTrace();
//        }
    }
}
