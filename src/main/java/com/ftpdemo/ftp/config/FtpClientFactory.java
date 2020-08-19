package com.ftpdemo.ftp.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.pool.PoolableObjectFactory;

import java.io.IOException;

@Slf4j
public class FtpClientFactory implements PoolableObjectFactory<FTPClient> {

    private FtpProperties ftpProperties;

    public FtpClientFactory(FtpProperties ftpProperties) {
        this.ftpProperties = ftpProperties;
    }

    @Override
    public FTPClient makeObject() throws Exception {
        FTPClient ftpClient = new FTPClient();
        ftpClient.setControlEncoding(ftpProperties.getEncoding());
        ftpClient.setConnectTimeout(ftpProperties.getClientTimeout());

        try {
            ftpClient.connect(ftpProperties.getHost(), ftpProperties.getPort());
            int reply = ftpClient.getReplyCode();
            if(!FTPReply.isPositiveCompletion(reply)){
                ftpClient.disconnect();
                log.warn("FTPServer refused connection");
            }
            boolean result = ftpClient.login(ftpProperties.getUsername(),ftpProperties.getPassword());
            ftpClient.setFileType(ftpProperties.getTransferFileType());
            if(!result){
                log.warn("ftpClient login failed... username is {}",ftpProperties.getUsername());
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.error("create ftp connection failed...{}",e.getMessage());
            throw e;
        }
        return ftpClient;
    }

    @Override
    public void destroyObject(FTPClient ftpClient) throws Exception {
        try {
            ftpClient.logout();
        } catch (IOException e) {
            e.printStackTrace();
            log.error("ftp client logout failed...{}",e.getMessage());
            throw e;
        }finally {
            if(null != ftpClient){
                ftpClient.disconnect();
            }
        }
    }

    @Override
    public boolean validateObject(FTPClient ftpClient) {
        try {
            return ftpClient.sendNoOp();
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Failed to validate client: {}",e.getMessage());
            return false;
        }
    }

    @Override
    public void activateObject(FTPClient ftpClient) throws Exception {

    }

    @Override
    public void passivateObject(FTPClient ftpClient) throws Exception {

    }
}
