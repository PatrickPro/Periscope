/*
 * This code is based on the previous bachelor thesis by Maximilian Peter Hackenschmied
*/
package ba.patrickproppe.adb;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class Communicator extends Service {

	String serverIP = "192.168.2.102";
	int port = 4321;
	BufferedReader in;
	// MainActivity m;

	private Socket socket;
	private OutputStream outputStream;
	private PrintWriter printWriter;
	private InputStream inputStream;
	private InputStreamReader inputStreamReader;
	private Boolean isConnected = false;

	private final IBinder binder = new LocalBinder();

	private String registerreceiver;

	private Periscope mainADBDebugAtivity;
	private byte[] buffer = new byte[256];

	private static Communicator sInstance;

	/**
	 * Zeigt an, ob die Server-Client-Verbindung laufen sollte;
	 */
	private boolean conRun = false;
	private int reconnectCounter = 0;

	@Override
	public void onCreate() {
		// Log.v("Communicator", System.currentTimeMillis()
		// + ": ClientService erstellt.");
		sInstance = this;

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		// Log.v("Communicator", System.currentTimeMillis()
		// + ": ClientService gestartet.");

		if (intent != null) {
			Bundle extras = intent.getExtras();
			if (extras != null) {
				serverIP = (String) extras.getString("url");
				port = extras.getInt("port");
				registerreceiver = (String) extras.getString("registerKey");

			}

		}

		// starts and listens to connection
		conRun = true;
		Thread thread = new Thread(new Runnable() {

			@Override
			public void run() {
				buildConnection(serverIP);
				runListener();

			}
		});
		thread.start();
		return START_STICKY;

	}

	public void stopService() {
	
		try {
			this.closeConnection();
			this.stopSelf();
		} catch (Exception e) {

		}
	}

	private void buildConnection(String url) {

		final String u = url;

		try {

			socket = new Socket(u, port);
			outputStream = socket.getOutputStream();
			printWriter = new PrintWriter(new BufferedWriter(
					new OutputStreamWriter(outputStream)), true);

			inputStream = socket.getInputStream();
			inputStreamReader = new InputStreamReader(inputStream);
			asignConnected(true);
			reconnectCounter = 0;

		} catch (UnknownHostException e) {
			asignConnected(false);

			e.printStackTrace();
		} catch (IOException e) {
			asignConnected(false);
			e.printStackTrace();
		}



	}

	private void reconnectToServer() {
		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				buildConnection(serverIP);
				runListener();
			}
		});

		if (conRun) {
			t.start();
		}
	}

	private void asignConnected(boolean connected) {

		isConnected = connected;

	}

	private void sendToActivity(String s) {

		Intent i = new Intent(registerreceiver);

		i.putExtra("command", s);
		sendBroadcast(i);
	}

	// Reads and Interprets the InputStream
	private void runListener() {
		

		while (isConnected) {

			try {
				String s = "";
				inputStream.read(buffer);
				s = new String(buffer);
				// Interpretation
				sendToActivity(s);

			} catch (IOException e) {
				asignConnected(false);
				e.printStackTrace();
			}
		}

	}

	public void sendMsg(String msg) {
		printWriter.println(msg);
	}

	private void closeConnection() {
		conRun = false;
		if (socket != null) {
			try {

				socket.close();
	
			} catch (IOException e) {

				e.printStackTrace();
			}
		}
	}

	public void changeIp(String ip) {
		if (conRun) {
			closeConnection();
		}

		conRun = true;
		if (ip != null) {
			this.serverIP = ip;
		}
		reconnectToServer();
	}

	public void reconnect() {
		if (conRun) {
			closeConnection();
		}

		conRun = true;
		reconnectToServer();
	}

	public class LocalBinder extends Binder {
		public Communicator getService() {
			return Communicator.this;
		}
	}

	public static Communicator getInstance() {
		return sInstance;
	}

	@Override
	public IBinder onBind(Intent intent) {

		return binder;
	}

}
