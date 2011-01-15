package lsr.paxos.test;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

import lsr.common.Config;
import lsr.common.Configuration;
import lsr.paxos.replica.Replica;
import lsr.service.AbstractService;

public class DigestService extends AbstractService {

    /** Written to 'out' stream as the replica becomes up */
    public static final String RECOVERY_FINISHED = "rec_finished";

    protected MessageDigest sha512;
    protected int lastExecuteSeqNo;
    protected Random random = new Random();

    protected int snapshotSeqNo;
    protected byte[] snapshot;

    protected final DataOutputStream decisionsFile;

    protected final int localId;

    public DigestService(int localId, String logPath) throws Exception {
        this.localId = localId;

        sha512 = MessageDigest.getInstance("SHA-512");

        File logDirectory = new File(logPath, Integer.toString(localId));
        logDirectory.mkdirs();

        File logFile;
        int i = 0;
        do
            logFile = new File(logDirectory.getAbsolutePath(), "decisions.log." + i++);
        while (logFile.exists());

        decisionsFile = new DataOutputStream(new FileOutputStream(logFile));
    }

    public synchronized byte[] execute(byte[] value, int executeSeqNo) {
        lastExecuteSeqNo = executeSeqNo;
        byte[] digest = sha512.digest(value);

        StringBuffer sb = new StringBuffer();
        sb.append(executeSeqNo);
        sb.append(' ');
        sb.append(localId);
        sb.append(' ');
        sb.append(Arrays.toString(value).hashCode());
        sb.append(' ');
        sb.append(Arrays.toString(digest).hashCode());
        sb.append('\n');

        try {
            decisionsFile.writeBytes(sb.toString());
            decisionsFile.flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (random.nextInt(100) < 10) { // 10% chance
            snapshotSeqNo = executeSeqNo;
            snapshot = digest;

            if (random.nextInt(100) < 10) { // 1% chance
                fireSnapshotMade(snapshotSeqNo + 1, snapshot, digest);
            }
        }

        return digest;
    }

    public void askForSnapshot(int lastSnapshotNextRequestSeqNo) {
        if (random.nextInt(100) < 80) { // 80% chance
            ensureSnapshot();
            fireSnapshotMade(snapshotSeqNo + 1, snapshot, null);
        }
    }

    public void forceSnapshot(int lastSnapshotNextRequestSeqNo) {
        ensureSnapshot();
        fireSnapshotMade(snapshotSeqNo + 1, snapshot, null);
    }

    protected void ensureSnapshot() {
        if (snapshot == null) {
            snapshot = sha512.digest();
            snapshotSeqNo = lastExecuteSeqNo;
        }
    }

    public void updateToSnapshot(int requestSeqNo, byte[] snapshot) {
        sha512.reset();
        sha512.update(snapshot);
    }

    public void recoveryFinished() {
        System.out.println(RECOVERY_FINISHED);
    }

    public static void main(String[] args) throws Exception {
        int localId = Integer.parseInt(args[0]);
        Configuration config = new Configuration();
        String logPath = config.getProperty(Config.LOG_PATH, Config.DEFAULT_LOG_PATH);
        DigestService service = new DigestService(localId, logPath);
        Replica replica = new Replica(config, localId, service);
        replica.start();
        System.in.read();
        System.exit(-1);
    }
}
