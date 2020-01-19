package server;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A simple task that waits the two users involved in a challenge to finish before writing to
 * a byte buffer a recap of the challenge
 */
class RecapTask implements Runnable {
    private final int matchId;
    private final ByteBuffer output;
    private final ByteBuffer preOutput;
    private final AtomicBoolean processed;
    private final ThreadPoolExecutor threadPool;

    RecapTask(int matchId, ByteBuffer output, ByteBuffer preOutput, AtomicBoolean processed, ThreadPoolExecutor threadPool) {
        this.matchId = matchId;
        this.output = output;
        this.preOutput = preOutput;
        this.processed = processed;
        this.threadPool = threadPool;
    }

    @Override
    public void run() {
        if(ChallengeHandler.instance.challengeIsFinished(matchId)){
            String recap = ChallengeHandler.instance.getRecap(matchId);
            output.put(recap.getBytes(StandardCharsets.UTF_8));
            output.flip();

            //sending first the size of the buffer to be allocated
            preOutput.putInt(output.remaining());
            preOutput.flip();
            processed.set(true);
        }else{
            threadPool.execute(this);
        }
    }
}
