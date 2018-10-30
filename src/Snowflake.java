import java.net.InetAddress;
import java.net.UnknownHostException;

public class Snowflake {
	// start time
	private static long twepoch = 1540882800000L;
	// 机器占位
	private static long workerIdBits = 5L;
	// 数据中心占位
	private static long datacenterIdBits = 5L;
	// 序列占位
    private static long sequenceBits = 12L;
    // time
    private static long timeBits = 41L;
    // 设置偏移量
    private static long workerIdShift = 5L;
    private static long datacenterIdShift = sequenceBits + workerIdBits;
    private static long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
	
    
	private long workId;
    private long datacenterId;
    
    private long sequence = 0;
    private long lastTimestamp = -1L;
    
    private long sequenceMask = ~(-1L << sequenceBits);
    private long max_timestamp = twepoch + ~(-1L << timeBits);	//最大时间

    public Snowflake(long workId, long datacenterId) {
        this.workId = workId;
        this.datacenterId = datacenterId;
    }

    public synchronized long nextId() {
        long timestamp = this.timeGen();
        if(timestamp < lastTimestamp) {
//            if (lastTimestamp - timestamp <= 5) {
//                // wait 5ms
//                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
//                timestamp = timeGen();
//                
//            }
            System.err.printf("clock is moving backwards.  Rejecting requests until %d.", lastTimestamp);
            throw new RuntimeException(String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
        }
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            // 当前ms占满
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = timestamp;
        return this.generateId(lastTimestamp);
    }
    /**
     * 产生id
     * @param timestamp
     * @return
     */
    private long generateId(long timestamp) {
    		if (timestamp > max_timestamp) {
    			throw new RuntimeException("Timestamp reaches upper limit");
    		}
    		return ((timestamp - twepoch) << timestampLeftShift) |
				(datacenterId << datacenterIdShift) |
				(workId << workerIdShift) |
				sequence;
    }

    /**
     * 当前ms占满
     * @param lastTimestamp
     * @return
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while(timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }
    
    /**
     * 获取系统时间戳
     * @return
     */
    private long timeGen() {
        return System.currentTimeMillis();
    }
    
    /**
     * 根据ip产生workId的一种策略
     * @return
     */
    public static long getWorkIdByIP() {
    		try {
    			InetAddress address = InetAddress.getLocalHost();
    			byte[] ipv4ByteArray = address.getAddress();
    			long workId = 0L;
    			if (ipv4ByteArray.length == 4) {
               // 255.255.255.255
               for (byte b : ipv4ByteArray) {
                   workId += b & 0xff;
               }
    			} else if (ipv4ByteArray.length == 16) {
               // ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff
               for (byte b: ipv4ByteArray) {
                   workId += b & 0b1111111;
               }
    			}
    			return workId;
    		} catch (UnknownHostException e) {
            throw new IllegalStateException("Bad LocalHost InetAddress, please check your network!");
        }
    }
    
    public static void main(String ...args) {
        String str = Long.toBinaryString(~(-1L << 12L));
        System.out.println(str);
        Snowflake idWorker = new Snowflake(Snowflake.getWorkIdByIP(), 0);
        for (int i = 0; i < 1000; i++) {
            long id = idWorker.nextId();
            System.out.println(id + " -- " + Long.toBinaryString(id));
        }
    }
}
