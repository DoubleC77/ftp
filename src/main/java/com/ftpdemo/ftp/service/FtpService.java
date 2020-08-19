package com.ftpdemo.ftp.service;

import com.ftpdemo.ftp.config.FtpClientFactory;
import com.ftpdemo.ftp.config.FtpClientPool;
import com.ftpdemo.ftp.config.FtpProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.lang.reflect.Field;
import java.util.StringTokenizer;

@Slf4j
@Component
public class FtpService {


    /**
     * ftp连接池
     */
    public static FtpClientPool ftpClientPool;

    public static FTPClient ftpClient;

    private static FtpService ftpService;

    @Autowired
    private FtpProperties ftpProperties;

    /**
     * 根据 factory 初始化连接池
     * @return
     */
    @PostConstruct
    public boolean init(){
        FtpClientFactory factory = new FtpClientFactory(ftpProperties);
        ftpService = this;
        try {
            ftpClientPool = new FtpClientPool(factory);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 获取一个链接对象
     * @return
     * @throws Exception
     */
    public static FTPClient getFTPClient() throws Exception {
        if(null == ftpClient){
            synchronized (ftpClientPool){
                ftpClient = ftpClientPool.borrowObject();
                //todo 拿出来了一个链接,然后当前业务完成的时候补充回去一个链接,保持连接池有可用的链接,不然当队列长度为0时take()方法会一直阻塞
                ftpClientPool.returnObject(ftpClient);
            }
        }
        return ftpClient;
    }

    /**
     * 当前命令执行完成
     * @throws IOException
     */
    public void compelete() throws IOException {
        ftpClient.completePendingCommand();
    }

    /**
     * 当前线程任务处理完成后,重新加入到队列中
     * @throws Exception
     */
    public void disconnect() throws Exception {
        ftpClientPool.addObject(ftpClient);
    }

    /**
     * 用输入流上传文件到ftp目录
     * @param remoteFile
     * @param inputStream
     * @return
     */
    public static boolean uploadFile(String remoteFile, InputStream inputStream){
        boolean result = false;
        try {
            //获取一个ftpClient链接,这个链接在方法中指向了当前局部变量 ftpClient,同时也返回出来了,可以接收,也可以直接操作,局部变量.在获取方法内部用 ftpClientPool对象加了ftpClientPool锁
            getFTPClient();
            //使用 被动模式,让服务端开一个端口,然后我们客户端去连接服务端
            ftpClient.enterLocalPassiveMode();
            result = ftpClient.storeFile(remoteFile,inputStream);
            inputStream.close();
            ftpClient.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
            return result;
        }
        return result;
    }


    /**
     * 给出本地文件路径上传到远端的ftp目录
     * @param remoteFile
     * @param localFile
     * @return
     */
    public static boolean uploadFile(String remoteFile, String localFile){
        FileInputStream in = null;
        try {
            in = new FileInputStream(new File(localFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            log.error("文件上传失败:{}",e.getMessage());
        }
        return uploadFile(remoteFile,in);
    }


    /**
     * 获取远端ftp目录中的文件的输入流
     * completePendingCommand()会一直在等FTP Server返回226 Transfer complete，但是FTP Server只有在接受到InputStream 执行close方法时，才会返回
     * 所以后面要注意关闭这个输入流
     * @param fileName
     * @return
     */
    public static InputStream getRemoteFileInputStream(String fileName) throws Exception {
        ByteArrayOutputStream fos = new ByteArrayOutputStream();
        getFTPClient();
        ftpClient.retrieveFile(fileName,fos);
        ByteArrayInputStream in = new ByteArrayInputStream(fos.toByteArray());
        fos.close();
        return in;
    }

    /**
     * 将ftp目录中的文件下载本地
     * @param remoteFile
     * @param localFile
     * @return
     */
    public static boolean downFile(String remoteFile, String localFile){
        boolean result = false;
        try {
            getFTPClient();
            OutputStream os = new FileOutputStream(localFile);
            ftpClient.retrieveFile(remoteFile,os);
            os.flush();
            os.close();

            //todo 删除ftp上的原文件
            //ftpClient.deleteFile(remoteFile);
            ftpClient.logout();
            ftpClient.disconnect();
            result = true;
        } catch (Exception e) {
            e.printStackTrace();
            log.error("ftp download file failed {}",e.getMessage());
        }
        return result;
    }

    /**
     * ftp目录文件重命名
     * @param fromFile
     * @param toFile
     * @return
     * @throws Exception
     */
    public static boolean rename(String fromFile, String toFile) throws Exception {
        getFTPClient();
        return ftpClient.rename(fromFile,toFile);
    }


    /**
     * 获取ftp目录下的文件列表
     * @param dir
     * @return
     * @throws Exception
     */
    public static FTPFile [] getFiles(String dir) throws Exception{
        getFTPClient();
        FTPFile[] files = null;
        try {
            files = ftpClient.listFiles();
        } catch (IOException e) {
            e.printStackTrace();
            log.error("获取ftp目录下文件列表失败:{}",e.getMessage());
        }
        return files;
    }

    /**
     * 在ftp上创建文件夹,文件夹已存在的时候返回false
     * @param remoteDir
     * @return
     * @throws Exception
     */
    public boolean mkdir(String remoteDir) throws Exception{
        getFTPClient();
        boolean result = false;
        try {
            result = ftpClient.makeDirectory(remoteDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 在ftp上创建多个目录
     * @param dir
     * @return
     * @throws Exception
     */
    public boolean mkdirs(String dir) throws Exception{
        boolean result = false;
        if(StringUtils.isEmpty(dir)){
            return result;
        }
        getFTPClient();
        //切换到ftp的根目录,不是ftp服务器的根目录,具体目录在ftp的配置文件中配置
        ftpClient.changeWorkingDirectory("/");

        StringTokenizer dirs = new StringTokenizer(dir,"/");
        String temp = null;
        while (dirs.hasMoreElements()){
            //获取要创建的目录
            temp = dirs.nextElement().toString();
            //创建目录
            ftpClient.makeDirectory(temp);
            //进入目录
            ftpClient.changeWorkingDirectory(temp);
            result = true;
        }
        ftpClient.changeWorkingDirectory("/");
        return result;
    }



}
