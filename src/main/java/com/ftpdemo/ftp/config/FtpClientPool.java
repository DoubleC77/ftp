package com.ftpdemo.ftp.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.PoolableObjectFactory;

import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FtpClientPool implements ObjectPool<FTPClient> {

    private static final int DEFAULT_POOL_SIZE = 10;

    private static final int DEFAULT_QUEUE_TIMEOUT = 2;

    public BlockingQueue<FTPClient> blockingQueue;

    private FtpClientFactory factory;

    public FtpClientPool(FtpClientFactory factory) throws Exception {
        this(DEFAULT_POOL_SIZE,factory);
    }


    public FtpClientPool(int poolSize, FtpClientFactory factory) throws Exception {
        this.factory = factory;
        this.blockingQueue = new ArrayBlockingQueue<FTPClient>(poolSize);
        initPool(poolSize);
    }

    /**
     * 初始化连接池
     * @param maxPoolSize
     * @throws Exception
     */
    private void initPool(int maxPoolSize) throws Exception {
        int count = 0;
        while (count < maxPoolSize){
            this.addObject();
            count ++;
        }

    }

    /**
     * 从连接池中获取对象
     * @return
     * @throws Exception
     * @throws NoSuchElementException
     * @throws IllegalStateException
     */
    @Override
    public FTPClient borrowObject() throws Exception, NoSuchElementException, IllegalStateException {
        FTPClient client = blockingQueue.take();
        if(null == client){
            //创建一个新连接,这个链接还是从队列中拿出来的,所以这里不用重新添加,只需要给指针重新分配一块内存空间(实例化一个对象)即可
            client = factory.makeObject();
        }else if(!factory.validateObject(client)){
            //把这个已经断开的连接remove掉
            this.invalidateObject(client);
            //然后创建一个新的连接
            client = factory.makeObject();
            //todo 这个时候的队列其实比初始大小的10个要少一个了
        }
        return client;
    }


    /**
     * 归还一个链接,超时后失效
     * @param ftpClient
     * @throws Exception
     */
    @Override
    public void returnObject(FTPClient ftpClient) throws Exception {
        if(null != ftpClient && !blockingQueue.offer(ftpClient,DEFAULT_QUEUE_TIMEOUT, TimeUnit.MINUTES)){
            try {
                factory.destroyObject(ftpClient);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }
    }


    /**
     * 移除无效的对象(FTP客户端)
     * @param ftpClient
     * @throws Exception
     */
    @Override
    public void invalidateObject(FTPClient ftpClient) throws Exception {
        blockingQueue.remove(ftpClient);
    }


    /**
     * 增加一个链接到连接池,如果超过这个时间队列还是满的,那就加不进去
     * @throws Exception
     * @throws IllegalStateException
     * @throws UnsupportedOperationException
     */
    @Override
    public void addObject() throws Exception, IllegalStateException, UnsupportedOperationException {
        blockingQueue.offer(factory.makeObject(), DEFAULT_QUEUE_TIMEOUT, TimeUnit.MINUTES);
    }


    public void addObject(FTPClient ftpClient) throws Exception {
        blockingQueue.offer(ftpClient, DEFAULT_QUEUE_TIMEOUT, TimeUnit.MINUTES);
    }

    /**
     * 重新连接
     * @return
     * @throws Exception
     */
    public FTPClient reconnect() throws Exception{
        return factory.makeObject();
    }


    /**
     * 获取空闲的连接数
     * @return
     * @throws UnsupportedOperationException
     */
    @Override
    public int getNumIdle() throws UnsupportedOperationException {
        return blockingQueue.size();
    }

    /**
     * 获取活跃的链接数
     * @return
     * @throws UnsupportedOperationException
     */
    @Override
    public int getNumActive() throws UnsupportedOperationException {
        return DEFAULT_POOL_SIZE - getNumIdle();
    }

    @Override
    public void clear() throws Exception, UnsupportedOperationException {
        this.close();
    }

    /**
     * 关闭连接池
     * @throws Exception
     */
    @Override
    public void close() throws Exception {
        try {
            while (blockingQueue.iterator().hasNext()){
                FTPClient ftpClient = blockingQueue.take();
                factory.destroyObject(ftpClient);
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("close ftp client pool failed...{}",e.getMessage());
        }
    }

    @Override
    public void setFactory(PoolableObjectFactory<FTPClient> poolableObjectFactory) throws IllegalStateException, UnsupportedOperationException {

    }
}
