package com.bonree.brfs.common.http.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.message.BasicHeader;
import org.apache.http.nio.client.util.HttpAsyncClientUtils;
import org.apache.http.protocol.HttpContext;

public class HttpClient implements Closeable {
	
	private CloseableHttpAsyncClient client;
	
	public HttpClient() {
		this(ClientConfig.DEFAULT);
	}
	
	public HttpClient(ClientConfig clientConfig) {
		ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setBufferSize(clientConfig.getBufferSize())
                .setCharset(Consts.UTF_8)
                .build();
		
		IOReactorConfig ioConfig = IOReactorConfig.custom()
				.setSoKeepAlive(true)
				.setConnectTimeout(clientConfig.getConnectTimeout())
				.setSndBufSize(clientConfig.getSocketSendBufferSize())
				.setRcvBufSize(clientConfig.getSocketRecvBufferSize())
				.setIoThreadCount(clientConfig.getIOThreadNum())
				.setTcpNoDelay(false)
				.build();
		
		List<Header> defaultHeaders = new ArrayList<Header>();
		defaultHeaders.add(new BasicHeader("Connection", "keep-alive"));
		
		client = HttpAsyncClientBuilder.create()
		           .setMaxConnPerRoute(clientConfig.getMaxConnection())
		           .setMaxConnTotal(clientConfig.getMaxConnection())
		           .setDefaultConnectionConfig(connectionConfig)
		           .setDefaultIOReactorConfig(ioConfig)
		           .setConnectionReuseStrategy(new ConnectionReuseStrategy() {

					@Override
					public boolean keepAlive(org.apache.http.HttpResponse response, HttpContext context) {
						return true;
					}
				})
				.setKeepAliveStrategy(new ConnectionKeepAliveStrategy() {
					
					@Override
					public long getKeepAliveDuration(org.apache.http.HttpResponse response, HttpContext context) {
						return clientConfig.getIdleTimeout();
					}
				})
				.setDefaultHeaders(defaultHeaders)
				.build();
		
		client.start();
	}
	
	public void executeGet(URI uri, ResponseHandler handler) {
		executeInner(new HttpGet(uri), handler);
	}
	
	public HttpResponse executeGet(URI uri) throws Exception {
		return executeInner(new HttpGet(uri));
	}
	
	public HttpResponse executePut(URI uri) throws Exception {
		return executeInner(new HttpPut(uri));
	}
	
	public void executePut(URI uri, ResponseHandler handler) {
		executeInner(new HttpPut(uri), handler);
	}
	
	public HttpResponse executePut(URI uri, byte[] bytes) throws Exception {
		HttpPut put = new HttpPut(uri);
		put.setEntity(new ByteArrayEntity(bytes));
		
		return executeInner(put);
	}
	
	public void executePut(URI uri, byte[] bytes, ResponseHandler handler) {
		HttpPut put = new HttpPut(uri);
		put.setEntity(new ByteArrayEntity(bytes));
		
		executeInner(put, handler);
	}
	
	public HttpResponse executePost(URI uri) throws Exception {
		return executeInner(new HttpPost(uri));
	}
	
	public void executePost(URI uri, ResponseHandler handler) {
		executeInner(new HttpPost(uri), handler);
	}
	
	public HttpResponse executePost(URI uri, byte[] bytes) throws Exception {
		HttpPost post = new HttpPost(uri);
		post.setEntity(new ByteArrayEntity(bytes));
		
		return executeInner(post);
	}
	
	public void executePost(URI uri, byte[] bytes, ResponseHandler handler) {
		HttpPost post = new HttpPost(uri);
		post.setEntity(new ByteArrayEntity(bytes));
		
		executeInner(post, handler);
	}
	
	public HttpResponse executeClose(URI uri) throws Exception {
		return executeInner(new HttpClose(uri));
	}
	
	public void executeClose(URI uri, ResponseHandler handler) {
		executeInner(new HttpClose(uri), handler);
	}
	
	public HttpResponse executeClose(URI uri, byte[] bytes) throws Exception {
		HttpClose close = new HttpClose(uri);
		close.setEntity(new ByteArrayEntity(bytes));
		
		return executeInner(close);
	}
	
	public void executeClose(URI uri, byte[] bytes, ResponseHandler handler) {
		HttpClose close = new HttpClose(uri);
		close.setEntity(new ByteArrayEntity(bytes));
		
		executeInner(close, handler);
	}
	
	public HttpResponse executeDelete(URI uri) throws Exception {
		return executeInner(new HttpDelete(uri));
	}
	
	public void executeDelete(URI uri, ResponseHandler handler) {
		executeInner(new HttpDelete(uri), handler);
	}
	
	private HttpResponse executeInner(HttpUriRequest request) throws Exception {
		Future<org.apache.http.HttpResponse> future = client.execute(request, new FutureCallback<org.apache.http.HttpResponse>() {

			@Override
			public void completed(org.apache.http.HttpResponse result) {
			}

			@Override
			public void failed(Exception ex) {
			}

			@Override
			public void cancelled() {
			}
		});
		
		return new HttpResponseProxy(future.get());
	}
	
	private void executeInner(HttpUriRequest request, ResponseHandler handler) {
		client.execute(request, new HttpResponseReceiver(handler));
	}
	
	private class HttpResponseReceiver implements FutureCallback<org.apache.http.HttpResponse> {
		private ResponseHandler responseHandler;
		
		public HttpResponseReceiver(ResponseHandler responseHandler) {
			this.responseHandler = responseHandler;
		}

		@Override
		public void completed(org.apache.http.HttpResponse response) {
			responseHandler.onCompleted(new HttpResponseProxy(response));
		}

		@Override
		public void failed(Exception ex) {
			responseHandler.onThrowable(ex);
		}

		@Override
		public void cancelled() {
			responseHandler.onThrowable(new CancellationException());
		}
		
	}

	@Override
	public void close() throws IOException {
		HttpAsyncClientUtils.closeQuietly(client);
	}
}