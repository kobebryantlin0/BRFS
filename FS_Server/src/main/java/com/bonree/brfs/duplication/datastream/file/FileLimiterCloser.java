package com.bonree.brfs.duplication.datastream.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bonree.brfs.common.service.ServiceManager;
import com.bonree.brfs.disknode.client.DiskNodeClient;
import com.bonree.brfs.duplication.DuplicationEnvironment;
import com.bonree.brfs.duplication.coordinator.DuplicateNode;
import com.bonree.brfs.duplication.coordinator.FileCoordinator;
import com.bonree.brfs.duplication.coordinator.FileNode;
import com.bonree.brfs.duplication.coordinator.FilePathBuilder;
import com.bonree.brfs.duplication.datastream.connection.DiskNodeConnection;
import com.bonree.brfs.duplication.datastream.connection.DiskNodeConnectionPool;
import com.bonree.brfs.duplication.synchronize.FileSynchronizeCallback;
import com.bonree.brfs.duplication.synchronize.FileSynchronizer;
import com.bonree.brfs.server.identification.ServerIDManager;

public class FileLimiterCloser implements FileCloseListener {
	private static final Logger LOG = LoggerFactory.getLogger(FileLimiterCloser.class);
	
	private FileSynchronizer fileRecovery;
	private DiskNodeConnectionPool connectionPool;
	private FileCoordinator fileCoordinator;
	private ServerIDManager idManager;
	private ServiceManager serviceManager;
	
	public FileLimiterCloser(FileSynchronizer fileRecovery,
			DiskNodeConnectionPool connectionPool,
			FileCoordinator fileCoordinator,
			ServiceManager serviceManager,
			ServerIDManager idManager) {
		this.fileRecovery = fileRecovery;
		this.connectionPool = connectionPool;
		this.fileCoordinator = fileCoordinator;
		this.serviceManager = serviceManager;
		this.idManager = idManager;
	}
	
	@Override
	public void close(FileLimiter file) throws Exception {
		fileRecovery.synchronize(file.getFileNode(), new FileCloseConditionChecker(file));
	}
	
	public void closeFileNode(FileNode fileNode) {
		LOG.info("start to close file node[{}]", fileNode.getName());
		for(DuplicateNode node : fileNode.getDuplicateNodes()) {
			if(node.getGroup().equals(DuplicationEnvironment.VIRTUAL_SERVICE_GROUP)) {
				LOG.info("Ignore virtual duplicate node[{}]", node);
				continue;
			}
			
			DiskNodeConnection connection = connectionPool.getConnection(node);
			if(connection == null || connection.getClient() == null) {
				LOG.info("close error because node[{}] is disconnected!", node);
				continue;
			}
			
			DiskNodeClient client = connection.getClient();
			String serverId = idManager.getOtherSecondID(node.getId(), fileNode.getStorageId());
			String filePath = FilePathBuilder.buildFilePath(fileNode.getStorageName(), serverId, fileNode.getCreateTime(), fileNode.getName());
			
			try {
				LOG.info("closing file[{}]", filePath);
				boolean closed = client.closeFile(filePath);
				LOG.info("close file[{}] result->{}", filePath, closed);
				if(closed) {
					fileCoordinator.delete(fileNode);
				}
			} catch (Exception e) {
				LOG.error("close file[{}] error!", filePath);
			}
		}
	}
	
	private class FileCloseConditionChecker implements FileSynchronizeCallback {
		private FileLimiter file;
		
		public FileCloseConditionChecker(FileLimiter file) {
			this.file = file;
		}

		@Override
		public void complete(FileNode fileNode) {
			LOG.info("close file[{}] after sync", fileNode.getName());
			closeFileNode(fileNode);
		}

		@Override
		public void error(Throwable cause) {
			LOG.error("sync file to close error", cause);
			try {
				//对于没办法处理的文件，只能放弃了
				fileCoordinator.delete(file.getFileNode());
			} catch (Exception e) {
				LOG.error("delete file node error", e);
			}
		}
		
	}
}
