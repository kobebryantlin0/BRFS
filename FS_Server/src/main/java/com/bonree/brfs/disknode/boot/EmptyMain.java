package com.bonree.brfs.disknode.boot;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bonree.brfs.common.net.http.HttpConfig;
import com.bonree.brfs.common.net.http.netty.NettyHttpRequestHandler;
import com.bonree.brfs.common.net.http.netty.NettyHttpServer;
import com.bonree.brfs.common.service.Service;
import com.bonree.brfs.common.service.ServiceManager;
import com.bonree.brfs.common.service.ServiceStateListener;
import com.bonree.brfs.common.utils.LifeCycle;
import com.bonree.brfs.common.utils.PooledThreadFactory;
import com.bonree.brfs.configuration.Configs;
import com.bonree.brfs.configuration.units.DiskNodeConfigs;
import com.bonree.brfs.disknode.DiskContext;
import com.bonree.brfs.disknode.data.write.FileWriterManager;
import com.bonree.brfs.disknode.data.write.record.RecordCollectionManager;
import com.bonree.brfs.disknode.server.handler.CloseMessageHandler;
import com.bonree.brfs.disknode.server.handler.DeleteMessageHandler;
import com.bonree.brfs.disknode.server.handler.FileCopyMessageHandler;
import com.bonree.brfs.disknode.server.handler.FlushMessageHandler;
import com.bonree.brfs.disknode.server.handler.ListMessageHandler;
import com.bonree.brfs.disknode.server.handler.OpenMessageHandler;
import com.bonree.brfs.disknode.server.handler.PingPongRequestHandler;
import com.bonree.brfs.disknode.server.handler.ReadMessageHandler;
import com.bonree.brfs.disknode.server.handler.RecoveryMessageHandler;
import com.bonree.brfs.disknode.server.handler.SequenceNumberCache;
import com.bonree.brfs.disknode.server.handler.WriteMessageHandler;
import com.bonree.brfs.disknode.server.handler.WritingBytesMessageHandler;
import com.bonree.brfs.disknode.server.handler.WritingMetaDataMessageHandler;
import com.bonree.brfs.disknode.server.handler.WritingSequenceMessageHandler;

public class EmptyMain implements LifeCycle {
	private static final Logger LOG = LoggerFactory.getLogger(EmptyMain.class);
	
	private NettyHttpServer server;
	private HttpConfig httpConfig;
	
	private DiskContext diskContext;
	
	private FileWriterManager writerManager;
	private ServiceManager serviceManager;
	
	private ExecutorService requestHandlerExecutor;
	
	public EmptyMain(ServiceManager serviceManager) {
		this.diskContext = new DiskContext(Configs.getConfiguration().GetConfig(DiskNodeConfigs.CONFIG_DATA_ROOT));
		this.serviceManager = serviceManager;
	}

	@Override
	public void start() throws Exception {
		httpConfig = new HttpConfig(Configs.getConfiguration().GetConfig(DiskNodeConfigs.CONFIG_HOST),
				Configs.getConfiguration().GetConfig(DiskNodeConfigs.CONFIG_PORT));
		
		LOG.info("Empty Main--port[{}]", httpConfig.getPort());
		
		checkDiskContextPath();
		
		RecordCollectionManager recorderManager = new RecordCollectionManager();
		writerManager = new FileWriterManager(recorderManager);
		writerManager.start();
		
		writerManager.rebuildFileWriterbyDir(diskContext.getRootDir());
		
		serviceManager.addServiceStateListener(Configs.getConfiguration().GetConfig(DiskNodeConfigs.CONFIG_SERVICE_GROUP_NAME), new ServiceStateListener() {
			
			@Override
			public void serviceRemoved(Service service) {
				LOG.info("service[{}] removed, time to flush all files", service);
				writerManager.flushAll();
			}
			
			@Override
			public void serviceAdded(Service service) {
			}
		});
		
		httpConfig.setBacklog(1024);
		server = new NettyHttpServer(httpConfig);
		
		requestHandlerExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new PooledThreadFactory("request_common"));
		
