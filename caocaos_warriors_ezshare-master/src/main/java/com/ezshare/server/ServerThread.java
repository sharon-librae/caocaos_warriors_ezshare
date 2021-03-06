package com.ezshare.server;
//import com.ezshare.Message;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import org.pmw.tinylog.Logger;

import com.ezshare.server.model.Message;
import com.ezshare.server.model.Subscriber;

import EZShare.Constant;

/**
 * @author mvalentino
 * 
 *         A class to handle thread creation when a new client connected to the
 *         server This class also handle the main logic processing
 *
 */
public class ServerThread extends Thread {
	private Socket socket = null;
	private String ipAddress;
	private int ID = -1;
	Message message;

	public ServerThread(Socket socket, String ipAddress) throws SocketException {
		this.socket = socket;
		this.ipAddress = ipAddress;
		this.ID = socket.getPort();
	}

	@Override
	public void run() {
		Logger.debug("Server thread " + ID + " running");
		String jsonString = "";
		try {
			DataInputStream streamIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			DataOutputStream streamOut = new DataOutputStream(socket.getOutputStream());
			while (true) {
				if (streamIn.available() > 0) {
					jsonString = streamIn.readUTF();
					Logger.debug(jsonString);
					message = Utilities.convertJsonToObject(jsonString, Message.class);

					if ((message.command.equals(Constant.FETCH.toUpperCase())
							|| message.command.equals(Constant.QUERY.toUpperCase())
							|| message.command.equals(Constant.SUBSCRIBE.toUpperCase()))
							&& (!jsonString.contains("resourceTemplate"))) {
						Logger.debug(message.command);
						streamOut.writeUTF(Utilities.getReturnMessage(Constant.MISSING_RESOURCE_TEMPLATE));
						break;
					} else if ((message.command.equals(Constant.PUBLISH.toUpperCase())
							|| message.command.equals(Constant.REMOVE.toUpperCase())
							|| message.command.equals(Constant.SHARE.toUpperCase()))
							&& !jsonString.contains("resource")) {
						streamOut.writeUTF(Utilities.getReturnMessage(Constant.MISSING_RESOURCE));
					} else if (message.command.equals(Constant.SUBSCRIBE.toUpperCase())) {
						// store the information for this subscriber
						socket.setKeepAlive(true);
						Storage.subscriber.add(new Subscriber(message.id, 0, message.resourceTemplate, this.socket));
						Subscription subscription = new Subscription(streamOut, message, false);
						subscription.subscribe();
						// break;
					} else {
						CommandHandler handler = new CommandHandler(message, streamOut, Storage.secret, false);
						handler.processMessage();
						break;
					}
				}
			}

			if (!message.command.equals(Constant.SUBSCRIBE.toUpperCase())) {
				Logger.debug(String.format("SERVER: removing %s from ip list", this.ipAddress));
				Utilities.removeIp(this.ipAddress);
				Logger.debug(String.format("SERVER: ip list size: %d", Storage.ipList.size()));
				try {
					if (streamIn != null)
						streamIn.close();
					if (streamOut != null)
						streamOut.close();
					if (socket != null) {
						socket.close();
						Logger.debug("Socket on thread " + ID + " closed");
					}
					Logger.debug("Server thread " + ID + " closed");
				} catch (IOException e) {
					Logger.error(e);
				}
			}
		} catch (IOException ioe) {
			Logger.error(ioe);
		}
	}
}
