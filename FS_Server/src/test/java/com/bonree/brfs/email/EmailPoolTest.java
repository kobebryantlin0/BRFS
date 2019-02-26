package com.bonree.brfs.email;

import com.bonree.mail.worker.MailWorker;
import com.bonree.mail.worker.ProgramInfo;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailPoolTest{
    private static final Logger LOG = LoggerFactory.getLogger(EmailPoolTest.class);
    /**
     * 初始化配置文件信息
     */
    @Before
    public void initConfig(){
        String resourcePath = Class.class.getResource("/").getPath()+"/server.properties";
        System.setProperty("configuration.file",resourcePath);
    }
    @Test
    @SuppressWarnings("all")
    public void initPool(){
        EmailPool.getInstance();
    }
    @Test
    @SuppressWarnings("all")
    public void sendmail(){
        MailWorker.Builder builder = MailWorker.newBuilder(ProgramInfo.getInstance()).setException(new NullPointerException("none"));
        EmailPool.getInstance().sendEmail(builder,false);
        LOG.info("----------------------------------------------------");
        try{
            Thread.sleep(1000);
        } catch(InterruptedException e){
            e.printStackTrace();
        }
    }
    @Test
    @SuppressWarnings("all")
    public void sendmailWaitResult(){
        MailWorker.Builder builder = MailWorker.newBuilder(ProgramInfo.getInstance()).setException(new NullPointerException("none"));
        boolean status = EmailPool.getInstance().sendEmail(builder,true);
        LOG.info("send status :{}",status);
    }
}