		NettyHttpRequestHandler requestHandler = new NettyHttpRequestHandler();
		requestHandler.addMessageHandler("PUT", new OpenMessageHandler(diskContext, writerManager, requestHandlerExecutor));
		requestHandler.addMessageHandler("POST", new WriteMessageHandler(diskContext, writerManager));
		requestHandler.addMessageHandler("GET", new ReadMessageHandler(diskContext, requestHandlerExecutor));
		requestHandler.addMessageHandler("CLOSE", new CloseMessageHandler(diskContext, writerManager, requestHandlerExecutor));
		requestHandler.addMessageHandler("DELETE", new DeleteMessageHandler(diskContext, writerManager, requestHandlerExecutor));
		server.addContextHandler(DiskContext.URI_DISK_NODE_ROOT, requestHandler);
		
		NettyHttpRequestHandler flushRequestHandler = new NettyHttpRequestHandler();
		flushRequestHandler.addMessageHandler("POST", new FlushMessageHandler(diskContext, writerManager, requestHandlerExecutor));
		server.addContextHandler(DiskContext.URI_FLUSH_NODE_ROOT, flushRequestHandler);
		
		SequenceNumberCache cache = new SequenceNumberCache(writerManager);
		
		NettyHttpRequestHandler sequenceRequestHandler = new NettyHttpRequestHandler();
		sequenceRequestHandler.addMessageHandler("GET", new WritingSequenceMessageHandler(diskContext, cache));
		server.addContextHandler(DiskContext.URI_SEQUENCE_NODE_ROOT, sequenceRequestHandler);
		
		NettyHttpRequestHandler bytesRequestHandler = new NettyHttpRequestHandler();
		bytesRequestHandler.addMessageHandler("GET", new WritingBytesMessageHandler(diskContext, cache));
		server.addContextHandler(DiskContext.URI_SEQ_BYTE_NODE_ROOT, bytesRequestHandler);
		
		NettyHttpRequestHandler metaRequestHandler = new NettyHttpRequestHandler();
		metaRequestHandler.addMessageHandler("GET", new WritingMetaDataMessageHandler(diskContext, writerManager, requestHandlerExecutor));
		server.addContextHandler(DiskContext.URI_META_NODE_ROOT, metaRequestHandler);
		
		NettyHttpRequestHandler cpRequestHandler = new NettyHttpRequestHandler();
		cpRequestHandler.addMessageHandler("POST", new FileCopyMessageHandler(diskContext));
		server.addContextHandler(DiskContext.URI_COPY_NODE_ROOT, cpRequestHandler);
		
		NettyHttpRequestHandler listRequestHandler = new NettyHttpRequestHandler();
		listRequestHandler.addMessageHandler("GET", new ListMessageHandler(diskContext, requestHandlerExecutor));
		server.addContextHandler(DiskContext.URI_LIST_NODE_ROOT, listRequestHandler);
		
		NettyHttpRequestHandler recoverRequestHandler = new NettyHttpRequestHandler();
		recoverRequestHandler.addMessageHandler("POST", new RecoveryMessageHandler(diskContext, serviceManager, writerManager, recorderManager, requestHandlerExecutor));
		server.addContextHandler(DiskContext.URI_RECOVER_NODE_ROOT, recoverRequestHandler);
		
		NettyHttpRequestHandler pingRequestHandler = new NettyHttpRequestHandler();
		pingRequestHandler.addMessageHandler("GET", new PingPongRequestHandler());
		server.addContextHandler(DiskContext.URI_PING_PONG_ROOT, pingRequestHandler);
		
		server.start();
	}
	
	private void checkDiskContextPath() {
		if(!new File(diskContext.getRootDir()).exists()) {
			throw new IllegalArgumentException("Disk context path[" + diskContext.getRootDir() + "] is not existed!");
		}
	}

	@Override
	public void stop() throws Exception {
		server.stop();
		writerManager.stop();
		
		if(requestHandlerExecutor != null) {
			requestHandlerExecutor.shutdown();
		}
	}
}
