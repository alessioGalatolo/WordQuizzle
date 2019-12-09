package Server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

public class ReadTask implements Runnable {
    private SelectionKey selectionKey;

    public ReadTask(SelectionKey currentSelectionKey) {
        selectionKey = currentSelectionKey;
    }

    @Override
    public void run() {
        try {

            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

            ByteBuffer byteBuffer = ByteBuffer.allocate(Consts.ARRAY_INIT_SIZE);

            if (socketChannel.read(byteBuffer) == -1) {
                //end of stream
                selectionKey.cancel();
            } else {
                //something has been read previously

                byteBuffer.flip();

                byte[] lastBytes = (byte[]) selectionKey.attachment(); //getting previous pieces of message (if existent)
                if (lastBytes != null) {
                    //an old attachment has been found

                    byte[] largerByteArray = Arrays.copyOf(lastBytes, lastBytes.length + byteBuffer.limit()); //returns bigger array
                    byte[] lastByteRead = Arrays.copyOfRange(byteBuffer.array(), 0, byteBuffer.limit());
                    System.arraycopy(lastByteRead, 0, largerByteArray, lastBytes.length, lastByteRead.length); //joins arrays
                    selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE); //updates key with write op
                    selectionKey.attach(largerByteArray); //attach buffer
                } else {
                    //no old attachment
                    selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    selectionKey.attach(Arrays.copyOfRange(byteBuffer.array(), 0, byteBuffer.limit())); //attach buffer
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
